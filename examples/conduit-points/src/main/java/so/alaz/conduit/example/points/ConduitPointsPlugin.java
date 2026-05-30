package so.alaz.conduit.example.points;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.economy.Economy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sibling of {@code conduit-economy}, but registers a whole-number "points"
 * {@link Economy}. With both plugins installed the server has two registered
 * economies, which is the precondition for the {@code conduit-shop} Case B demo.
 *
 * <p>Its consumer commands are named {@code /points} and {@code /sendpoints} so
 * they do not collide with {@code conduit-economy}'s {@code /wallet} and
 * {@code /pay}.
 */
public final class ConduitPointsPlugin extends JavaPlugin implements Listener {

    /** Starting points granted to brand-new accounts on first join. */
    static final BigDecimal STARTING_POINTS = new BigDecimal("100");

    private InMemoryPointsEconomy economy;
    private CallerToken callerToken;

    @Override
    public void onEnable() {
        this.economy = new InMemoryPointsEconomy();
        Conduit.getRegistry().register(Economy.class, economy, this, ServicePriority.Normal);
        this.callerToken = Conduit.getRegistry().registerCaller(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getCommandMap().register("conduitpoints", new PointsWalletCommand(this));
        getServer().getCommandMap().register("conduitpoints", new PointsPayCommand(this));

        getSLF4JLogger().info("Example points provider registered with Conduit; /points and /sendpoints ready.");
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            Conduit.getRegistry().unregister(Economy.class, economy);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Economy eco = economy;
        CallerToken.runWith(callerToken, () -> eco.hasAccount(id).thenAccept(has -> {
            if (!has) {
                CallerToken.runWith(callerToken, () ->
                        eco.createAccount(id).thenRun(() ->
                                CallerToken.runWith(callerToken, () -> eco.deposit(id, STARTING_POINTS))));
            }
        }));
    }

    /**
     * @return the caller token identifying this plugin to Conduit
     */
    CallerToken callerToken() {
        return callerToken;
    }

    /**
     * @return this plugin's points economy (its own provider instance)
     */
    Economy economy() {
        return economy;
    }

    static Component info(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    static Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }
}
