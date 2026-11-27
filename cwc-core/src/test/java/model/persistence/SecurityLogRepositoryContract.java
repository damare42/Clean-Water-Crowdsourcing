package model.persistence;

import model.security.SecurityEvent;
import model.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour every {@link SecurityLogRepository} must have.
 */
abstract class SecurityLogRepositoryContract {

    protected static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    protected SecurityLogRepository repository;

    protected abstract SecurityLogRepository createRepository();

    @BeforeEach
    void setUpRepository() {
        repository = createRepository();
    }

    @Test
    void emptyLog() {
        assertTrue(repository.findNewestFirst(10).isEmpty());
        assertEquals(Optional.empty(), repository.findLatest(SecurityEventType.LOGIN_FAILURE, "alice"));
        assertEquals(0, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "alice", Instant.EPOCH));
    }

    @Test
    void appendAssignsIncreasingSequenceAndKeepsFields() {
        SecurityEvent first = repository.append(T0, SecurityEventType.REGISTRATION, "Alice", "Alice", "Worker");
        SecurityEvent second = repository.append(T0.plusMillis(1), SecurityEventType.LOGIN_FAILURE, null,
                "bob", "wrong password");

        assertTrue(second.sequence() > first.sequence());
        assertEquals(new SecurityEvent(first.sequence(), T0, SecurityEventType.REGISTRATION, "Alice", "Alice",
                "Worker"), first);
        assertEquals("", second.actor());
        assertEquals(List.of(second, first), repository.findNewestFirst(10));
        assertEquals(List.of(second), repository.findNewestFirst(1));
    }

    @Test
    void everyTypeRoundTrips() {
        for (SecurityEventType type : SecurityEventType.values()) {
            repository.append(T0, type, "a", "s", "d");
        }
        List<SecurityEvent> events = repository.findNewestFirst(100);
        assertEquals(SecurityEventType.values().length, events.size());
        for (int i = 0; i < events.size(); i++) {
            assertEquals(SecurityEventType.values()[events.size() - 1 - i], events.get(i).type());
        }
    }

    @Test
    void storedTextIsCleaned() {
        repository.append(T0, SecurityEventType.LOGIN_FAILURE, "", "evil\nid" + "x".repeat(400), null);

        SecurityEvent stored = repository.findNewestFirst(1).get(0);
        assertEquals(SecurityEvent.MAX_TEXT_LENGTH, stored.subject().length());
        assertTrue(stored.subject().startsWith("evil id"));
        assertEquals("", stored.detail());
        assertEquals(1, repository.countAfter(SecurityEventType.LOGIN_FAILURE,
                "evil\nid" + "x".repeat(400), Instant.EPOCH), "lookups clean the subject the same way");
    }

    @Test
    void latestAndCountFilterByTypeSubjectAndTime() {
        repository.append(T0, SecurityEventType.LOGIN_FAILURE, "", "alice", "");
        repository.append(T0.plusSeconds(60), SecurityEventType.LOGIN_FAILURE, "", "alice", "");
        repository.append(T0.plusSeconds(120), SecurityEventType.LOGIN_FAILURE, "", "bob", "");
        repository.append(T0.plusSeconds(180), SecurityEventType.LOGIN_SUCCESS, "Alice", "alice", "");

        assertEquals(Optional.of(T0.plusSeconds(60)), repository.findLatest(SecurityEventType.LOGIN_FAILURE, "alice"));
        assertEquals(Optional.of(T0.plusSeconds(180)), repository.findLatest(SecurityEventType.LOGIN_SUCCESS, "alice"));
        assertEquals(Optional.empty(), repository.findLatest(SecurityEventType.LOGIN_SUCCESS, "bob"));
        assertEquals(Optional.empty(), repository.findLatest(SecurityEventType.LOGIN_FAILURE, "ALICE"));
        assertEquals(2, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "alice", T0.minusMillis(1)));
        assertEquals(1, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "alice", T0), "strictly after");
        assertEquals(0, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "alice", T0.plusSeconds(60)));
        assertEquals(1, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "bob", T0));
    }

    @Test
    void subMillisecondBoundsBehaveTheSameEverywhere() {
        repository.append(T0, SecurityEventType.LOGIN_FAILURE, "", "alice", "");

        assertEquals(1, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "alice", T0.minusNanos(1)));
        assertEquals(0, repository.countAfter(SecurityEventType.LOGIN_FAILURE, "alice", T0.plusNanos(1)));
    }

    @Test
    void invalidLimitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.findNewestFirst(0));
    }
}
