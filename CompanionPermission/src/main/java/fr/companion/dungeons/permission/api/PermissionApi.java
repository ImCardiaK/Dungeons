package fr.companion.dungeons.permission.api;

import fr.companion.dungeons.permission.model.*;
import java.util.*;

/** Thread-safe immutable permission and display queries for other Dungeons plugins. */
public interface PermissionApi
{
    /** @return all known groups */
    Map<String, PermissionGroup> groups();
    /** @return all known player profiles */
    Map<UUID, PlayerProfile> players();
    /** @return resolved explicit nodes, excluding Bukkit defaults */
    Map<String, Boolean> effective(UUID id);
    /** @return inherited display text for a known player */
    Optional<PlayerDisplay> display(UUID id);
}
