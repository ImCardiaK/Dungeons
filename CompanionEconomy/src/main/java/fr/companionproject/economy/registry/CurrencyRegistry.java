package fr.companionproject.economy.registry;

import fr.companionproject.economy.model.Currency;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads one currency per YAML file and publishes only a fully valid set. */
public final class CurrencyRegistry
{
    private final JavaPlugin plugin;
    private Map<String, Currency> currencies = Map.of();

    /** @param plugin owning plugin */
    public CurrencyRegistry(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Loads all definitions; leaves the previous set intact on validation failure. */
    public void reload(final Map<UUID, Map<String, BigDecimal>> accounts) throws IOException {
        final File directory = new File(plugin.getDataFolder(), "currencies");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create " + directory);
        final File[] files = directory.listFiles((final File file) -> file.isFile() && file.getName().endsWith(".yml"));
        if (files == null)
            throw new IOException("Cannot list " + directory);
        final Map<String, Currency> loaded = new LinkedHashMap<>();
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (final File file : files)
        {
            final YamlConfiguration yaml = new YamlConfiguration();
            try {
                yaml.load(file);
            } catch (final InvalidConfigurationException exception) {
                throw new IOException(file.getName() + ": invalid YAML", exception);
            }
            if (yaml.getInt("schema-version", -1) != 1)
                throw new IOException(file.getName() + ": schema-version must be 1");
            final String id = yaml.getString("id", "");
            final String name = yaml.getString("name", "");
            final String symbol = yaml.getString("symbol", "");
            final int scale = yaml.getInt("scale", -1);
            if (!id.matches("[a-z][a-z0-9_-]{0,31}") || !file.getName().equals(id + ".yml")
                    || name.isBlank() || symbol.isBlank()
                    || scale < 0 || scale > 8 || !yaml.isBoolean("payments-enabled"))
                throw new IOException(file.getName() + ": invalid id, name, symbol, scale or payments-enabled");
            if (loaded.putIfAbsent(id, new Currency(id, name, symbol, scale,
                    yaml.getBoolean("payments-enabled"))) != null)
                throw new IOException("Duplicate currency id: " + id);
        }
        if (loaded.isEmpty())
            throw new IOException("At least one currency file is required in " + directory);
        for (final Map<String, BigDecimal> balances : accounts.values())
        {
            for (final Map.Entry<String, BigDecimal> entry : balances.entrySet())
            {
                final Currency currency = loaded.get(entry.getKey());
                if (currency != null && entry.getValue().stripTrailingZeros().scale() > currency.scale())
                    throw new IOException("Currency " + currency.id() + " has stored balances finer than its scale");
            }
        }
        currencies = Map.copyOf(loaded);
    }

    /** @return immutable deployed currency set */
    public Collection<Currency> all() { return currencies.values(); }

    /** @return currency or null when unknown */
    public Currency get(final String id) { return currencies.get(id.toLowerCase(Locale.ROOT)); }
}
