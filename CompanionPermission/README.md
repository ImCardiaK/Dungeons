# CompanionPermission

New standalone permission plugin for Companion Dungeons, targeting Paper 26.2 and Java 25.
Requires the Dungeons CompanionCore 2.0.0. It has no dependency on the old CompanionPermissions project.

Build with `mvn -DskipTests package` after installing CompanionCore into the local Maven repository.
Artifact: `target/CompanionPermission-2.0.0.jar`.

All commands require `companionpermission.admin` (operator by default).

| Command | Purpose |
| --- | --- |
| `/companionpermission` (`/cpermission`, `/cperms`) | Command help |
| `/cgroup list` | List groups |
| `/cgroup create <group>` | Create a group |
| `/cgroup delete <group>` | Delete a non-default group and reassign its members |
| `/cgroup <group> info` | Show direct group data |
| `/cgroup <group> parent set <parent>` | Set inheritance, rejecting cycles |
| `/cgroup <group> parent clear` | Remove inheritance |
| `/cgroup <group> permission set <node> <true|false>` | Allow or deny a node |
| `/cgroup <group> permission unset <node>` | Remove a direct node |
| `/cgroup <group> prefix set <text>` | Set prefix text (128 characters maximum) |
| `/cgroup <group> suffix set <text>` | Set suffix text |
| `/cgroup <group> prefix clear` | Clear prefix and inherit again |
| `/cgroup <group> suffix clear` | Clear suffix and inherit again |
| `/cuser <player> info` | Show profile and effective explicit permissions |
| `/cuser <player> group set <group>` | Assign the player's single group |
| `/cuser <player> permission set <node> <true|false>` | Set a direct override |
| `/cuser <player> permission unset <node>` | Remove a direct override |

`/cgroup` and `/cuser` alias `/companiongroup` and `/companionuser`. Offline administration accepts a known
name or UUID after the player has joined. Wrap display text in double quotes to preserve surrounding spaces.
Prefixes and suffixes are exposed to other plugins; this plugin does not format chat itself.

Ancestor permissions are overridden by child permissions, then player assignments. Wildcards expand against
registered Bukkit nodes; explicit nodes take precedence. Operator attachments omit denials, preserving Bukkit
operator defaults. Operator changes are detected within one second. Changes refresh connected players immediately.

The new data directory is `plugins/CompanionPermission`. `config.yml` defines `default-group`; restart after
editing it. `permissions.yml` stores groups and UUID-based profiles together with schema version 1. Permission
nodes use lists of flat dotted-key boolean maps. All state is saved in order on a dedicated writer using atomic
replacement, with a final flush on shutdown. Invalid schemas or inheritance stop startup without overwriting data.
Do not edit permission data while the server runs. Storage errors are logged; immediate command success means
the in-memory change was applied and its disk write queued.

Other plugins compile against this artifact in provided scope and obtain `PermissionApi` through Dungeons
`CoreServices.find(PermissionApi.class)`. Its queries return immutable snapshots and support asynchronous readers.

This is a new 2.0.0 API and storage format. Old `CompanionPermissions` data and permission nodes are not imported.
Recreate groups using the commands and use `companionpermission.admin` for administration. Run one permission
manager per server. The original project is only a functional reference and remains untouched.

## Migration to 2.0.0

The Java namespace and Maven group are now `fr.companionproject`. Install CompanionCore, CompanionEconomy and CompanionPermission 2.0.0 together; remove their previous JARs first. Recompile external integrations against the new packages. Plugin names, command permissions and stored data paths remain compatible. Back up plugin data before upgrading.

Persistence now keeps only the latest waiting snapshot while a write is active. Replaced submissions complete after the replacement is written. Shutdown drains accepted writes. Permission YAML serialization runs on the storage worker using immutable snapshots.
