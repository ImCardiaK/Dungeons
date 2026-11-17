package fr.companionproject.economy.command;

import fr.companionproject.economy.api.EconomyService;
import fr.companionproject.economy.model.Currency;
import fr.companionproject.economy.service.MessageService;
import java.math.BigDecimal;
import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;

/** Player-to-player transfer command. */
public final class PayCommand implements TabExecutor
{
    private final EconomyService economy;
    private final MessageService messages;

    /** @param economy economy API */
    public PayCommand(final EconomyService economy, final MessageService messages) {
        this.economy = economy;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        if (!(sender instanceof Player player))
        {
            messages.send(sender, "only-player");
            return true;
        }
        if (args.length != 3)
            return false;
        final OfflinePlayer target = CommandSupport.player(args[0]);
        if (target == null)
        {
            messages.send(sender, "unknown-player");
            return true;
        }
        final Currency currency = economy.currency(args[1]).orElse(null);
        if (currency == null)
        {
            messages.send(sender, "unknown-currency");
            return true;
        }
        final BigDecimal amount = CommandSupport.amount(args[2]);
        if (CommandSupport.failed(sender, messages, economy.transfer(player.getUniqueId(), target.getUniqueId(),
                currency.id(), amount)))
            return true;
        messages.send(sender, "pay-sent", "amount", CommandSupport.format(currency, amount),
                "player", target.getName() == null ? target.getUniqueId().toString() : target.getName());
        if (target instanceof Player online)
            messages.send(online, "pay-received", "amount", CommandSupport.format(currency, amount),
                    "player", player.getName());
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
            final String alias, final String[] args) {
        return args.length == 2 ? CommandSupport.currencies(economy, args[1]) : List.of();
    }
}
