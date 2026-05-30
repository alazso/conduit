package so.alaz.conduit.example.points;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;

/**
 * {@code /points} — reports the sender's balance in the points economy.
 *
 * <p>Because the server has more than one provider, this targets this plugin's
 * own points provider directly rather than {@code Conduit.getEconomy()} (which
 * resolves to whichever provider is active).
 */
final class PointsWalletCommand extends Command {

    private final ConduitPointsPlugin plugin;

    PointsWalletCommand(ConduitPointsPlugin plugin) {
        super("points");
        this.plugin = plugin;
        setDescription("Show your Conduit example points balance.");
        setUsage("/points");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ConduitPointsPlugin.error("Only players have a points balance."));
            return true;
        }
        Economy eco = plugin.economy();
        CallerToken.runWith(plugin.callerToken(), () -> eco.getBalance(player.getUniqueId())
                .thenAccept(balance -> player.sendMessage(
                        ConduitPointsPlugin.info("Points: " + eco.format(balance.amount()))))
                .exceptionally(throwable -> {
                    player.sendMessage(ConduitPointsPlugin.error("Could not read points: " + throwable.getMessage()));
                    return null;
                }));
        return true;
    }
}
