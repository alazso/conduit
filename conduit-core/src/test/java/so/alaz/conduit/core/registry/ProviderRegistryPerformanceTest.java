package so.alaz.conduit.core.registry;

import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.economy.Economy;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.core.support.TestPlugins;
import so.alaz.conduit.testing.MockEconomy;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Performance smoke test guarding the hot provider-resolution path against
 * accidental algorithmic regressions (e.g. O(n^2) or lock contention). The
 * bound is deliberately generous so it does not flake on shared CI runners.
 */
class ProviderRegistryPerformanceTest {

    private static final int RESOLUTIONS = 200_000;
    private static final Duration GENEROUS_BUDGET = Duration.ofSeconds(5);

    @Test
    void resolution_stays_fast_under_repeated_lookups() {
        ProviderRegistryImpl registry = new ProviderRegistryImpl(new RecordingEventPublisher(), new InterceptorBus());
        registry.register(Economy.class, MockEconomy.builder().build(), TestPlugins.named("Eco"), ServicePriority.Normal);

        assertTimeoutPreemptively(GENEROUS_BUDGET, () -> {
            for (int i = 0; i < RESOLUTIONS; i++) {
                assertThat(registry.requireProvider(Economy.class)).isNotNull();
            }
        });
    }

    @Test
    void registry_is_threadsafe_under_concurrent_access() throws InterruptedException {
        ProviderRegistryImpl registry = new ProviderRegistryImpl(new RecordingEventPublisher(), new InterceptorBus());
        registry.register(Economy.class, MockEconomy.builder().build(), TestPlugins.named("Eco"), ServicePriority.Normal);

        int threads = 8;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            var latch = new java.util.concurrent.CountDownLatch(threads);
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    for (int i = 0; i < 25_000; i++) {
                        registry.getProvider(Economy.class);
                    }
                    latch.countDown();
                });
            }
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }
}
