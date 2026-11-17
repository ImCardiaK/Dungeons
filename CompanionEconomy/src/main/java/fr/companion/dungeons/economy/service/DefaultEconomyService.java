package fr.companion.dungeons.economy.service;

import fr.companion.dungeons.economy.api.EconomyService;
import fr.companion.dungeons.economy.database.local.YamlBalanceRepository;
import fr.companion.dungeons.economy.model.Currency;
import fr.companion.dungeons.economy.model.TransactionResult;
import fr.companion.dungeons.economy.registry.CurrencyRegistry;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Atomic in-memory accounts with ordered durable snapshots. */
public final class DefaultEconomyService implements EconomyService
{
    private final JavaPlugin plugin;
    private final CurrencyRegistry registry;
    private final YamlBalanceRepository repository;
    private final Map<UUID, Map<String, BigDecimal>> accounts;

    /** Constructs the service from a validated startup snapshot. */
    public DefaultEconomyService(final JavaPlugin plugin, final CurrencyRegistry registry,
            final YamlBalanceRepository repository, final Map<UUID, Map<String, BigDecimal>> accounts) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.accounts = new HashMap<>();
        accounts.forEach((final UUID player, final Map<String, BigDecimal> balances) ->
                this.accounts.put(player, new HashMap<>(balances)));
    }

    /** {@inheritDoc} */
    @Override
    public Collection<Currency> currencies() { mainThread(); return registry.all(); }

    /** {@inheritDoc} */
    @Override
    public Optional<Currency> currency(final String id) { mainThread(); return Optional.ofNullable(registry.get(id)); }

    /** {@inheritDoc} */
    @Override
    public BigDecimal balance(final UUID player, final String currencyId) {
        mainThread();
        Objects.requireNonNull(player, "player");
        final String id = currencyId.toLowerCase(Locale.ROOT);
        return accounts.getOrDefault(player, Map.of()).getOrDefault(id, BigDecimal.ZERO);
    }

    /** {@inheritDoc} */
    @Override
    public BigDecimal total(final String currencyId) {
        mainThread();
        final String id = currencyId.toLowerCase(Locale.ROOT);
        return accounts.values().stream().map((final Map<String, BigDecimal> amounts) ->
                amounts.getOrDefault(id, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** {@inheritDoc} */
    @Override
    public TransactionResult deposit(final UUID player, final String currencyId, final BigDecimal amount) {
        mainThread();
        final Currency currency = registry.get(currencyId);
        if (currency == null)
            return TransactionResult.UNKNOWN_CURRENCY;
        if (!positive(currency, amount))
            return TransactionResult.INVALID_AMOUNT;
        final BigDecimal next = balance(player, currency.id()).add(amount);
        if (!valid(currency, next))
            return TransactionResult.BALANCE_LIMIT;
        return setBalance(player, currency.id(), next);
    }

    /** {@inheritDoc} */
    @Override
    public TransactionResult withdraw(final UUID player, final String currencyId, final BigDecimal amount) {
        mainThread();
        final Currency currency = registry.get(currencyId);
        if (currency == null)
            return TransactionResult.UNKNOWN_CURRENCY;
        if (!positive(currency, amount))
            return TransactionResult.INVALID_AMOUNT;
        if (balance(player, currency.id()).compareTo(amount) < 0)
            return TransactionResult.INSUFFICIENT_FUNDS;
        return setBalance(player, currency.id(), balance(player, currency.id()).subtract(amount));
    }

    /** {@inheritDoc} */
    @Override
    public TransactionResult setBalance(final UUID player, final String currencyId, final BigDecimal amount) {
        mainThread();
        Objects.requireNonNull(player, "player");
        final Currency currency = registry.get(currencyId);
        if (currency == null)
            return TransactionResult.UNKNOWN_CURRENCY;
        if (!valid(currency, amount))
            return TransactionResult.INVALID_AMOUNT;
        if (balance(player, currency.id()).compareTo(amount) == 0)
            return TransactionResult.UNCHANGED;

        accounts.computeIfAbsent(player, (final UUID ignored) -> new HashMap<>()).put(currency.id(), amount);
        persist();
        return TransactionResult.SUCCESS;
    }

    /** {@inheritDoc} */
    @Override
    public TransactionResult transfer(final UUID sender, final UUID recipient, final String currencyId,
            final BigDecimal amount) {
        mainThread();
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        final Currency currency = registry.get(currencyId);
        if (currency == null)
            return TransactionResult.UNKNOWN_CURRENCY;
        if (!currency.paymentsEnabled())
            return TransactionResult.PAYMENTS_DISABLED;
        if (!positive(currency, amount))
            return TransactionResult.INVALID_AMOUNT;
        if (sender.equals(recipient))
            return TransactionResult.SAME_ACCOUNT;
        if (balance(sender, currency.id()).compareTo(amount) < 0)
            return TransactionResult.INSUFFICIENT_FUNDS;
        final BigDecimal recipientBalance = balance(recipient, currency.id()).add(amount);
        if (!valid(currency, recipientBalance))
            return TransactionResult.BALANCE_LIMIT;

        accounts.computeIfAbsent(sender, (final UUID ignored) -> new HashMap<>()).put(currency.id(),
                balance(sender, currency.id()).subtract(amount));
        accounts.computeIfAbsent(recipient, (final UUID ignored) -> new HashMap<>()).put(currency.id(),
                recipientBalance);
        persist();
        return TransactionResult.SUCCESS;
    }

    /** {@inheritDoc} */
    @Override
    public void reloadCurrencies() throws IOException {
        mainThread();
        registry.reload(accounts);
    }

    private boolean positive(final Currency currency, final BigDecimal amount) {
        return valid(currency, amount) && amount.signum() > 0;
    }

    private boolean valid(final Currency currency, final BigDecimal amount) {
        return amount != null && amount.signum() >= 0 && amount.scale() >= 0
                && amount.scale() <= currency.scale()
                && amount.precision() <= 24;
    }

    private void persist() {
        repository.save(accounts).whenComplete((final Void ignored, final Throwable error) -> {
            if (error != null)
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Balance snapshot failed", error);
        });
    }

    private void mainThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("EconomyService must be used on the server thread");
    }
}
