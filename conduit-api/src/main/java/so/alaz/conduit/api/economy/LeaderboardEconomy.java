package so.alaz.conduit.api.economy;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.model.RankedBalance;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Economy providers that can rank accounts by balance ("baltop").
 *
 * <p>This is a <strong>structural capability</strong>: a provider's ability to
 * produce a leaderboard is signalled by implementing this interface, exactly as
 * banking and multi-currency are. There is deliberately no {@code Capability}
 * flag for it. A generic fallback is impossible — the base {@link Economy}
 * surface has no way to enumerate every account — so consumers that need
 * baltop must resolve {@code getProvider(LeaderboardEconomy.class)} and handle
 * its absence.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface LeaderboardEconomy extends Economy {

    /**
     * @param offset the number of top entries to skip (for pagination); {@code 0} starts at the wealthiest
     * @param limit  the maximum number of entries to return
     * @return a future resolving the ranked balances in the default currency,
     *         wealthiest first; ranks are absolute (offset-aware)
     */
    @NotNull CompletableFuture<List<RankedBalance>> getTopBalances(int offset, int limit);

    /**
     * Convenience for the first {@code limit} entries.
     *
     * @param limit the maximum number of entries to return
     * @return a future resolving the wealthiest {@code limit} accounts
     */
    default @NotNull CompletableFuture<List<RankedBalance>> getTopBalances(int limit) {
        return getTopBalances(0, limit);
    }
}
