package so.alaz.conduit.core.scheduler;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Abstracts scheduling differences between Paper and Folia.
 *
 * <p>All internal Conduit operations route through this adapter so the same
 * code path is correct on both the single main thread (Paper) and the
 * region/entity threading model (Folia).
 */
@ApiStatus.Internal
public interface SchedulerAdapter {

    /**
     * Run a task on an asynchronous worker thread.
     *
     * @param task the task
     */
    void runAsync(@NotNull Runnable task);

    /**
     * Run a task on the thread owning the given entity (Folia entity scheduler;
     * main thread on Paper).
     *
     * @param entity the entity
     * @param task   the task
     */
    void runOnEntity(@NotNull Entity entity, @NotNull Runnable task);

    /**
     * Run a task on the thread owning the given location (Folia region
     * scheduler; main thread on Paper).
     *
     * @param location the location
     * @param task     the task
     */
    void runOnRegion(@NotNull Location location, @NotNull Runnable task);

    /**
     * Run a task on the global scheduler (Folia) or the main thread (Paper).
     *
     * @param task the task
     */
    void runGlobal(@NotNull Runnable task);

    /**
     * Schedule a repeating task on the global/main scheduler.
     *
     * @param task   the task
     * @param delay  the initial delay
     * @param period the period between runs
     * @return a handle to cancel the task
     */
    @NotNull ConduitTask scheduleRepeating(@NotNull Runnable task, @NotNull Duration delay, @NotNull Duration period);
}
