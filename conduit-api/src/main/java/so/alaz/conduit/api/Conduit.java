package so.alaz.conduit.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.registry.ProviderRegistry;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Static entry point to the Conduit runtime.
 *
 * <p>The {@link ProviderRegistry} is installed by {@code conduit-core} during
 * plugin bootstrap. The registry is the sole owner of dispatch decoration: every
 * resolved {@link Economy} it returns is already wrapped with the dispatch layer
 * (synchronous amount validation, pre-auth interceptors, post-commit events), so
 * this facade simply forwards to it.
 */
public final class Conduit {

    /**
     * The {@code major.minor} version of the Conduit API this runtime ships.
     * Providers declare a minimum via {@link Economy#requiredApiVersion()}; the
     * registry rejects providers requiring a newer API than this.
     */
    public static final String API_VERSION = "1.0";

    private static volatile ProviderRegistry registry;

    private Conduit() {
    }

    /**
     * @return {@code true} if the Conduit runtime has been initialised (the
     *         plugin is enabled); {@code false} before enable or after disable
     */
    public static boolean isInitialized() {
        return registry != null;
    }

    /**
     * @return the active provider registry
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull ProviderRegistry getRegistry() {
        ProviderRegistry r = registry;
        if (r == null) {
            throw new IllegalStateException(
                    "Conduit runtime is not initialised. Is the Conduit plugin installed and enabled?");
        }
        return r;
    }

    /**
     * @return the active economy provider, wrapped with the dispatch layer
     * @throws so.alaz.conduit.api.exception.ProviderNotFoundException if none is registered
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull Economy getEconomy() {
        return getRegistry().requireProvider(Economy.class);
    }

    /**
     * @return the active economy provider wrapped with the dispatch layer, or empty
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull Optional<Economy> findEconomy() {
        return getRegistry().getProvider(Economy.class);
    }

    /**
     * Order-insensitive provider consumption; preferred over {@link #getEconomy()}
     * in {@code onEnable()}.
     *
     * @param service  the service type of interest
     * @param consumer the consumer of the provider
     * @param <T>      the service type
     */
    public static <T> void whenProviderAvailable(@NotNull Class<T> service, @NotNull Consumer<T> consumer) {
        getRegistry().whenProviderAvailable(service, consumer);
    }

    /**
     * Install the runtime registry. Called once by {@code conduit-core}.
     *
     * @param providerRegistry the registry implementation
     */
    @ApiStatus.Internal
    public static void init(@NotNull ProviderRegistry providerRegistry) {
        registry = providerRegistry;
    }

    /**
     * Tear down the runtime references. Called by {@code conduit-core} on disable
     * and usable by tests to reset global state.
     */
    @ApiStatus.Internal
    public static void shutdown() {
        registry = null;
    }
}
