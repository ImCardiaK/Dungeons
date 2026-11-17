## Project boundaries

- Keep every plugin in its own top-level project directory, named after the plugin. Never nest one Maven/plugin project inside another.
- Give each plugin its own `pom.xml`, `README.md`, `src`, resources, build output, configuration, data folder, and lifecycle.
- Do not couple plugins through source folders or implementation classes. Expose an intentional public service API when integration is required.
- Keep generated files such as `target`, local Maven caches, IDE metadata, and logs out of source control.

## Shared infrastructure

- `CompanionCore` is the one shared library of this workspace. It is a Bukkit plugin installed once on the server, depended on with `depend: [CompanionCore]` and a `provided` Maven dependency, never shaded into a plugin jar.
- Put a class there only when it is infrastructure every plugin needs in the same form: the scheduler seam, command routing and argument parsing, bundled-resource loading and configuration validation, translation catalogs and message rendering, the menu framework, and the ordered atomic writers behind a repository.
- Keep domain in the plugin that owns it. A composition root, a screen catalogue, a permission node, a locale policy, and a result or status enum name one product's rules and never move.
- Extract into the library the moment the same infrastructure is written a second time, and delete the copies in the same change. Two implementations of one seam drift.
- Treat a change to a shared signature as breaking for every dependent plugin at once. Release the library's major version with the list of plugin versions that go with it.

## Naming and language

- Write everything the project ships in English: package and type names, configuration keys and comments, bundled data, log messages, Javadoc, and in-method comments. Other languages belong only in the translation catalogs.
- Derive the base package from the vendor and the product, as with `fr.companion.economy` and `fr.companion`. Never leave a stale product name inside the package after a rename, and never end up with a segment repeated at two levels.
- Use lowercase directory names for data folders the plugin creates, matching the lowercase style of the configuration files beside them.
- Renaming a plugin changes its data folder, its permission nodes, and the namespace of every `NamespacedKey` it writes. Treat it as a breaking change, and say so in the README together with the migration steps.
- Keep bundled identifiers (card ids, theme names, definition keys) stable, lowercase, and free of accents, because they travel through file names, item metadata, and command arguments.

## Package architecture

- Follow the established layers, one package per layer: plugin bootstrap, `command`, `listener`, `service`, `database` with its concrete implementations under `database/local`, `model`, `registry` with its parsers under `registry/settings`, `scheduler`, `manager`, and `gui` with one screen per class under `gui/menu`.
- Put every registry in `registry`, every service in `service`, and every runtime manager in `manager`. Name each class after what it is, so its package and its name agree.
- Put every immutable record, enum, and value rule in `model`, flat. `model` must depend on no other package of the plugin, so every other layer may depend on it freely.
- Give the product's own domain behaviour a dedicated package named after the product's subject, such as `tcg`. It holds the game or domain classes that are none of the above: parsers, item factories, engines, and their mutable session state.
- Treat the `JavaPlugin` class as the composition root. It loads configuration, constructs dependencies, registers commands/listeners, handles reload state, and flushes resources on shutdown. It exposes no accessor for the collaborators it built.
- Keep business rules in services, storage details in repositories, Bukkit event reactions in listeners, and presentation/argument handling in commands.
- Depend on repository interfaces from services. Keep YAML or future database details behind concrete repository implementations.
- Use small support, registry, scheduler, or runtime classes only for genuinely shared infrastructure; do not turn them into miscellaneous utility containers.
- Extract a shared value type the moment the same rule is written twice. Board geometry, container materials, name normalization, and price tables each belong in exactly one place.

## Commands

