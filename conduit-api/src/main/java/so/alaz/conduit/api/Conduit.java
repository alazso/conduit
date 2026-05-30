package so.alaz.conduit.api;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.api.registry.ProviderRegistry;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Static entry point to the Conduit runtime.
 *
 * <p>The {@link ProviderRegistry} and the economy decorator are installed by
 * {@code conduit-core} during plugin bootstrap. The decorator wraps resolved
 * {@link Economy} providers with the dispatch layer (synchronous amount
 * validation, pre-auth interceptors, post-commit events); in environments where
 * core is not present (e.g. pure unit tests) it defaults to identity.
 */
public final class Conduit {

    private static volatile ProviderRegistry registry;
    private static volatile UnaryOperator<Economy> economyDecorator = UnaryOperator.identity();

    private Conduit() {
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
        return economyDecorator.apply(getRegistry().requireProvider(Economy.class));
    }

    /**
     * @return the active economy provider wrapped with the dispatch layer, or empty
     * @throws IllegalStateException if the Conduit runtime is not initialised
     */
    public static @NotNull Optional<Economy> findEconomy() {
        return getRegistry().getProvider(Economy.class).map(economyDecorator);
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
     * Install the economy dispatch decorator. Called once by {@code conduit-core}.
     *
     * @param decorator wraps a raw economy provider with the dispatch layer
     */
    @ApiStatus.Internal
    public static void setEconomyDecorator(@NotNull UnaryOperator<Economy> decorator) {
        economyDecorator = decorator;
    }

    /**
     * Tear down the runtime references. Called by {@code conduit-core} on disable
     * and usable by tests to reset global state.
     */
    @ApiStatus.Internal
    public static void shutdown() {
        registry = null;
        economyDecorator = UnaryOperator.identity();
    }
}
