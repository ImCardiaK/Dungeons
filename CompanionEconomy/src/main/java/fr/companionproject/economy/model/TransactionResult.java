package fr.companionproject.economy.model;

/** Result of a balance change or transfer. */
public enum TransactionResult
{
    SUCCESS, UNCHANGED, UNKNOWN_CURRENCY, INVALID_AMOUNT, INSUFFICIENT_FUNDS,
    SAME_ACCOUNT, PAYMENTS_DISABLED, BALANCE_LIMIT
}
