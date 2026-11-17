# CompanionEconomy

Multi-currency economy for Paper 26.2, Java 25, and CompanionCore 1.0.0. Each currency is deployed by placing a YAML file in `plugins/CompanionEconomy/currencies/` and running `/economy reload`. The first startup creates `coins.yml` only if the directory contains no currency definitions. Each definition has `schema-version: 1`, a unique lowercase `id` matching its filename, a display `name`, a `symbol`, `scale` from 0 to 8, and `payments-enabled`. For example:

```yaml
schema-version: 1
id: gems
name: Gems
symbol: '◆'
scale: 0
payments-enabled: true
```

Save that as `currencies/gems.yml`. A reload validates the complete set before publishing it; a bad file leaves the current currencies active. Reducing a currency's scale is refused while stored balances have finer precision. Removing a definition makes its balances unavailable until the same ID is deployed again.

Build with Maven: `mvn -DskipTests package`. The jar is `target/CompanionEconomy-1.0.0.jar`. Install it beside CompanionCore on the Paper server. The Paper and CompanionCore dependencies are `provided`, not bundled. No Vault integration is included.

## Commands

| Command | Purpose | Permission |
| --- | --- | --- |
| `/balance [currency] [player]` | Own balance; first currency when omitted | `companioneconomy.balance` |
| `/balance <currency> <player>` | Another player's balance | `companioneconomy.balance.others` |
| `/pay <player> <currency> <amount>` | Transfer to a known player | `companioneconomy.pay` |
| `/economy list` | List active currencies | `companioneconomy.admin` |
| `/economy reload` | Deploy changed currency files | `companioneconomy.admin` |
| `/economy give|take|set <player> <currency> <amount>` | Change a known account | `companioneconomy.admin` |
| `/economy total <currency>` | Sum all stored balances | `companioneconomy.admin` |

Default permissions: balance and pay are available to players; others and admin are operator-only. Accounts use player UUIDs. Player arguments accept a known player name or UUID. Balances start at zero, cannot be negative, and are stored in `plugins/CompanionEconomy/balances.yml` with `schema-version: 1`. Amounts use decimal notation with at most 24 significant digits and the currency's configured number of decimal places. Credits and transfers refuse to overflow that limit. Changes are saved on one ordered background writer using atomic file replacement; the server waits for accepted writes on shutdown. A failed background write is logged; plugins that grant rewards should understand that a successful in-memory result precedes disk completion.

`config.yml` selects `default-locale` (`EN_us` by default). Player messages use their client locale when a matching file exists under `locales/`, falling back to the configured default. The bundled `EN_us.yml` and `FR_fr.yml` are copied only when absent. Console messages use the default locale. Catalogs can be edited before a server restart. Every administrator-owned YAML file has `schema-version: 1`.

## Plugin API

Other plugins declare `depend: [CompanionCore, CompanionEconomy]` and compile with `fr.companion.dungeons:companioneconomy:1.0.0` in `provided` scope. After enabling, obtain `CoreServices` from Bukkit's services manager, then `find(EconomyService.class)`. All economy calls belong on the main server thread. `deposit`, `withdraw`, `setBalance`, and `transfer` return `TransactionResult`; check it before granting a purchase or reward.

Source layout: `api` public contract, `model` values, `registry` currency definitions, `service` account rules and messages, `database/local` YAML snapshots, `command` player/admin presentation, and the root bootstrap. This project has no source or runtime link to the earlier CompanionMinecraft plugins.
