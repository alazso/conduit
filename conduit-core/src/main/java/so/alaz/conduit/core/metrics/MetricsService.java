package so.alaz.conduit.core.metrics;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * bStats metrics wiring. Constructed only when the bStats classes are present on
 * the runtime classpath (guarded by the caller), so the hard reference here is
 * safe.
 */
@ApiStatus.Internal
public final class MetricsService {

    private final Metrics metrics;

    /**
     * @param plugin             the owning plugin
     * @param bstatsPluginId     the allocated bStats plugin id (must be positive)
     * @param activeProviderName supplier of the active economy provider name
     */
    public MetricsService(@NotNull JavaPlugin plugin, int bstatsPluginId, @NotNull Supplier<String> activeProviderName) {
        if (bstatsPluginId <= 0) {
            throw new IllegalArgumentException("bStats plugin id must be positive, got " + bstatsPluginId);
        }
        this.metrics = new Metrics(plugin, bstatsPluginId);
        metrics.addCustomChart(new SimplePie("active_economy_provider", activeProviderName::get));
    }

    /**
     * Shut down the metrics submitter.
     */
    public void shutdown() {
        metrics.shutdown();
    }
}
