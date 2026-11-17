package fr.companion.dungeons.permission.service;

import fr.companion.dungeons.permission.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

/** Owns groups and profiles, with immutable reads and ordered atomic persistence. */
public final class PermissionService implements AutoCloseable, fr.companion.dungeons.permission.api.PermissionApi
{
    private final Map<String, PermissionGroup> groups = new TreeMap<>();
    private final Map<UUID, PlayerProfile> players = new HashMap<>();
    private final Path file;
    private final String defaultGroup;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);

    /** Loads a new, independent permission store. */
    public PermissionService(final Path file, final String defaultGroup) throws IOException {
        this.file = file;
        this.defaultGroup = groupName(defaultGroup);
        if (Files.exists(file))
        {
            final YamlConfiguration yaml = new YamlConfiguration();
            try { yaml.load(file.toFile()); }
            catch (InvalidConfigurationException e) { throw new IOException("Invalid permissions.yml", e); }
            if (yaml.getInt("schema-version") != 1) throw new IOException("Unsupported permission schema");
            final var root = yaml.getConfigurationSection("groups");
            if (root != null) for (String name : root.getKeys(false))
            {
                groupName(name);
                groups.put(name, new PermissionGroup(name, root.getString(name + ".parent"),
                        nodes(root.getList(name + ".permissions", List.of())),
                        root.getString(name + ".prefix", ""), root.getString(name + ".suffix", "")));
            }
            final var users = yaml.getConfigurationSection("players");
            if (users != null) for (String id : users.getKeys(false))
                players.put(UUID.fromString(id), new PlayerProfile(users.getString(id + ".name", id),
                        users.getString(id + ".group", defaultGroup),
                        nodes(users.getList(id + ".permissions", List.of()))));
        }
        groups.putIfAbsent(defaultGroup, PermissionGroup.empty(defaultGroup));
        for (PermissionGroup group : groups.values())
        {
            final Set<String> seen = new HashSet<>();
            String cursor = group.name();
            while (cursor != null)
            {
                if (!seen.add(cursor) || !groups.containsKey(cursor))
                    throw new IOException("Invalid inheritance at " + group.name());
                cursor = groups.get(cursor).parent();
            }
        }
        players.replaceAll((id, p) -> groups.containsKey(p.group()) ? p : p.withGroup(defaultGroup));
    }

    private Map<String, Boolean> nodes(final List<?> entries) throws IOException {
        final Map<String, Boolean> result = new TreeMap<>();
        for (Object entry : entries)
        {
            if (!(entry instanceof Map<?, ?> values)) throw new IOException("Expected permission list entries");
            for (var value : values.entrySet())
            {
                if (!(value.getKey() instanceof String key) || !(value.getValue() instanceof Boolean allowed))
                    throw new IOException("Permission entries must contain boolean values");
                result.put(node(key), allowed);
            }
        }
        return result;
    }

    /** @return canonical validated group name */
    public static String groupName(final String value) {
        final String name = value.toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]{1,32}")) throw new IllegalArgumentException("Invalid group name");
        return name;
    }

    /** @return canonical validated permission node */
    public static String node(final String value) {
        final String name = value.toLowerCase(Locale.ROOT);
        if (!name.matches("(?:\\*|[a-z0-9_-]+(?:\\.[a-z0-9_-]+)*(?:\\.\\*)?)") || name.length() > 255)
            throw new IllegalArgumentException("Invalid permission node");
        return name;
    }

    /** Registers or updates an observed player identity. */
    public synchronized void ensure(final UUID id, final String name) {
        final PlayerProfile old = players.get(id);
        if (old != null && old.name().equals(name)) return;
        players.put(id, new PlayerProfile(name, old == null ? defaultGroup : old.group(),
                old == null ? Map.of() : old.nodes()));
        save();
    }

    /** @return immutable group definitions */
    public synchronized Map<String, PermissionGroup> groups() { return Map.copyOf(groups); }
    /** @return immutable player profiles */
    public synchronized Map<UUID, PlayerProfile> players() { return Map.copyOf(players); }
    /** Resolves UUIDs or unambiguous known names. */
    public synchronized UUID find(final String value) {
        try { final UUID id = UUID.fromString(value); return players.containsKey(id) ? id : null; }
        catch (IllegalArgumentException ignored) { }
        final List<UUID> matches = players.entrySet().stream()
                .filter(e -> e.getValue().name().equalsIgnoreCase(value)).map(Map.Entry::getKey).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    /** Resolves ancestor, child, then direct player assignments. */
    public synchronized Map<String, Boolean> effective(final UUID id) {
        final PlayerProfile player = players.get(id);
        if (player == null) return Map.of();
        final List<PermissionGroup> chain = new ArrayList<>();
        PermissionGroup current = groups.get(player.group());
        while (current != null) {
            chain.add(current);
            current = current.parent() == null ? null : groups.get(current.parent());
        }
        final Map<String, Boolean> result = new TreeMap<>();
        for (int index = chain.size() - 1; index >= 0; index--) result.putAll(chain.get(index).nodes());
        result.putAll(player.nodes());
        return Map.copyOf(result);
    }

    /** Resolves prefix and suffix independently from the nearest defining ancestor. */
    public synchronized Optional<PlayerDisplay> display(final UUID id) {
        final PlayerProfile player = players.get(id);
        if (player == null) return Optional.empty();
        String prefix = "";
        String suffix = "";
        PermissionGroup current = groups.get(player.group());
        while (current != null)
        {
            if (prefix.isEmpty()) prefix = current.prefix();
            if (suffix.isEmpty()) suffix = current.suffix();
            current = current.parent() == null ? null : groups.get(current.parent());
        }
        return Optional.of(new PlayerDisplay(player.group(), prefix, suffix));
    }

    /** Applies a validated group operation and queues its complete snapshot. */
    public synchronized void changeGroup(final String raw, final String action, final String value,
            final Boolean allowed) {
        final String name = groupName(raw);
        final PermissionGroup group = groups.get(name);
        if (action.equals("create"))
        {
            if (group != null) throw new IllegalArgumentException("Group already exists");
            groups.put(name, PermissionGroup.empty(name));
        } else {
            if (group == null) throw new IllegalArgumentException("Unknown group");
            switch (action)
            {
                case "delete" -> {
                    if (name.equals(defaultGroup)) throw new IllegalArgumentException("Default group is protected");
                    groups.remove(name);
                    groups.replaceAll((key, g) -> name.equals(g.parent()) ? g.withParent(null) : g);
                    players.replaceAll((id, p) -> name.equals(p.group()) ? p.withGroup(defaultGroup) : p);
                }
                case "parent" -> {
                    final String parent = value == null ? null : groupName(value);
                    if (parent != null && !groups.containsKey(parent)) throw new IllegalArgumentException("Unknown parent");
                    String cursor = parent;
                    while (cursor != null)
                    {
                        if (cursor.equals(name)) throw new IllegalArgumentException("Inheritance cycle refused");
                        cursor = groups.get(cursor).parent();
                    }
                    groups.put(name, group.withParent(parent));
                }
                case "permission" -> {
                    final Map<String, Boolean> copy = new TreeMap<>(group.nodes());
                    final String key = node(value);
                    if (allowed == null) copy.remove(key); else copy.put(key, allowed);
                    groups.put(name, group.withNodes(copy));
                }
                case "prefix", "suffix" -> {
                    final String text = value == null ? "" : value;
                    if (text.length() > 128 || text.contains("\n") || text.contains("\r"))
                        throw new IllegalArgumentException("Display text must be a single line of at most 128 characters");
                    groups.put(name, action.equals("prefix") ? group.withPrefix(text) : group.withSuffix(text));
                }
                default -> throw new IllegalArgumentException("Unknown action");
            }
        }
        save();
    }

    /** Changes one known player's group or direct permission. */
    public synchronized void changePlayer(final UUID id, final String action, final String value,
            final Boolean allowed) {
        final PlayerProfile profile = players.get(id);
        if (profile == null) throw new IllegalArgumentException("Unknown player");
        if (action.equals("group"))
        {
            final String name = groupName(value);
            if (!groups.containsKey(name)) throw new IllegalArgumentException("Unknown group");
            players.put(id, profile.withGroup(name));
        } else {
            final Map<String, Boolean> copy = new TreeMap<>(profile.nodes());
            final String key = node(value);
            if (allowed == null) copy.remove(key); else copy.put(key, allowed);
            players.put(id, profile.withNodes(copy));
        }
        save();
    }

    /** Serializes on the calling thread; only filesystem work runs on the writer. */
    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        groups.forEach((name, group) -> {
            final String path = "groups." + name;
            yaml.set(path + ".parent", group.parent());
            yaml.set(path + ".prefix", group.prefix());
            yaml.set(path + ".suffix", group.suffix());
            yaml.set(path + ".permissions", entries(group.nodes()));
        });
        players.forEach((id, profile) -> {
            final String path = "players." + id;
            yaml.set(path + ".name", profile.name());
            yaml.set(path + ".group", profile.group());
            yaml.set(path + ".permissions", entries(profile.nodes()));
        });
        final String contents = yaml.saveToString();
        pending = CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(file.getParent());
                final Path temporary = Files.createTempFile(file.getParent(), "permissions-", ".tmp");
                try {
                    Files.writeString(temporary, contents);
                    try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException e) {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally { Files.deleteIfExists(temporary); }
            } catch (IOException e) { throw new CompletionException(e); }
        }, writer);
        pending.whenComplete((ignored, failure) -> {
            if (failure != null) java.util.logging.Logger.getLogger("CompanionPermission")
                    .log(java.util.logging.Level.SEVERE, "Permission persistence failed", failure);
        });
    }

    private List<Map<String, Boolean>> entries(final Map<String, Boolean> nodes) {
        return nodes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> Map.of(e.getKey(), e.getValue())).toList();
    }

    /** Flushes the final snapshot and closes the ordered writer. */
    @Override public synchronized void close() {
        save();
        writer.shutdown();
        pending.join();
    }
}
