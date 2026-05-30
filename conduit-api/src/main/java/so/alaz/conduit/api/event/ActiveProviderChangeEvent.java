package so.alaz.conduit.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when the active (highest-priority) provider changes for a service.
 *
 * <p>Computed per base service key: a registration that changes which provider
 * {@code getProvider(base)} returns fires one event for that base key.
 *
 * @param <T> the service type
 */
@ApiStatus.AvailableSince("1.0.0")
public class ActiveProviderChangeEvent<T> extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Class<T> service;
    private final @Nullable T previousProvider;
    private final T newProvider;

    /**
     * @param service          the base service key
     * @param previousProvider the previously active provider, or {@code null}
     * @param newProvider      the newly active provider
     */
    public ActiveProviderChangeEvent(@NotNull Class<T> service, @Nullable T previousProvider, @NotNull T newProvider) {
        this.service = service;
        this.previousProvider = previousProvider;
        this.newProvider = newProvider;
    }

    /**
     * @return the base service key
     */
    public @NotNull Class<T> getService() {
        return service;
    }

    /**
     * @return the previously active provider, or {@code null} if there was none
     */
    public @Nullable T getPreviousProvider() {
        return previousProvider;
    }

    /**
     * @return the newly active provider
     */
    public @NotNull T getNewProvider() {
        return newProvider;
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
