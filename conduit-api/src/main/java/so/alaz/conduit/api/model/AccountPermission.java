package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;

/**
 * Granular permissions for shared bank account membership.
 *
 * <p>Used by {@link so.alaz.conduit.api.economy.BankingEconomy} to model what a
 * member can do on an account they do not own.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AccountPermission {
    /** Can view the account balance. */
    BALANCE,
    /** Can deposit into the account. */
    DEPOSIT,
    /** Can withdraw from the account. */
    WITHDRAW,
    /** Can transfer funds out of the account. */
    TRANSFER,
    /** Full owner rights — implies all others. */
    OWNER,
    /** Shorthand for all permissions. Useful for granting full access without OWNER semantics. */
    ALL;

    /**
     * @param other the permission to test for inclusion
     * @return {@code true} if holding {@code this} permission grants {@code other}
     */
    public boolean includes(AccountPermission other) {
        return this == ALL || this == other || (this == OWNER && other != ALL);
    }
}
