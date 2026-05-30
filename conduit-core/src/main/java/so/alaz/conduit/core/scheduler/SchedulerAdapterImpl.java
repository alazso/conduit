package so.alaz.conduit.core.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link SchedulerAdapter} backed by Paper's region scheduler API. These
 * schedulers are present on both Paper and Folia (Paper provides main-thread
 * equivalents), so a single code path is correct on both platforms.
 */
@ApiStatus.Internal
public final class SchedulerAdapterImpl implements SchedulerAdapter {

    private static final long MIN_TICKS = 1L;
    private static final long MILLIS_PER_TICK = 50L;

    private final Plugin plugin;

    public SchedulerAdapterImpl(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void runAsync(@NotNull Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public void runOnEntity(@NotNull Entity entity, @NotNull Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), task);
    }

    @Override
    public void runOnRegion(@NotNull Location location, @NotNull Runnable task) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, task);
    }

    @Override
    public void runGlobal(@NotNull Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public @NotNull ConduitTask scheduleRepeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period) {
        long delayTicks = Math.max(MIN_TICKS, delay.toMillis() / MILLIS_PER_TICK);
        long periodTicks = Math.max(MIN_TICKS, period.toMillis() / MILLIS_PER_TICK);
        ScheduledTask handle = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
        return new ConduitTaskImpl(handle);
    }

    /**
     * Schedule a one-shot async task after a delay.
     *
     * @param task  the task
     * @param delay the delay
     */
    public void runAsyncLater(@NotNull Runnable task, @NotNull Duration delay) {
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, t -> task.run(), Math.max(1L, delay.toMillis()), TimeUnit.MILLISECONDS);
    }

    private record ConduitTaskImpl(ScheduledTask handle) implements ConduitTask {
        @Override
        public void cancel() {
            handle.cancel();
        }

        @Override
        public boolean isCancelled() {
            return handle.isCancelled();
        }
    }
}
