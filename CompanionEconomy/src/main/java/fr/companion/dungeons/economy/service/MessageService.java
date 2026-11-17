package fr.companion.dungeons.economy.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads player-facing messages from per-locale YAML catalogs. */
public final class MessageService
{
    private final JavaPlugin plugin;
    private final Map<String, Map<String, String>> catalogs = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private String defaultLocale;

    /** @param plugin owning plugin */
    public MessageService(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Loads the configured default locale and every catalog in the locales directory. */
    public void load() throws IOException {
        plugin.saveDefaultConfig();
        final YamlConfiguration config = loadFile(new File(plugin.getDataFolder(), "config.yml"));
        if (config.getInt("schema-version", -1) != 1)
            throw new IOException("config.yml: schema-version must be 1");
        final String configuredDefault = config.getString("default-locale", "");
        if (!configuredDefault.matches("[A-Z]{2}_[a-z]{2}"))
            throw new IOException("config.yml: invalid default-locale");

        final File directory = new File(plugin.getDataFolder(), "locales");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Cannot create " + directory);
        for (final String bundled : new String[] {"EN_us", "FR_fr"})
        {
            final File file = new File(directory, bundled + ".yml");
            if (!file.exists())
                plugin.saveResource("locales/" + bundled + ".yml", false);
        }

        final File[] files = directory.listFiles((final File file) ->
                file.isFile() && file.getName().matches("[A-Z]{2}_[a-z]{2}\\.yml"));
        if (files == null)
            throw new IOException("Cannot list " + directory);
        final Map<String, Map<String, String>> loaded = new HashMap<>();
        for (final File file : files)
        {
            final YamlConfiguration yaml = loadFile(file);
            if (yaml.getInt("schema-version", -1) != 1)
                throw new IOException(file.getName() + ": schema-version must be 1");
            final Map<String, String> values = new HashMap<>();
            for (final String key : yaml.getKeys(false))
            {
                if (!key.equals("schema-version") && yaml.isString(key))
                    values.put(key, yaml.getString(key, ""));
            }
            loaded.put(file.getName().replace(".yml", ""), Map.copyOf(values));
        }
        if (!loaded.containsKey(configuredDefault))
            throw new IOException("Missing default locale catalog: " + configuredDefault);
        catalogs.clear();
        catalogs.putAll(loaded);
        defaultLocale = configuredDefault;
        missing.clear();
    }

    /** Sends a localized message with alternating placeholder names and values. */
    public void send(final CommandSender recipient, final String key, final String... replacements) {
        if (replacements.length % 2 != 0)
            throw new IllegalArgumentException("Placeholder names and values must be paired");
        final String locale = recipient instanceof Player player ? locale(player.getLocale()) : defaultLocale;
        String value = catalogs.getOrDefault(locale, Map.of()).get(key);
        if (value == null)
            value = catalogs.getOrDefault(defaultLocale, Map.of()).get(key);
        if (value == null)
        {
            if (missing.add(key))
                plugin.getLogger().warning("Missing translation key: " + key);
            value = key;
        }
        for (int index = 0; index < replacements.length; index += 2)
            value = value.replace("{" + replacements[index] + "}", replacements[index + 1]);
        recipient.sendMessage(ChatColor.translateAlternateColorCodes('&', value));
    }

    private String locale(final String bukkitLocale) {
        final String[] parts = bukkitLocale.split("[_-]", 2);
        return parts.length == 2 ? parts[0].toUpperCase(Locale.ROOT) + "_"
                + parts[1].toLowerCase(Locale.ROOT) : defaultLocale;
    }

    private YamlConfiguration loadFile(final File file) throws IOException {
        final YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (final InvalidConfigurationException exception) {
            throw new IOException(file.getName() + ": invalid YAML", exception);
        }
        return yaml;
    }
}
