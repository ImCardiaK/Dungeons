# CompanionCore

Fresh shared library for Companion Dungeons plugins on Paper 26.2 and Java 25. It has no dependency on the earlier CompanionMinecraft project, no gameplay rules, commands, permissions, configuration, or persistent data.

## Build and installation

Run `mvn -DskipTests package` in this directory. The artifact is `target/CompanionCore-1.0.0.jar`. Copy it into the server's `plugins` directory and restart the server. Paper provides the API at runtime; it is not bundled in this jar.

To compile a dependent plugin, add `fr.companion.dungeons:companioncore:1.0.0` with Maven `provided` scope. Run `mvn install` here first so it resolves locally. Declare `depend: [CompanionCore]` in the dependent plugin's `plugin.yml`.

## Integration API

`CoreServices` is the only public integration contract. Get it from Bukkit's services manager after CompanionCore has enabled:

```java
RegisteredServiceProvider<CoreServices> provider =
        Bukkit.getServicesManager().getRegistration(CoreServices.class);
if (provider == null)
    throw new IllegalStateException("CompanionCore service is unavailable");
CoreServices services = provider.getProvider();
```

Define a **public interface** in the plugin that owns a service, then publish its implementation with `services.register(this, MyService.class, implementation)`. Consumers compile against that interface and call `services.find(MyService.class)`. All registry calls must run on the main server thread. Only one provider may own a given interface at a time. CompanionCore removes registrations when the owner disables; consumers should look up a service again after plugin reloads rather than retaining an old instance.

The registry is deliberately small. Add shared scheduling, command, configuration, message, menu, or persistence abstractions when a concrete Dungeons plugin needs the same infrastructure. Product rules remain in their owning plugin.

## Compatibility

This is a new `1.0.0` API in `fr.companion.dungeons.core`. It is not binary compatible with the earlier `fr.companion.core` library. Install only one plugin named `CompanionCore` on a server; plugins built against the earlier library require migration before using this one.

## Project layout

- `api`: public service contract.
- `service`: registry implementation.
- `listener`: owner lifecycle cleanup.
- Root package: plugin bootstrap.
