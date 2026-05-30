package so.alaz.conduit.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a new provider registers for a service.
 *
 * @param <T> the service type
 */
@ApiStatus.AvailableSince("1.0.0")
public class ProviderRegisterEvent<T> extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Class<T> service;
    private final T provider;
    private final ServicePriority priority;
    private final Plugin registrant;

    /**
     * @param service    the service type registered under
     * @param provider   the provider instance
     * @param priority   the registration priority
     * @param registrant the registering plugin
     */
    public ProviderRegisterEvent(@NotNull Class<T> service, @NotNull T provider, @NotNull ServicePriority priority, @NotNull Plugin registrant) {
        this.service = service;
        this.provider = provider;
        this.priority = priority;
        this.registrant = registrant;
    }

    /**
     * @return the service type registered under
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

    /**
     * @return the registration priority
     */
    public @NotNull ServicePriority getPriority() {
        return priority;
    }

    /**
     * @return the registering plugin
     */
    public @NotNull Plugin getRegistrant() {
        return registrant;
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
