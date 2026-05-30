package so.alaz.conduit.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.model.AccountEventType;

import java.util.UUID;

/**
 * Fired after a player account is created or deleted. Post-commit and
 * non-cancellable.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EconomyAccountEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final AccountEventType type;

    /**
     * @param uuid the affected account UUID
     * @param type whether the account was created or deleted
     */
    public EconomyAccountEvent(@NotNull UUID uuid, @NotNull AccountEventType type) {
        super(true);
        this.uuid = uuid;
        this.type = type;
    }

    /**
     * @return the affected account UUID
     */
    public @NotNull UUID getUuid() {
        return uuid;
    }

    /**
     * @return whether the account was created or deleted
     */
    public @NotNull AccountEventType getType() {
        return type;
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
