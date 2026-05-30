package so.alaz.conduit.core.interceptor;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.api.event.EconomyTransactionInterceptor;
import so.alaz.conduit.api.event.EconomyTransactionInterceptor.InterceptContext;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds registered {@link EconomyTransactionInterceptor}s and runs the
 * synchronous pre-authorisation pass before an economy operation begins.
 *
 * <p>Interceptors run in descending {@link ServicePriority} order; within equal
 * priority, registration order is preserved. The first interceptor to veto
 * aborts the pass and short-circuits the rest.
 */
@ApiStatus.Internal
public final class InterceptorBus {

    private record Entry(EconomyTransactionInterceptor interceptor, Plugin plugin, ServicePriority priority, long seq) {}

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();

    /**
     * Register an interceptor.
     *
     * @param interceptor the interceptor
     * @param plugin      the registering plugin
     * @param priority    the priority (highest runs first)
     */
    public void register(@NotNull EconomyTransactionInterceptor interceptor, @NotNull Plugin plugin, @NotNull ServicePriority priority) {
        entries.add(new Entry(interceptor, plugin, priority, sequence.getAndIncrement()));
    }

    /**
     * Remove a registered interceptor (by identity).
     *
     * @param interceptor the interceptor to remove
     */
    public void unregister(@NotNull EconomyTransactionInterceptor interceptor) {
        entries.removeIf(e -> e.interceptor() == interceptor);
    }

    /**
     * Run the synchronous pre-authorisation pass.
     *
     * @param context the operation about to be attempted
     * @return {@code true} if all interceptors allow the operation; {@code false}
     *         if any vetoes it
     */
    public boolean preAuthorize(@NotNull InterceptContext context) {
        for (Entry entry : ordered()) {
            if (!entry.interceptor().intercept(context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the number of registered interceptors
     */
    public int size() {
        return entries.size();
    }

    private List<Entry> ordered() {
        return entries.stream()
                .sorted(Comparator
                        .comparingInt((Entry e) -> e.priority().ordinal()).reversed()
                        .thenComparingLong(Entry::seq))
                .toList();
    }
}
