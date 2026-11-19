package model.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityEventTest {

    private static final Instant WHEN = Instant.parse("2026-01-02T03:04:05.678912Z");

    @Test
    void textIsCleaned() {
        SecurityEvent event = new SecurityEvent(1, WHEN, SecurityEventType.LOGIN_FAILURE, null,
                "a\nb\tc\u0000d", "x".repeat(500));

        assertEquals("", event.actor());
        assertEquals("a b c d", event.subject());
        assertEquals(SecurityEvent.MAX_TEXT_LENGTH, event.detail().length());
        assertEquals(Instant.parse("2026-01-02T03:04:05.678Z"), event.occurredAt());
    }

    @Test
    void cleanKeepsOrdinaryText() {
        assertEquals("Ärger O'Brien <x>", SecurityEvent.clean("Ärger O'Brien <x>"));
        assertEquals("", SecurityEvent.clean(null));
    }

    @Test
    void invalidEventsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new SecurityEvent(0, WHEN, SecurityEventType.LOGIN_SUCCESS, "", "", ""));
        assertThrows(NullPointerException.class,
                () -> new SecurityEvent(1, null, SecurityEventType.LOGIN_SUCCESS, "", "", ""));
        assertThrows(NullPointerException.class, () -> new SecurityEvent(1, WHEN, null, "", "", ""));
    }

    @Test
    void lockoutPolicyDefaultsAndValidation() {
        assertEquals(5, LockoutPolicy.DEFAULT.maxFailures());
        assertEquals(Duration.ofMinutes(15), LockoutPolicy.DEFAULT.window());
        assertEquals(Duration.ofMinutes(15), LockoutPolicy.DEFAULT.lockDuration());
        assertThrows(IllegalArgumentException.class,
                () -> new LockoutPolicy(0, Duration.ofMinutes(1), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new LockoutPolicy(1, Duration.ZERO, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new LockoutPolicy(1, Duration.ofMinutes(1), Duration.ofMinutes(-1)));
    }
}
