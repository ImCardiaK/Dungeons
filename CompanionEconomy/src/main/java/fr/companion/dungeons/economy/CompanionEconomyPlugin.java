package fr.companion.dungeons.economy;

import fr.companion.dungeons.core.api.CoreServices;
import fr.companion.dungeons.economy.api.EconomyService;
import fr.companion.dungeons.economy.command.BalanceCommand;
import fr.companion.dungeons.economy.command.EconomyCommand;
import fr.companion.dungeons.economy.command.PayCommand;
import fr.companion.dungeons.economy.database.local.YamlBalanceRepository;
import fr.companion.dungeons.economy.registry.CurrencyRegistry;
import fr.companion.dungeons.economy.service.DefaultEconomyService;
import fr.companion.dungeons.economy.service.MessageService;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Composition root of the multi-currency economy. */
public final class CompanionEconomyPlugin extends JavaPlugin
{
    private YamlBalanceRepository repository;
    private CoreServices coreServices;

    /** Creates the Bukkit bootstrap. */
    public CompanionEconomyPlugin() {}

    /** Loads currencies and balances, then publishes the economy API and commands. */
    @Override
    public void onEnable() {
        try {
            final MessageService messages = new MessageService(this);
            messages.load();
            final File defaultCurrency = new File(getDataFolder(), "currencies/coins.yml");
            final File currencyDirectory = defaultCurrency.getParentFile();
            final File[] definitions = currencyDirectory.listFiles((final File file) ->
                    file.isFile() && file.getName().endsWith(".yml"));
            if (definitions == null || definitions.length == 0)
                saveResource("currencies/coins.yml", false);
            repository = new YamlBalanceRepository(this);
            final Map<UUID, Map<String, BigDecimal>> accounts = repository.load();
            final CurrencyRegistry registry = new CurrencyRegistry(this);
            registry.reload(accounts);
            final EconomyService economy = new DefaultEconomyService(this, registry, repository, accounts);
            final RegisteredServiceProvider<CoreServices> provider =
                    getServer().getServicesManager().getRegistration(CoreServices.class);
            if (provider == null)
                throw new IllegalStateException("CompanionCore service API is unavailable");
            coreServices = provider.getProvider();
            coreServices.register(this, EconomyService.class, economy);
            register("balance", new BalanceCommand(economy, messages));
            register("pay", new PayCommand(economy, messages));
            register("economy", new EconomyCommand(economy, messages));
            getLogger().info("CompanionEconomy enabled with " + registry.all().size() + " currencies.");
        } catch (final IOException | IllegalStateException exception) {
            getLogger().severe("Cannot enable CompanionEconomy: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void register(final String name, final TabExecutor executor) {
        final PluginCommand command = getCommand(name);
        if (command == null)
            throw new IllegalStateException("Missing plugin.yml command: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    /** Removes the public service and finishes pending snapshots. */
    @Override
    public void onDisable() {
        if (coreServices != null)
            coreServices.unregister(this);
        if (repository != null)
            repository.close();
    }
}
