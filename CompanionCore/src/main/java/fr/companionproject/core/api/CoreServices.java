package fr.companionproject.core.api;

import java.util.Optional;
import org.bukkit.plugin.Plugin;

/** Registry for service contracts shared by Companion Dungeons plugins. */
public interface CoreServices
{
    /**
     * Publishes one service contract owned by a plugin. Must be called on the server thread.
     *
     * @param owner plugin that owns the service
     * @param contract public interface consumers compile against
     * @param service implementation supplied by the owner
     * @param <T> service contract type
     * @throws IllegalArgumentException if the contract is not an interface or the service is invalid
     * @throws IllegalStateException if the owner is disabled or the contract is already registered
     */
    <T> void register(Plugin owner, Class<T> contract, T service);

    /**
     * Looks up a published service. Must be called on the server thread.
     *
     * @param contract public service interface
     * @param <T> service contract type
     * @return current service, if registered
     */
    <T> Optional<T> find(Class<T> contract);

    /**
     * Removes all services published by an owner. Must be called on the server thread.
     *
     * @param owner plugin that owns the services
     */
    void unregister(Plugin owner);
}