- Give every concrete command action its own dedicated class, including nested actions such as `list`, `create`, `delete`, `info`, `set`, `unset`, and `clear`.
- A shared dispatcher may route execution and merge tab suggestions, but it must not contain concrete command behavior.
- Keep argument validation, the action, its usage message, and its tab completion in the same concrete command class.
- Validate the exact argument count before reading positional arguments and return immediately after reporting invalid input.
- Resolve names through the relevant service/support layer, while UUIDs remain the stable stored identity.
- Reuse presentation-only helpers for parsing booleans, validating permission nodes, formatting values, matching completions, refreshing runtime state, and reporting asynchronous results.
- Return deterministic, case-insensitive, prefix-filtered tab suggestions. Suggest only values valid for the current argument position.
- Declare every top-level Bukkit command and permission in `plugin.yml`. Register through `CommandRegistry` so missing declarations fail during startup.
- Share one operation between a command and a menu through a service, so both paths report the same outcome instead of drifting apart.
- Keep `plugin.yml`, help output, usage strings, aliases, and `README.md` command documentation synchronized.

## Listeners and Bukkit runtime state

- Give every listener one dedicated class. Do not combine unrelated event responsibilities.
- Keep listeners thin: receive the event, extract the Bukkit object, and delegate to a service or runtime manager.
- Register each listener explicitly in the plugin bootstrap.
- Handle already-online players during `onEnable` so plugin reloads behave like ordinary joins.
- Own Bukkit resources such as `PermissionAttachment` instances in a dedicated manager. Remove or replace owned resources before adding new ones and clean all of them during shutdown.
- Recalculate player permissions after attachment changes.
- Act on the main hand only when an interaction can fire for both hands, so one player action never runs the handler twice.
- Clear every piece of per-player transient runtime state on quit: sessions, remembered views, running animations, and pending requests.

## Menus and screens

- Give every screen its own class that both renders itself and handles its own clicks. A listener may cancel the event and forward it, but it holds no menu behaviour.
- Route a click to the screen whose title is the longest match for the open inventory, so adding a screen never means editing an ordered chain of title comparisons.
- Never encode state in an inventory title. Keep the page, the edited entity, and any filter in a per-player view memory owned by the screen, and clear it on quit. A user-supplied name must be free to contain any character the title would otherwise have to escape.
- Store the action a click should trigger in the item's persistent data, never in its display name.
- Keep the slot layout in one place and fail startup when a slot array and the configured limit it renders stop agreeing, instead of silently hiding entries.
- Resolve a title, a label, and an item's lore for the viewer that will read it, because two players may read different languages.

## Services and domain rules

- Keep methods small and single-purpose. Prefer short, intention-revealing names when they remain unambiguous.
- Validate and normalize at service boundaries, including configured values, group names, permission nodes, monetary scale, and loaded data.
- Canonicalize case-insensitive identifiers with `Locale.ROOT`; preserve UUIDs as authoritative player/account identifiers.
- Make compound in-memory mutations atomic. The current services use synchronized public operations to protect related reads and writes.
- Reject invalid operations before mutating state. Do not partially apply transfers, overdrafts, inheritance cycles, protected-group deletion, or malformed permissions.
- Use explicit result/status types when an operation has several valid outcomes. Distinguish changed, unchanged, missing, protected, cyclic, expired, or insufficient-funds states instead of collapsing them into one failure.
- Queue persistence only after a successful state change. Return an already-completed stage for unchanged or rejected operations.
- Refresh online runtime state immediately after an accepted in-memory change; report persistence completion separately.
- Keep ordering and precedence rules explicit. Examples are ancestor-to-child-to-player permission resolution and balance mutation before transaction-ledger append.

## Value exchanges

- Validate every side of an exchange completely before moving any money. A trade, a purchase, or a payout must never reach a state where one side has paid and the other has not delivered.
- When a step after the payment is rejected, refund what was moved and report the refusal. Never leave a paid-for delivery unmade.
- Lock an offer while it is being settled, so neither side can edit what is already being paid for.
- Claim a limited offer before requesting its payment and release the claim when the payment fails, so the same offer cannot be sold twice while a debit is in flight.
- Snapshot what an exchange promised at the moment it was validated, and settle from that snapshot rather than from live mutable state.

## Models and data safety

