package model.persistence;

import model.security.SecurityEvent;
import model.security.SecurityEventType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link SecurityLogRepository} that keeps the log in memory, for tests. Thread-safe.
 */
public final class InMemorySecurityLogRepository implements SecurityLogRepository {

    private final List<SecurityEvent> events = new ArrayList<>();

    @Override
    public synchronized SecurityEvent append(Instant occurredAt, SecurityEventType type, String actor,
                                             String subject, String detail) {
        SecurityEvent event = new SecurityEvent(events.size() + 1L, occurredAt, type, actor, subject, detail);
        events.add(event);
        return event;
    }

    @Override
    public synchronized List<SecurityEvent> findNewestFirst(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
        List<SecurityEvent> newest = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && newest.size() < limit; i--) {
            newest.add(events.get(i));
        }
        return List.copyOf(newest);
    }

    @Override
    public synchronized Optional<Instant> findLatest(SecurityEventType type, String subject) {
        String cleaned = SecurityEvent.clean(subject);
        return events.stream()
                .filter(e -> e.type() == type && e.subject().equals(cleaned))
                .map(SecurityEvent::occurredAt)
                .max(Comparator.naturalOrder());
    }

    @Override
    public synchronized int countAfter(SecurityEventType type, String subject, Instant after) {
        Objects.requireNonNull(after, "after");
        String cleaned = SecurityEvent.clean(subject);
        return (int) events.stream()
                .filter(e -> e.type() == type && e.subject().equals(cleaned) && e.occurredAt().isAfter(after))
                .count();
    }
}
