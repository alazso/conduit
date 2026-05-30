package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single entry in a balance leaderboard ("baltop").
 *
 * @param rank     the 1-based position in the ranking (1 is the wealthiest)
 * @param owner    the account owner's UUID
 * @param currency the currency the balance is denominated in
 * @param amount   the balance amount
 */
@ApiStatus.AvailableSince("1.0.0")
public record RankedBalance(
        int rank,
        @NotNull UUID owner,
        @NotNull Currency currency,
        @NotNull BigDecimal amount
) {
    /**
     * Canonical constructor; validates the rank is positive.
     */
    public RankedBalance {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got " + rank);
        }
    }
}
