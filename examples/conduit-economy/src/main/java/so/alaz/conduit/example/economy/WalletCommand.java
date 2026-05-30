package so.alaz.conduit.example.economy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;

/**
 * {@code /wallet} — reports the sender's balance in this plugin's own economy.
 *
 * <p>Targets {@code plugin.economy()} directly rather than the active provider,
 * so the dollars balance stays independent of any other registered economy.
 */
final class WalletCommand extends Command {

    private final ConduitEconomyPlugin plugin;

    WalletCommand(ConduitEconomyPlugin plugin) {
        super("wallet");
        this.plugin = plugin;
        setDescription("Show your Conduit example-economy balance.");
        setUsage("/wallet");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ConduitEconomyPlugin.error("Only players have a wallet."));
            return true;
        }
        Economy eco = plugin.economy();
        CallerToken.runWith(plugin.callerToken(), () -> eco.getBalance(player.getUniqueId())
                .thenAccept(balance -> player.sendMessage(
                        ConduitEconomyPlugin.info("Balance: " + eco.format(balance.amount()))))
                .exceptionally(throwable -> {
                    player.sendMessage(ConduitEconomyPlugin.error("Could not read balance: " + throwable.getMessage()));
                    return null;
                }));
        return true;
    }
}
