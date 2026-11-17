package fr.companion.dungeons.economy.command;

import fr.companion.dungeons.economy.api.EconomyService;
import fr.companion.dungeons.economy.model.Currency;
import fr.companion.dungeons.economy.service.MessageService;
import java.util.List;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.TabExecutor;
import org.bukkit.command.CommandSender;

import org.bukkit.entity.Player;

/** Player balance command. */
public final class BalanceCommand implements TabExecutor
{
    private final EconomyService economy;
    private final MessageService messages;

    /** @param economy economy API */
    public BalanceCommand(final EconomyService economy, final MessageService messages) {
        this.economy = economy;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label,
            final String[] args) {
        if (args.length > 2)
            return false;
        final Currency currency = args.length == 0 ? economy.currencies().stream().sorted(
                java.util.Comparator.comparing(Currency::id)).findFirst().orElse(null) : economy.currency(args[0]).orElse(null);
        if (currency == null)
        {
            messages.send(sender, "unknown-currency");
            return true;
        }
        final OfflinePlayer target;
        if (args.length == 2)
        {
            if (!sender.hasPermission("companioneconomy.balance.others"))
            {
                messages.send(sender, "balance-others-denied");
                return true;
            }
            target = CommandSupport.player(args[1]);
            if (target == null)
            {
                messages.send(sender, "unknown-player");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            messages.send(sender, "balance-console-usage");
            return true;
        }
        messages.send(sender, "balance-show", "player",
                target.getName() == null ? target.getUniqueId().toString() : target.getName(), "amount",
                CommandSupport.format(currency, economy.balance(target.getUniqueId(), currency.id())));
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command,
            final String alias, final String[] args) {
        return args.length == 1 ? CommandSupport.currencies(economy, args[0]) : List.of();
    }
}
