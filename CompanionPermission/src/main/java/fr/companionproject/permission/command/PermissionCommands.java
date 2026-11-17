package fr.companionproject.permission.command;

import fr.companionproject.permission.service.PermissionService;
import java.util.*;
import org.bukkit.command.*;

/** Administrative commands for the new permission store. */
public final class PermissionCommands implements TabExecutor
{
    private final PermissionService service;
    private final Runnable refresh;

    /** Wires domain operations to immediate online refresh. */
    public PermissionCommands(final PermissionService service, final Runnable refresh) {
        this.service = service;
        this.refresh = refresh;
    }

    /** Validates and executes one administration command. */
    @Override public boolean onCommand(final CommandSender sender, final Command command,
            final String label, final String[] args) {
        if (!sender.hasPermission("companionpermission.admin")) return true;
        try {
            if (command.getName().equals("companionpermission"))
            {
                if (args.length != 0) return false;
                sender.sendMessage("/cgroup list|create <group>|delete <group>");
                sender.sendMessage("/cgroup <group> info|parent set <parent>|parent clear");
                sender.sendMessage("/cgroup <group> permission set <node> <true|false>|permission unset <node>");
                sender.sendMessage("/cgroup <group> prefix|suffix set <text> / clear");
                sender.sendMessage("/cuser <player> info|group set <group>|permission set <node> <true|false>|permission unset <node>");
                return true;
            }
            if (command.getName().equals("companiongroup")) return group(sender, args);
            return user(sender, args);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("Operation refused: " + e.getMessage());
            return true;
        }
    }

    private boolean group(final CommandSender sender, final String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list"))
        {
            sender.sendMessage("Groups: " + String.join(", ", new TreeSet<>(service.groups().keySet())));
            return true;
        }
        if (args.length == 2 && Set.of("create", "delete").contains(args[0].toLowerCase(Locale.ROOT)))
        {
            service.changeGroup(args[1], args[0].toLowerCase(Locale.ROOT), null, null);
            return changed(sender);
        }
        if (args.length < 2) return false;
        final String name = PermissionService.groupName(args[0]);
        final String action = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 2 && action.equals("info"))
        {
            final var group = service.groups().get(name);
            if (group == null) throw new IllegalArgumentException("Unknown group");
            sender.sendMessage(group.toString());
            return true;
        }
        if (args.length < 3) return false;
        final String operation = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("parent"))
        {
            if (operation.equals("clear") && args.length == 3) service.changeGroup(name, action, null, null);
            else if (operation.equals("set") && args.length == 4) service.changeGroup(name, action, args[3], null);
            else return false;
        } else if (action.equals("permission")) {
            if (operation.equals("unset") && args.length == 4) service.changeGroup(name, action, args[3], null);
            else if (operation.equals("set") && args.length == 5) service.changeGroup(name, action, args[3], bool(args[4]));
            else return false;
        } else if (action.equals("prefix") || action.equals("suffix")) {
            if (operation.equals("clear") && args.length == 3) service.changeGroup(name, action, "", null);
            else if (operation.equals("set") && args.length >= 4)
            {
                String text = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                if (text.startsWith("\"") && text.endsWith("\"") && text.length() >= 2)
                    text = text.substring(1, text.length() - 1);
                service.changeGroup(name, action, text, null);
            } else return false;
        } else return false;
        return changed(sender);
    }

    private boolean user(final CommandSender sender, final String[] args) {
        if (args.length < 2) return false;
        final UUID id = service.find(args[0]);
        if (id == null) throw new IllegalArgumentException("Unknown or ambiguous player; use a UUID");
        if (args.length == 2 && args[1].equalsIgnoreCase("info"))
        {
            sender.sendMessage(service.players().get(id).toString());
            sender.sendMessage("Effective: " + service.effective(id));
            return true;
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("group") && args[2].equalsIgnoreCase("set"))
            service.changePlayer(id, "group", args[3], null);
        else if (args.length == 4 && args[1].equalsIgnoreCase("permission") && args[2].equalsIgnoreCase("unset"))
            service.changePlayer(id, "permission", args[3], null);
        else if (args.length == 5 && args[1].equalsIgnoreCase("permission") && args[2].equalsIgnoreCase("set"))
            service.changePlayer(id, "permission", args[3], bool(args[4]));
        else return false;
        return changed(sender);
    }

    private Boolean bool(final String value) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Expected true or false");
        return Boolean.valueOf(value);
    }

    private boolean changed(final CommandSender sender) {
        refresh.run();
        sender.sendMessage("Change applied. Persistence queued.");
        return true;
    }

    /** Provides deterministic context-sensitive suggestions. */
    @Override public List<String> onTabComplete(final CommandSender sender, final Command command,
            final String alias, final String[] args) {
        if (!sender.hasPermission("companionpermission.admin") || args.length == 0) return List.of();
        final List<String> candidates = new ArrayList<>();
        final boolean group = command.getName().equals("companiongroup");
        if (args.length == 1)
        {
            if (group) { candidates.addAll(service.groups().keySet()); candidates.addAll(List.of("list", "create", "delete")); }
            else if (command.getName().equals("companionuser"))
                service.players().values().forEach(p -> candidates.add(p.name()));
        } else if (args.length == 2) {
            if (group && args[0].equalsIgnoreCase("delete")) candidates.addAll(service.groups().keySet());
            else if (!group || service.groups().containsKey(args[0].toLowerCase(Locale.ROOT)))
                candidates.addAll(group ? List.of("info", "parent", "permission", "prefix", "suffix")
                        : List.of("info", "group", "permission"));
        } else if (args.length == 3) {
            if (args[1].equalsIgnoreCase("permission")) candidates.addAll(List.of("set", "unset"));
            else if (Set.of("parent", "prefix", "suffix").contains(args[1].toLowerCase(Locale.ROOT)))
                candidates.addAll(List.of("set", "clear"));
            else if (args[1].equalsIgnoreCase("group")) candidates.add("set");
        } else if (args.length == 4 && args[2].equalsIgnoreCase("set")
                && Set.of("parent", "group").contains(args[1].toLowerCase(Locale.ROOT)))
            candidates.addAll(service.groups().keySet());
        else if (args.length == 5 && args[1].equalsIgnoreCase("permission") && args[2].equalsIgnoreCase("set"))
            candidates.addAll(List.of("true", "false"));
        final String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().distinct().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }
}
