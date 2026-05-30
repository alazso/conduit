package so.alaz.conduit.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a provider unregisters from a service.
 *
 * @param <T> the service type
 */
@ApiStatus.AvailableSince("1.0.0")
public class ProviderUnregisterEvent<T> extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Class<T> service;
    private final T provider;

    /**
     * @param service  the service type unregistered from
     * @param provider the provider instance
     */
    public ProviderUnregisterEvent(@NotNull Class<T> service, @NotNull T provider) {
        this.service = service;
        this.provider = provider;
    }

    /**
     * @return the service type unregistered from
     */
    public @NotNull Class<T> getService() {
        return service;
    }

    /**
     * @return the provider instance
     */
    public @NotNull T getProvider() {
        return provider;
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
