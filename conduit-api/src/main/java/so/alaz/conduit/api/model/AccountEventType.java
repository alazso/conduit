package so.alaz.conduit.api.model;

import org.jetbrains.annotations.ApiStatus;

/**
 * Describes what happened to an account in an
 * {@link so.alaz.conduit.api.event.EconomyAccountEvent}.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AccountEventType {
    /** An account was created. */
    CREATED,
    /** An account was deleted. */
    DELETED
}
