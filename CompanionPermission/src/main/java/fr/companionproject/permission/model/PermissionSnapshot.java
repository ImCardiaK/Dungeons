package fr.companionproject.permission.model;

import java.util.Map;
import java.util.UUID;

/** Immutable state written as one atomic YAML document. */
public record PermissionSnapshot(Map<String, PermissionGroup> groups, Map<UUID, PlayerProfile> players)
{
    /** Copies both aggregate maps. Their values are immutable records. */
    public PermissionSnapshot {
        groups = Map.copyOf(groups);
        players = Map.copyOf(players);
    }
}
