package fr.companion.dungeons.economy.command;

import fr.companion.dungeons.economy.api.EconomyService;
import fr.companion.dungeons.economy.model.Currency;
import fr.companion.dungeons.economy.model.TransactionResult;
import fr.companion.dungeons.economy.service.MessageService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/** Shared command parsing and presentation. */
public final class CommandSupport
{
    private CommandSupport() {}

    /** Resolves a known player without creating a new account for a mistyped name. */
    public static OfflinePlayer player(final String name) {
        try {
            final UUID uuid = UUID.fromString(name);
            final OfflinePlayer byId = Bukkit.getOfflinePlayer(uuid);
            return byId.hasPlayedBefore() || byId.isOnline() ? byId : null;
        } catch (final IllegalArgumentException ignored) {
            // The argument is a player name.
        }
        return Arrays.stream(Bukkit.getOfflinePlayers()).filter((final OfflinePlayer candidate) ->
                candidate.getName() != null && candidate.getName().equalsIgnoreCase(name)).findFirst()
                .orElseGet(() -> Bukkit.getPlayerExact(name));
    }

    /** Parses a decimal amount; returns null for malformed input. */
    public static BigDecimal amount(final String text) {
        if (!text.matches("[0-9]{1,24}(\\.[0-9]{1,8})?"))
            return null;
        try {
            return new BigDecimal(text);
        } catch (final NumberFormatException exception) {
            return null;
        }
    }

    /** Formats money for the currency's configured precision. */
    public static String format(final Currency currency, final BigDecimal amount) {
        return amount.setScale(currency.scale()).toPlainString() + " " + currency.symbol();
    }

    /** Provides deterministic currency suggestions. */
    public static List<String> currencies(final EconomyService economy, final String prefix) {
        final String normalized = prefix.toLowerCase(Locale.ROOT);
        return economy.currencies().stream().map(Currency::id).filter((final String id) ->
                id.startsWith(normalized)).sorted().toList();
    }

    /** Sends a transaction result when it failed. */
    public static boolean failed(final CommandSender sender, final MessageService messages,
            final TransactionResult result) {
        if (result == TransactionResult.SUCCESS || result == TransactionResult.UNCHANGED)
            return false;
        messages.send(sender, "result-" + result.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        return true;
    }
}
