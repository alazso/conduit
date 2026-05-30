package so.alaz.conduit.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.model.Transaction;

/**
 * Fired after an economy transaction commits.
 *
 * <p>Post-commit and non-cancellable: by the time a handler sees this event the
 * transaction is already on disk. For pre-authorisation use
 * {@link EconomyTransactionInterceptor} instead. The event is constructed
 * {@code async = true} because it is published from the async completion of a
 * {@link java.util.concurrent.CompletableFuture}.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EconomyTransactionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Transaction transaction;
    private final CallerToken caller;

    /**
     * @param transaction the committed transaction
     * @param caller      the caller that initiated the operation
     */
    public EconomyTransactionEvent(@NotNull Transaction transaction, @NotNull CallerToken caller) {
        super(true);
        this.transaction = transaction;
        this.caller = caller;
    }

    /**
     * @return the committed transaction
     */
    public @NotNull Transaction getTransaction() {
        return transaction;
    }

    /**
     * @return the caller that initiated the operation
     */
    public @NotNull CallerToken getCaller() {
        return caller;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * @return the shared handler list for this event type
     */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
