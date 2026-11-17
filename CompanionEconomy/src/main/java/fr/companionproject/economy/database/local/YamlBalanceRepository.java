package fr.companionproject.economy.database.local;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import fr.companionproject.core.storage.SnapshotWriter;
import fr.companionproject.core.storage.AtomicFiles;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Ordered asynchronous snapshots of all balances in a single YAML document. */
public final class YamlBalanceRepository
{
    private final JavaPlugin plugin;
    private final File file;
    private final SnapshotWriter<Map<UUID, Map<String, BigDecimal>>> writer =
            new SnapshotWriter<>("CompanionEconomy-writer", snapshot -> {
                try { write(snapshot); }
                catch (IOException exception) { throw new IllegalStateException("Cannot save balances", exception); }
            });
    /** @param plugin owning plugin */
    public YamlBalanceRepository(final JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "balances.yml");
    }

    /** Loads a snapshot during startup, skipping malformed account entries. */
    public Map<UUID, Map<String, BigDecimal>> load() throws IOException {
        final Map<UUID, Map<String, BigDecimal>> result = new HashMap<>();
        if (!file.exists())
            return result;
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (final InvalidConfigurationException exception) {
            throw new IOException("balances.yml: invalid YAML", exception);
        }
        if (yaml.getInt("schema-version", -1) != 1)
            throw new IOException("balances.yml: unsupported schema-version");
        final ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
        if (accounts == null)
            return result;
        for (final String uuidText : accounts.getKeys(false))
        {
            try {
                final UUID uuid = UUID.fromString(uuidText);
                final ConfigurationSection amounts = accounts.getConfigurationSection(uuidText);
                if (amounts == null)
                    continue;
                final Map<String, BigDecimal> balances = new HashMap<>();
                for (final String id : amounts.getKeys(false))
                {
                    try {
                        final BigDecimal amount = new BigDecimal(amounts.getString(id, ""));
                        if (!id.equals(id.toLowerCase(Locale.ROOT)) || !id.matches("[a-z][a-z0-9_-]{0,31}")
                                || amount.signum() < 0 || amount.scale() < 0 || amount.scale() > 8
                                || amount.precision() > 24)
                            throw new NumberFormatException("invalid stored currency or amount");
                        balances.put(id, amount);
                    } catch (final IllegalArgumentException exception) {
                        plugin.getLogger().warning("Skipping invalid balance " + uuidText + "/" + id + ": "
                                + exception.getMessage());
                    }
                }
                result.put(uuid, balances);
            } catch (final IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid balance account " + uuidText + ": " + exception.getMessage());
            }
        }
        return result;
    }

    /** Queues an independent snapshot; earlier writes always finish first. */
    public CompletableFuture<Void> save(final Map<UUID, Map<String, BigDecimal>> snapshot) {
        final Map<UUID, Map<String, BigDecimal>> copy = new HashMap<>();
        snapshot.forEach((final UUID uuid, final Map<String, BigDecimal> amounts) ->
                copy.put(uuid, Map.copyOf(amounts)));
        return writer.submit(Map.copyOf(copy));
    }

    private void write(final Map<UUID, Map<String, BigDecimal>> snapshot) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        snapshot.forEach((final UUID uuid, final Map<String, BigDecimal> balances) ->
                balances.forEach((final String id, final BigDecimal amount) ->
                        yaml.set("accounts." + uuid + "." + id, amount.toPlainString())));
        AtomicFiles.replace(file.toPath(), yaml.saveToString());
    }

    /** Drains all accepted snapshots before releasing the writer. */
    public void close() { writer.close(); }
}