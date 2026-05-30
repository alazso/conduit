package so.alaz.conduit.core.support;

import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;

import so.alaz.conduit.core.events.EventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Test {@link EventPublisher} that records events synchronously on the calling
 * thread, so tests can assert on what was published without a live server.
 */
public final class RecordingEventPublisher implements EventPublisher {

    private final List<Event> events = new ArrayList<>();

    @Override
    public synchronized void publish(@NotNull Event event) {
        events.add(event);
    }

    public synchronized List<Event> events() {
        return List.copyOf(events);
    }

    @SuppressWarnings("unchecked")
    public synchronized <T extends Event> List<T> ofType(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Event e : events) {
            if (type.isInstance(e)) {
                out.add((T) e);
            }
        }
        return out;
    }

    public synchronized void clear() {
        events.clear();
    }
}
