package so.alaz.conduit.core.registry;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import so.alaz.conduit.api.caller.CallerToken;
import so.alaz.conduit.api.event.ActiveProviderChangeEvent;
import so.alaz.conduit.api.event.ProviderRegisterEvent;
import so.alaz.conduit.api.event.ProviderUnregisterEvent;
import so.alaz.conduit.api.exception.ProviderNotFoundException;
import so.alaz.conduit.core.interceptor.InterceptorBus;
import so.alaz.conduit.core.support.RecordingEventPublisher;
import so.alaz.conduit.core.support.TestPlugins;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("rawtypes") // ofType(ActiveProviderChangeEvent.class) intentionally uses the raw event type
class ProviderRegistryImplTest {

    // Sibling extension interfaces over a common base, mirroring the economy hierarchy.
    interface Base {}
    interface ExtA extends Base {}
    interface ExtB extends Base {}
    interface Composite extends ExtA, ExtB {}

    static final class BaseProvider implements Base {}
    static final class ExtAProvider implements ExtA {}
    static final class ExtBProvider implements ExtB {}
    static final class CompositeProvider implements Composite {}

    private RecordingEventPublisher events;
    private ProviderRegistryImpl registry;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        events = new RecordingEventPublisher();
        registry = new ProviderRegistryImpl(events, new InterceptorBus());
        plugin = TestPlugins.named("Test");
    }

    @Test
    void resolves_by_walking_the_registered_type_hierarchy() {
        ExtAProvider provider = new ExtAProvider();
        registry.register(ExtA.class, provider, plugin, ServicePriority.Normal);

        assertThat(registry.getProvider(ExtA.class)).contains(provider);
        assertThat(registry.getProvider(Base.class)).contains(provider);
        assertThat(registry.getProvider(ExtB.class)).isEmpty();
    }

    @Test
    void composite_registration_resolves_all_extension_views_to_one_instance() {
        CompositeProvider provider = new CompositeProvider();
        registry.register(Composite.class, provider, plugin, ServicePriority.High);

        assertThat(registry.getProvider(Base.class)).contains(provider);
        assertThat(registry.getProvider(ExtA.class)).contains(provider);
        assertThat(registry.getProvider(ExtB.class)).contains(provider);
        assertThat(registry.getProvider(Composite.class)).contains(provider);
    }

    @Test
    void duplicate_instance_registration_is_rejected() {
        BaseProvider provider = new BaseProvider();
        registry.register(Base.class, provider, plugin, ServicePriority.Normal);

        assertThatThrownBy(() -> registry.register(Base.class, provider, plugin, ServicePriority.High))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void highest_priority_wins_regardless_of_subtype_specificity() {
        BaseProvider baseHigh = new BaseProvider();
        ExtAProvider extNormal = new ExtAProvider();
        registry.register(Base.class, baseHigh, plugin, ServicePriority.High);
        registry.register(ExtA.class, extNormal, plugin, ServicePriority.Normal);

        // Subtype specificity is NOT a tiebreaker — priority is.
        assertThat(registry.getProvider(Base.class)).contains(baseHigh);
    }

    @Test
    void equal_priority_breaks_ties_by_registration_order() {
        BaseProvider first = new BaseProvider();
        BaseProvider second = new BaseProvider();
        registry.register(Base.class, first, plugin, ServicePriority.Normal);
        registry.register(Base.class, second, plugin, ServicePriority.Normal);

        assertThat(registry.getProvider(Base.class)).contains(first);
        assertThat(registry.getProviders(Base.class)).containsExactly(first, second);
    }

    @Test
    void getProviders_returns_priority_order_highest_first() {
        BaseProvider low = new BaseProvider();
        BaseProvider high = new BaseProvider();
        registry.register(Base.class, low, plugin, ServicePriority.Low);
        registry.register(Base.class, high, plugin, ServicePriority.Highest);

        assertThat(registry.getProviders(Base.class)).containsExactly(high, low);
    }

    @Test
    void requireProvider_throws_when_absent() {
        assertThatThrownBy(() -> registry.requireProvider(Base.class))
                .isInstanceOf(ProviderNotFoundException.class);
    }

    @Test
    void whenProviderAvailable_runs_immediately_if_present() {
        BaseProvider provider = new BaseProvider();
        registry.register(Base.class, provider, plugin, ServicePriority.Normal);

        AtomicReference<Base> seen = new AtomicReference<>();
        registry.whenProviderAvailable(Base.class, seen::set);

        assertThat(seen.get()).isSameAs(provider);
    }

    @Test
    void whenProviderAvailable_defers_until_registration() {
        List<Base> seen = new ArrayList<>();
        registry.whenProviderAvailable(Base.class, seen::add);
        assertThat(seen).isEmpty();

        BaseProvider provider = new BaseProvider();
        registry.register(Base.class, provider, plugin, ServicePriority.Normal);

        assertThat(seen).containsExactly(provider);
    }

    @Test
    void register_fires_register_and_active_change_events() {
        BaseProvider provider = new BaseProvider();
        registry.register(Base.class, provider, plugin, ServicePriority.Normal);

        assertThat(events.ofType(ProviderRegisterEvent.class)).hasSize(1);
        List<ActiveProviderChangeEvent> changes = events.ofType(ActiveProviderChangeEvent.class);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getPreviousProvider()).isNull();
        assertThat(changes.get(0).getNewProvider()).isSameAs(provider);
    }

    @Test
    void higher_priority_registration_fires_active_change_with_previous() {
        BaseProvider normal = new BaseProvider();
        BaseProvider high = new BaseProvider();
        registry.register(Base.class, normal, plugin, ServicePriority.Normal);
        events.clear();

        registry.register(Base.class, high, plugin, ServicePriority.High);

        List<ActiveProviderChangeEvent> changes = events.ofType(ActiveProviderChangeEvent.class);
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getPreviousProvider()).isSameAs(normal);
        assertThat(changes.get(0).getNewProvider()).isSameAs(high);
    }

    @Test
    void unregister_removes_and_fires_unregister_event() {
        BaseProvider provider = new BaseProvider();
        registry.register(Base.class, provider, plugin, ServicePriority.Normal);
        events.clear();

        registry.unregister(Base.class, provider);

        assertThat(registry.getProvider(Base.class)).isEmpty();
        assertThat(events.ofType(ProviderUnregisterEvent.class)).hasSize(1);
    }

    @Test
    void registerCaller_is_idempotent() {
        CallerToken first = registry.registerCaller(plugin);
        CallerToken second = registry.registerCaller(plugin);

        assertThat(first).isSameAs(second);
        assertThat(first.pluginName()).isEqualTo("Test");
    }
}
