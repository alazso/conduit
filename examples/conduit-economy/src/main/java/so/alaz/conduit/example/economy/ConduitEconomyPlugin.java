package so.alaz.conduit.example.economy;

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
 * A small end-to-end Conduit test plugin for the dev server. It does two things:
 *
 * <ol>
 *   <li><b>Provides</b> an in-memory {@link Economy} so the Conduit stack has a
 *       backend to expose (visible via {@code /conduit economy} and PAPI).</li>
 *   <li><b>Consumes</b> <em>its own</em> economy in a join listener and the
 *       {@code /wallet} and {@code /pay} commands.</li>
 * </ol>
 *
 * <p><b>Independence:</b> this plugin operates on {@link #economy()} (its own
 * provider instance), <strong>not</strong> {@link Conduit#getEconomy()}. The
 * latter resolves to whichever provider is <em>active</em> across the whole
 * server, so a provider that consumes through it would silently read/write a
 * different economy when another provider has higher priority. Each provider's
 * balances are fully independent; a primary currency, points, and a premium
 * currency must never share state. The trade-off of targeting a specific
 * provider directly is that operations use the raw instance and so bypass the
 * dispatch decorator (validation/interceptors/events) that only wraps the active
 * provider.
 *
 * Balances are in-memory only and reset on restart, which is the point: it is a
 * disposable harness for verifying Conduit on a development server.
 */
public final class ConduitEconomyPlugin extends JavaPlugin implements Listener {

    /** Starting balance granted to brand-new accounts on first join. */
    static final BigDecimal STARTING_BALANCE = new BigDecimal("250.00");

    private InMemoryEconomy economy;
    private CallerToken callerToken;

    @Override
    public void onEnable() {
        this.economy = new InMemoryEconomy();
        Conduit.getRegistry().register(Economy.class, economy, this, ServicePriority.Normal);
        this.callerToken = Conduit.getRegistry().registerCaller(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getCommandMap().register("conduiteco", new WalletCommand(this));
        getServer().getCommandMap().register("conduiteco", new PayCommand(this));

        getSLF4JLogger().info("Example economy provider registered with Conduit; /wallet and /pay ready.");
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
                                CallerToken.runWith(callerToken, () -> eco.deposit(id, STARTING_BALANCE))));
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
     * @return this plugin's economy (its own provider instance, independent of
     *         any other registered economy)
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
