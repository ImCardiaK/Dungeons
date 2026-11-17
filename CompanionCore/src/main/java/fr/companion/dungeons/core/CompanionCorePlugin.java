package fr.companion.dungeons.core;

import fr.companion.dungeons.core.api.CoreServices;
import fr.companion.dungeons.core.listener.ServiceOwnerListener;
import fr.companion.dungeons.core.service.DefaultCoreServices;
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
