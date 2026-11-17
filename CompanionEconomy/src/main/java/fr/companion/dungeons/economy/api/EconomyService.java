package fr.companion.dungeons.economy.api;

import fr.companion.dungeons.economy.model.Currency;
import fr.companion.dungeons.economy.model.TransactionResult;
import java.math.BigDecimal;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/** Public economy contract for other Companion Dungeons plugins. Calls belong on the main server thread. */
public interface EconomyService
{
    /** @return all currently deployed currencies */
    Collection<Currency> currencies();

    /** @return a deployed currency, if present */
    Optional<Currency> currency(String id);

    /** @return account balance, or zero for a new account */
    BigDecimal balance(UUID player, String currencyId);

    /** @return sum of all stored account balances for a currency */
    BigDecimal total(String currencyId);

    /** Credits an account. @return transaction result */
    TransactionResult deposit(UUID player, String currencyId, BigDecimal amount);

    /** Debits an account. @return transaction result */
    TransactionResult withdraw(UUID player, String currencyId, BigDecimal amount);

    /** Sets an account balance. @return transaction result */
    TransactionResult setBalance(UUID player, String currencyId, BigDecimal amount);

    /** Moves money atomically between accounts. @return transaction result */
    TransactionResult transfer(UUID sender, UUID recipient, String currencyId, BigDecimal amount);

    /** Reloads all currency definitions atomically after validating stored balances. */
    void reloadCurrencies() throws IOException;
}
