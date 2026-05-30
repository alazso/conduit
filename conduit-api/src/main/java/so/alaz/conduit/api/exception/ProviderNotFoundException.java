package so.alaz.conduit.api.exception;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown by {@code ProviderRegistry.requireProvider()} when no provider is
 * registered for the requested service type.
 */
@ApiStatus.AvailableSince("1.0.0")
public class ProviderNotFoundException extends RuntimeException {

    private final transient Class<?> service;

    /**
     * @param service the service type with no registered provider
     */
    public ProviderNotFoundException(@NotNull Class<?> service) {
        super("No provider registered for service: " + service.getSimpleName());
        this.service = service;
    }

    /**
     * @return the service type with no registered provider
     */
    public @NotNull Class<?> service() {
        return service;
    }
}
