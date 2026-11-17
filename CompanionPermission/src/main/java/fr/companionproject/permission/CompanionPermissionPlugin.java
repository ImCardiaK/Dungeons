package fr.companionproject.permission;

import fr.companionproject.permission.service.PermissionService;
import fr.companionproject.permission.command.PermissionCommands;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle owner of the independent CompanionPermission plugin. */
public final class CompanionPermissionPlugin extends JavaPlugin implements Listener
{
    private PermissionService service;
    private fr.companionproject.core.api.CoreServices core;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();
    private final Map<UUID, Boolean> operators = new HashMap<>();

    /** Loads state and activates online permissions. */
    @Override public void onEnable() {
        try {
            saveDefaultConfig();
            if (getConfig().getInt("schema-version") != 1)
                throw new IllegalArgumentException("Unsupported config schema");
            service = new PermissionService(getDataFolder().toPath().resolve("permissions.yml"),
                    getConfig().getString("default-group", "default"));
            core = Objects.requireNonNull(getServer().getServicesManager()
                    .load(fr.companionproject.core.api.CoreServices.class), "CompanionCore API unavailable");
            core.register(this, fr.companionproject.permission.api.PermissionApi.class, service);
            final PermissionCommands commands = new PermissionCommands(service, this::refreshAll);
            for (String name : List.of("companionpermission", "companionuser", "companiongroup"))
            {
                final var command = Objects.requireNonNull(getCommand(name), "Missing command " + name);
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
            getServer().getPluginManager().registerEvents(this, this);
            for (Player player : getServer().getOnlinePlayers()) add(player);
            getServer().getScheduler().runTaskTimer(this, () -> {
                for (Player player : getServer().getOnlinePlayers())
                    if (!Objects.equals(operators.get(player.getUniqueId()), player.isOp())) refresh(player);
            }, 20L, 20L);
            service.save();
            getLogger().info("CompanionPermission enabled with " + service.groups().size() + " groups.");
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Cannot enable CompanionPermission", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /** Registers a joining player. */
    @EventHandler public void onJoin(final PlayerJoinEvent event) { add(event.getPlayer()); }
    /** Releases the quitting player's attachment. */
    @EventHandler public void onQuit(final PlayerQuitEvent event) { remove(event.getPlayer()); }

    private void add(final Player player) {
        service.ensure(player.getUniqueId(), player.getName());
        refresh(player);
    }

    private void refreshAll() { getServer().getOnlinePlayers().forEach(this::refresh); }

    private void refresh(final Player player) {
        remove(player);
        final var attachment = player.addAttachment(this);
        final Map<String, Boolean> effective = service.effective(player.getUniqueId());
        final Map<String, Boolean> expanded = new TreeMap<>();
        effective.entrySet().stream().filter(e -> e.getKey().equals("*") || e.getKey().endsWith(".*"))
                .sorted(Comparator.comparingInt(e -> e.getKey().length())).forEach(e -> {
                    final String prefix = e.getKey().equals("*") ? "" : e.getKey().substring(0, e.getKey().length() - 1);
                    getServer().getPluginManager().getPermissions().forEach(permission -> {
                        if (permission.getName().startsWith(prefix)) expanded.put(permission.getName(), e.getValue());
                    });
                });
        expanded.putAll(effective);
        expanded.forEach((node, allowed) -> {
            if (allowed || !player.isOp()) attachment.setPermission(node, allowed);
        });
        attachments.put(player.getUniqueId(), attachment);
        operators.put(player.getUniqueId(), player.isOp());
        player.recalculatePermissions();
    }

    private void remove(final Player player) {
        final var attachment = attachments.remove(player.getUniqueId());
        if (attachment != null) player.removeAttachment(attachment);
        operators.remove(player.getUniqueId());
    }

    /** Detaches online players and waits for accepted writes. */
    @Override public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getServer().getOnlinePlayers().forEach(this::remove);
        if (core != null) core.unregister(this);
        if (service != null) service.close();
    }
}
