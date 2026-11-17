package fr.companionproject.core.listener;

import fr.companionproject.core.api.CoreServices;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

/** Removes services when their owning plugin stops. */
public final class ServiceOwnerListener implements Listener
{
    private final CoreServices services;

    /** @param services registry to clean when a plugin stops */
    public ServiceOwnerListener(final CoreServices services) {
        this.services = Objects.requireNonNull(services, "services");
    }

    /** @param event plugin shutdown event */
    @EventHandler
    public void onPluginDisable(final PluginDisableEvent event) {
        services.unregister(event.getPlugin());
    }
}
