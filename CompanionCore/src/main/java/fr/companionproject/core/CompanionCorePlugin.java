package fr.companionproject.core;

import fr.companionproject.core.api.CoreServices;
import fr.companionproject.core.listener.ServiceOwnerListener;
import fr.companionproject.core.service.DefaultCoreServices;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Bootstrap for the Companion Dungeons shared library. */
public final class CompanionCorePlugin extends JavaPlugin
{
    /** Creates the Bukkit plugin bootstrap. */
    public CompanionCorePlugin() {}

    /** Publishes the shared registry for dependent plugins. */
    @Override
    public void onEnable() {
        final CoreServices services = new DefaultCoreServices();
        getServer().getPluginManager().registerEvents(new ServiceOwnerListener(services), this);
        getServer().getServicesManager().register(CoreServices.class, services, this, ServicePriority.Normal);
        getLogger().info("CompanionCore service API is ready.");
    }

    /** Withdraws the shared registry. */
    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
    }
}
