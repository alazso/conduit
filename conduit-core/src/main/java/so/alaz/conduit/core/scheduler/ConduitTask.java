package so.alaz.conduit.core.scheduler;

import org.jetbrains.annotations.ApiStatus;

/**
 * A handle to a scheduled repeating task. Wraps the platform-native task
 * (Bukkit on Paper, the region/global equivalent on Folia).
 */
@ApiStatus.Internal
public interface ConduitTask {

    /** Cancel the task. */
    void cancel();

    /**
     * @return {@code true} if the task has been cancelled
     */
    boolean isCancelled();
}