- Prefer immutable records for configuration, identities, snapshots, results, and stored domain values.
- Defensively copy mutable collections at model/snapshot boundaries with `Map.copyOf`, `List.copyOf`, or an equivalent independent copy. Copy the nested collections too, not only the outer one.
- Replace immutable records after mutations instead of exposing mutable fields.
- Return immutable collections and snapshots from services so callers cannot mutate service state.
- Use `Optional` for public lookup APIs when absence is expected. Restrict `null` to narrow parsing/Bukkit-boundary cases where it keeps command flow simpler, and document what `null` means when a parameter accepts it.
- Remember that `Map.of` rejects null values. A placeholder value that can be absent must be given a defined fallback before it reaches the message service.
- Use `BigDecimal` for currency. Normalize scale deliberately, cap accepted precision, use an explicit rounding mode, and never use floating-point money.
- Use `Instant` and `Duration` for deadlines and lifetimes rather than manual millisecond arithmetic.

## Configuration

- Copy bundled defaults on first startup without overwriting administrator-edited files.
- Never write a configuration or language file back during an ordinary load. Load the administrator's file, set the bundled resource as its defaults, and merge in memory. A schema migration is the sole exception: back up the untouched source, apply incremental migrations in memory, validate the latest schema, and replace the file atomically.
- Parse each configuration file with its own dedicated parser, share the validation helpers between them, and expose typed immutable configuration records to the rest of the plugin.
- Build the whole configuration before publishing it, so a value rejected part-way leaves the previous configuration in place and a reload never half-applies.
- Reject a value the plugin could not honour rather than clamping it silently. A stock size larger than the menu can render, or a cost larger than the cap, must fail startup with a message naming the key.
- Disable the plugin cleanly with a clear log message when required configuration is invalid.
- Give every administrator-owned YAML document an integer `schema-version` independent from the plugin version. Keep migrations incremental (`v1 -> v2`, then `v2 -> v3`) so upgrades may skip plugin releases, and reject documents newer than the plugin understands.

## Localization

- Keep player-facing text behind a message service. Support named placeholders and translate Bukkit color codes in one place.
- Store translations in separate YAML configuration files, with exactly one catalog per supported locale. Never combine multiple languages in one file.
- Name every translation catalog after its locale using the `<COUNTRY>_<language>.yml` convention, such as `FR_fr.yml`. Discover additional locale files dynamically, and fill missing bundled keys in memory without overwriting administrator-edited translations.
- Resolve text for the recipient it is meant for, so a player reads their client's language while another player reads theirs. Fall back to the configured default locale for the console and for world-visible text such as an entity name or a villager offer, and document that a `null` recipient selects it.
- Report a key no catalog defines once, and return the key itself rather than empty text.
- Put the colour codes inside the catalog values, so wording and colour are both configurable without touching code.
- Keep raw command-only messages exceptional; every repeated or configurable message belongs in a catalog.

## Persistence and concurrency

- Give each stored aggregate its own repository interface, and let each concrete implementation own the single-thread executor that keeps its writes ordered.
- Exchange snapshots across the repository boundary, never live mutable service state.
- Load persistent state during startup, sanitize it before use, and tolerate recoverable bad entries by skipping or repairing them with a warning.
- Store permission assignments as YAML lists of flat dotted-key boolean entries (for example `- companioneconomy.transactions: true`).
- Give persistent player documents their own integer `schema-version`. Back up and incrementally migrate older representations before publishing loaded state; never overwrite the source when migration or validation fails.
- Run file/database writes away from the Bukkit server thread on a dedicated single-thread executor so writes remain ordered.
- Represent asynchronous work with `CompletionStage`/`CompletableFuture`; compose dependent operations instead of blocking during normal gameplay. A reload must not wait on a write.
- Never call Bukkit APIs from a storage thread. Marshal callbacks that message senders, log through plugin state, schedule tasks, or modify Bukkit objects back to the server thread.
- Route every scheduler use through one seam class, so no other class talks to the Bukkit scheduler directly.
- Check that the plugin is still enabled before scheduling an asynchronous completion callback.
- Observe background failures even when no sender is waiting and log them with useful context and the original cause.
- Implement repository shutdown as an ordered barrier, then stop its executor. During `onDisable`, flush final snapshots and wait for completion so accepted changes are not lost.
- Guard partial initialization in `onDisable`; cleanup must remain safe if `onEnable` stopped early.

## Java style

