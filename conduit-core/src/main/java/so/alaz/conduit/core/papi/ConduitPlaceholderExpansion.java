package so.alaz.conduit.core.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.Conduit;
import so.alaz.conduit.api.economy.Economy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion exposing economy balances.
 *
 * <p>PlaceholderAPI resolves placeholders synchronously, but Conduit balances
 * are async. Each lookup serves the last cached value (or zero) and kicks off an
 * async refresh, so subsequent lookups converge on the live balance without
 * blocking the main thread. The expansion deliberately does <em>not</em>
 * implement {@code Listener}: PlaceholderAPI auto-registers listener expansions
 * via an internal {@code registerEvents} call that Paper's modern loader rejects
 * during plugin enable.
 *
 * <p>Supported placeholders: {@code %conduit_balance%},
 * {@code %conduit_balance_formatted%}, {@code %conduit_currency_symbol%},
 * {@code %conduit_provider%}.
 */
@ApiStatus.Internal
public final class ConduitPlaceholderExpansion extends PlaceholderExpansion {

    private final String version;
    private final ConcurrentHashMap<UUID, BigDecimal> balanceCache = new ConcurrentHashMap<>();

    public ConduitPlaceholderExpansion(@NotNull String version) {
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "conduit";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Alazso";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        Optional<Economy> economy = Conduit.findEconomy();
        return switch (params.toLowerCase()) {
            case "provider" -> economy.map(Economy::getName).orElse("none");
            case "currency_symbol" -> economy.map(e -> e.defaultCurrency().symbol()).orElse("");
            case "balance" -> economy.map(e -> balance(e, player).toPlainString()).orElse("0");
            case "balance_formatted" -> economy
                    .map(e -> e.defaultCurrency().format(balance(e, player)))
                    .orElse("");
            default -> null;
        };
    }

    private BigDecimal balance(Economy economy, OfflinePlayer player) {
        if (player == null) {
            return BigDecimal.ZERO;
        }
        UUID uuid = player.getUniqueId();
        economy.getBalance(uuid).thenAccept(b -> balanceCache.put(uuid, b.amount()));
        return balanceCache.getOrDefault(uuid, BigDecimal.ZERO);
    }
}
