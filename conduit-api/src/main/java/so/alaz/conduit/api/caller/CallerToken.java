package so.alaz.conduit.api.caller;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Identifies the plugin responsible for an economy operation.
 *
 * <p>Replaces the honor-system {@code String pluginName} parameter. A token is
 * obtained once from the {@code ProviderRegistry} at plugin startup and bound
 * via {@link ScopedValue} (JEP 506, stable in Java 25) at each call site so
 * events and interceptors can attribute operations.
 *
 * <h3>Threading contract</h3>
 * {@code ScopedValue} propagates into async work only through scope-aware
 * dispatch. Continuations on external executors (e.g.
 * {@code thenApplyAsync(fn, myExecutor)}) break propagation and must rebind the
 * token manually via {@link #runWith(CallerToken, Runnable)}. Callers that never
 * bind a token observe {@link #ANONYMOUS}.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class CallerToken {

    /** Sentinel for calls made without a bound token. */
    public static final CallerToken ANONYMOUS = new CallerToken(null, "anonymous");

    private static final ScopedValue<CallerToken> CURRENT = ScopedValue.newInstance();

    private final @Nullable Plugin plugin;
    private final String pluginName;
    private final UUID tokenId;

    private CallerToken(@Nullable Plugin plugin, @NotNull String pluginName) {
        this.plugin = plugin;
        this.pluginName = pluginName;
        this.tokenId = UUID.randomUUID();
    }

    /**
     * Internal factory used by the registry to mint a token bound to a plugin's
     * identity. Not part of the consumer-facing contract.
     *
     * @param plugin     the owning plugin
     * @param pluginName the plugin's display name
     * @return a fresh token
     */
    @ApiStatus.Internal
    public static @NotNull CallerToken create(@NotNull Plugin plugin, @NotNull String pluginName) {
        return new CallerToken(plugin, pluginName);
    }

    /**
     * Run an action with this token bound for its duration.
     *
     * @param token  the token to bind
     * @param action the action to run
     */
    public static void runWith(@NotNull CallerToken token, @NotNull Runnable action) {
        ScopedValue.where(CURRENT, token).run(action);
    }

    /**
     * Run a returning action with this token bound for its duration.
     *
     * @param token  the token to bind
     * @param action the action to invoke
     * @param <T>    the action's return type
     * @return the action's result
     * @throws Exception if the action throws
     */
    public static <T> T callWith(@NotNull CallerToken token, @NotNull Callable<T> action) throws Exception {
        // Java 25's stable ScopedValue.Carrier#call accepts a CallableOp; adapt
        // the public Callable contract via a method reference.
        return ScopedValue.where(CURRENT, token).call(action::call);
    }

    /**
     * @return the token bound to the current scope, or {@link #ANONYMOUS} if none
     */
    public static @NotNull CallerToken current() {
        return CURRENT.orElse(ANONYMOUS);
    }

    /**
     * Capture the currently-bound token and wrap {@code action} so it rebinds
     * that token when run, wherever it runs. Use this to preserve attribution
     * across async hops that would otherwise break {@link ScopedValue}
     * propagation (e.g. {@code thenApplyAsync(fn, executor)}).
     *
     * @param action the action to wrap
     * @return a runnable that rebinds the captured token for the action's duration
     */
    public static @NotNull Runnable wrapping(@NotNull Runnable action) {
        CallerToken captured = current();
        return () -> runWith(captured, action);
    }

    /**
     * Wrap an executor so every task submitted to it rebinds the token that was
     * current <em>at wrap time</em>. Continuations dispatched through the
     * returned executor therefore observe the correct caller.
     *
     * @param delegate the executor to wrap
     * @return a token-propagating executor
     */
    public static @NotNull Executor propagating(@NotNull Executor delegate) {
        CallerToken captured = current();
        return task -> delegate.execute(() -> runWith(captured, task));
    }

    /**
     * @return the owning plugin, or {@code null} for {@link #ANONYMOUS}
     */
    public @Nullable Plugin plugin() {
        return plugin;
    }

    /**
     * @return the owning plugin's display name
     */
    public @NotNull String pluginName() {
        return pluginName;
    }

    /**
     * @return this token's unique identifier
     */
    public @NotNull UUID tokenId() {
        return tokenId;
    }

    @Override
    public String toString() {
        return "CallerToken[" + pluginName + ", id=" + tokenId + "]";
    }
}