- Target the Java release declared by the project (currently Java 25) and the matching Spigot API version.
- Use four-space indentation and keep lines within 120 characters.
- Put type-declaration opening braces on the next line. Put method/constructor opening braces on the same line as the signature's closing parenthesis.
- Put the opening brace of a braced control block on its own next line. Keep `try {`, `} catch (…) {`, `} else {`, and `} else if (…) {` inline. A single-statement branch takes no braces at all.
- Keep a trivial method on one line when its complete body is a single short expression, as with a default method returning `List.of()`.
- Separate the logical steps of a method with a blank line: guards, then the work, then the result. Leave a blank line after a guard block that returns.
- Mark parameters and local variables `final` unless reassignment is necessary. Mark implementation classes `final` unless extension is intentional. Type lambda parameters explicitly when the surrounding code does.
- Prefer constructor injection and private final fields. Construct dependencies explicitly in the bootstrap rather than hiding them in global state.
- Use early returns for validation and failure paths to avoid deep nesting.
- Prefer clear domain names over abbreviations; shorten method names only when meaning stays obvious in context. Name a constant instead of repeating a literal, including slot numbers, radii, and sentinel values.
- Import every type you use. A fully qualified name never appears inside a method body.
- Add Javadocs to every type, constructor, and public or protected member, fields included. Document parameters, return values, declared exceptions, lifecycle effects, and non-obvious domain behavior where applicable.
- Comment inside a method wherever the reason for a step is not obvious from the code: why an order matters, why a value is refunded, why a branch is unreachable in practice. Do not narrate self-evident code.
- Catch only exceptions that can be handled meaningfully. Preserve the cause when wrapping asynchronous/storage errors.
- Use streams when they make collection transformations clearer; use ordinary loops when control flow, ordering, or early exit is easier to read.
- Leave no unused import, field, method, or configuration key behind. Delete dead code in the same change that makes it dead.

## Lifecycle and registration

- Keep `onEnable` ordered: configuration, translations, persistence, services, schedulers/runtime managers, presentation, commands, listeners, reload reconciliation, then the enabled log.
- Keep `onDisable` ordered according to dependencies. For example, save accounts before closing a ledger that depends on them, and detach Bukkit runtime resources before closing permission storage.
- Reload in the same order as startup: validate the new configuration first, then close the screens whose titles are about to change, then reload the catalogs and registries, then reconcile the players already online.
- Fail fast when Java registration and `plugin.yml` drift apart.
- Log a concise enabled message only after initialization succeeds.

## Maven, resources, and documentation

- Keep Spigot API dependencies in `provided` scope so the server supplies them at runtime.
- Set source encoding explicitly to UTF-8 and filter resources that use Maven project properties such as the plugin version. Keep `${…}` out of any other bundled resource, because filtering applies to all of them.
- Give jars stable plugin-specific final names and include useful implementation metadata in the manifest.
- Keep default configuration, language catalogs, and `plugin.yml` under `src/main/resources`.
- Ship an index of any bundled resource tree the plugin extracts file by file, since a jar cannot be listed as a directory at runtime.
- Maintain a project README with requirements, build instructions, jar location, commands, permissions, configuration, persistence behavior, package layout, and integration API notes.
- Do not keep a changelog file. Document a release that breaks compatibility in the README, with the breaking changes and the steps an administrator must take.
- Generate Javadocs when a plugin exposes a meaningful public API; keep any shared documentation styling consistent.

## Current verification scope

- Do not add, restore, or modify automated tests unless the user explicitly asks for test work.
- Still verify production changes by compiling/package-building each affected standalone project with tests skipped.
- Treat compiler failures as code failures to fix, while keeping test creation out of scope.
- When no JDK or Maven is available, cross-reference the change instead: confirm that packages match directories, that every import resolves, that every call matches a declared member and arity, that every translation key exists, and that nothing references a removed name. Report plainly that this is not a compile, and that the build still has to be confirmed.

## FINAL

- Add final .jar from C:\Users\Utilisateur\Desktop\Companion Dungeons SC to C:\Users\Utilisateur\Desktop\Companion Dungeons\plugins ! Tell me if i want before do this !