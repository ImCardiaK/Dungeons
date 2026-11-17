package fr.companionproject.core.service;

import fr.companionproject.core.api.CoreServices;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Server-thread implementation of the shared service registry. */
public final class DefaultCoreServices implements CoreServices
{
    private final Map<Class<?>, Registration<?>> registrations = new HashMap<>();

    /** Creates an empty registry. */
    public DefaultCoreServices() {}

    /** {@inheritDoc} */
    @Override
    public <T> void register(final Plugin owner, final Class<T> contract, final T service) {
        requireServerThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(service, "service");
        if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers()) || !contract.isInstance(service))
            throw new IllegalArgumentException("Service must implement a public interface: " + contract.getName());
        if (!owner.isEnabled())
            throw new IllegalStateException("Plugin is disabled: " + owner.getName());
        if (registrations.containsKey(contract))
            throw new IllegalStateException("Service already registered: " + contract.getName());

        registrations.put(contract, new Registration<>(owner, service));
    }

    /** {@inheritDoc} */
    @Override
    public <T> Optional<T> find(final Class<T> contract) {
        requireServerThread();
        Objects.requireNonNull(contract, "contract");
        final Registration<?> registration = registrations.get(contract);
        return registration == null ? Optional.empty() : Optional.of(contract.cast(registration.service()));
    }

    /** {@inheritDoc} */
    @Override
    public void unregister(final Plugin owner) {
        requireServerThread();
        Objects.requireNonNull(owner, "owner");
        registrations.entrySet().removeIf((final Map.Entry<Class<?>, Registration<?>> entry) ->
                entry.getValue().owner() == owner);
    }

    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("CoreServices must be used on the server thread");
    }

    private record Registration<T>(Plugin owner, T service) {}
}
