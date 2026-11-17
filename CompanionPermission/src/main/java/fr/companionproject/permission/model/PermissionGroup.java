package fr.companionproject.permission.model;

import java.util.Map;

/** Immutable group definition; a null parent means no inheritance. */
public record PermissionGroup(String name, String parent, Map<String, Boolean> nodes, String prefix, String suffix)
{
    /** Defensively copies direct assignments and normalizes absent display text. */
    public PermissionGroup {
        nodes = Map.copyOf(nodes);
        prefix = prefix == null ? "" : prefix;
        suffix = suffix == null ? "" : suffix;
    }

    /** @return an empty group with the supplied canonical name */
    public static PermissionGroup empty(final String name) {
        return new PermissionGroup(name, null, Map.of(), "", "");
    }

    /** @return this group with a different parent */
    public PermissionGroup withParent(final String value) {
        return new PermissionGroup(name, value, nodes, prefix, suffix);
    }

    /** @return this group with different direct assignments */
    public PermissionGroup withNodes(final Map<String, Boolean> value) {
        return new PermissionGroup(name, parent, value, prefix, suffix);
    }

    /** @return this group with different prefix text */
    public PermissionGroup withPrefix(final String value) {
        return new PermissionGroup(name, parent, nodes, value, suffix);
    }

    /** @return this group with different suffix text */
    public PermissionGroup withSuffix(final String value) {
        return new PermissionGroup(name, parent, nodes, prefix, value);
    }
}
