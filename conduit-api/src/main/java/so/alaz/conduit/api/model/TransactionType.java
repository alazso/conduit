package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;

/**
 * Classifies the nature of a {@link Transaction}.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum TransactionType {
    /** Funds credited to an account. */
    DEPOSIT,
    /** Funds debited from an account. */
    WITHDRAWAL,
    /** Credit side of an atomic transfer. */
    TRANSFER_IN,
    /** Debit side of an atomic transfer. */
    TRANSFER_OUT,
    /** Funds credited to a shared bank account. */
    BANK_DEPOSIT,
    /** Funds debited from a shared bank account. */
    BANK_WITHDRAWAL,
    /** Balance overwritten by an administrative set operation. */
    ADMIN_SET,
    /** System- or scheduler-initiated operation with no player actor. */
    SYSTEM
}
