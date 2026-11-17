package fr.companionproject.economy.command;

import fr.companionproject.economy.api.EconomyService;
import fr.companionproject.economy.model.Currency;
import fr.companionproject.economy.model.TransactionResult;
import fr.companionproject.economy.service.MessageService;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;


/** Administrator commands for deployment and account management. */
public final class EconomyCommand implements TabExecutor
{
    private final EconomyService economy;
    private final MessageService messages;

    /** @param economy economy API */
    public EconomyCommand(final EconomyService economy, final MessageService messages) {
        this.economy = economy;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        if (args.length == 0)
            return false;
        final String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("list") && args.length == 1)
        {
            messages.send(sender, "currencies-list", "currencies", economy.currencies().stream()
                    .map(Currency::id).sorted().collect(java.util.stream.Collectors.joining(", ")));
            return true;
        }
        if (action.equals("reload") && args.length == 1)
        {
            try {
                economy.reloadCurrencies();
                messages.send(sender, "currencies-reloaded", "count",
                        Integer.toString(economy.currencies().size()));
            } catch (final IOException exception) {
                messages.send(sender, "reload-failed", "reason", exception.getMessage());
            }
            return true;
        }
        if (action.equals("total") && args.length == 2)
        {
            final Currency currency = economy.currency(args[1]).orElse(null);
            if (currency == null)
                messages.send(sender, "unknown-currency");
            else
                messages.send(sender, "total-show", "currency", currency.name(), "amount",
                        CommandSupport.format(currency, economy.total(currency.id())));
            return true;
        }
        if ((action.equals("give") || action.equals("take") || action.equals("set")) && args.length == 4)
        {
            final OfflinePlayer target = CommandSupport.player(args[1]);
            final Currency currency = economy.currency(args[2]).orElse(null);
            final BigDecimal amount = CommandSupport.amount(args[3]);
            if (target == null || currency == null)
            {
                messages.send(sender, target == null ? "unknown-player" : "unknown-currency");
                return true;
            }
            final TransactionResult result = switch (action) {
                case "give" -> economy.deposit(target.getUniqueId(), currency.id(), amount);
                case "take" -> economy.withdraw(target.getUniqueId(), currency.id(), amount);
                default -> economy.setBalance(target.getUniqueId(), currency.id(), amount);
            };
            if (!CommandSupport.failed(sender, messages, result))
                messages.send(sender, "balance-updated", "player",
                        target.getName() == null ? target.getUniqueId().toString() : target.getName(), "amount",
                        CommandSupport.format(currency, economy.balance(target.getUniqueId(), currency.id())));
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
            final String alias, final String[] args) {
        if (args.length == 1)
            return List.of("give", "list", "reload", "set", "take", "total").stream()
                    .filter((final String value) -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("total"))
            return CommandSupport.currencies(economy, args[1]);
        if (args.length == 3)
            return CommandSupport.currencies(economy, args[2]);
        return List.of();
    }
}
