package fr.companion.dungeons.permission.model;

import java.util.Map;

/** Immutable known-player state. */
public record PlayerProfile(String name, String group, Map<String, Boolean> nodes)
{
    /** Copies direct assignments before publishing the profile. */
    public PlayerProfile {
        nodes = Map.copyOf(nodes);
    }

    /** @return this player in another group */
    public PlayerProfile withGroup(final String value) {
        return new PlayerProfile(name, value, nodes);
    }

    /** @return this player with different direct assignments */
    public PlayerProfile withNodes(final Map<String, Boolean> value) {
        return new PlayerProfile(name, group, value);
    }
}
