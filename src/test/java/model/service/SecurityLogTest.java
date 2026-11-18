package model.service;

import model.Account;
import model.AccountType;
import model.Permission;
import model.persistence.DataAccessException;
import model.persistence.InMemorySecurityLogRepository;
import model.persistence.SecurityLogRepository;
import model.security.SecurityEvent;
import model.security.SecurityEventType;
import org.junit.jupiter.api.Test;
import testutil.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityLogTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    private final InMemorySecurityLogRepository events = new InMemorySecurityLogRepository();
    private final MutableClock clock = new MutableClock(T0);
    private final SecurityLog log = new SecurityLog(events, clock);
    private final Account admin = Account.create(AccountType.Admin, "root", "Root", "Admin", "r@example.com");

    @Test
    void recordStampsTheClockTime() {
        log.record(SecurityEventType.REGISTRATION, "a", "a", "User");
        clock.advance(Duration.ofSeconds(5));
        SecurityEvent second = log.record(SecurityEventType.LOGIN_SUCCESS, "a", "a", null);

        assertEquals(T0.plusSeconds(5), second.occurredAt());
        assertEquals(T0.plusSeconds(5), log.now());
    }

    @Test
    void adminReadsNewestFirst() {
        log.record(SecurityEventType.REGISTRATION, "a", "a", "");
        log.record(SecurityEventType.LOGIN_SUCCESS, "a", "a", "");

        List<SecurityEvent> list = log.recentEvents(admin);

        assertEquals(List.of(SecurityEventType.LOGIN_SUCCESS, SecurityEventType.REGISTRATION),
                list.stream().map(SecurityEvent::type).toList());
    }

    @Test
    void recentEventsAreCapped() {
        for (int i = 0; i < SecurityLog.MAX_EVENTS + 5; i++) {
            log.record(SecurityEventType.LOGIN_FAILURE, "", "x", "");
        }
        assertEquals(SecurityLog.MAX_EVENTS, log.recentEvents(admin).size());
    }

    @Test
    void othersMayNotReadTheLogAndTheAttemptIsLogged() {
        Account manager = Account.create(AccountType.Manager, "boss", "Bo", "Ss", "b@example.com");

        assertThrows(PermissionDeniedException.class, () -> log.recentEvents(manager));
        assertThrows(PermissionDeniedException.class, () -> log.recentEvents(null));

        List<SecurityEvent> list = log.recentEvents(admin);
        assertEquals(2, list.size());
        assertEquals(SecurityEventType.PERMISSION_DENIED, list.get(1).type());
        assertEquals("boss", list.get(1).actor());
        assertEquals("Manager lacks MANAGE_USERS", list.get(1).detail());
    }

    @Test
    void denialIsStillRefusedWhenTheLogCannotBeWritten() {
        SecurityLogRepository broken = new SecurityLogRepository() {
            @Override
            public SecurityEvent append(Instant at, SecurityEventType type, String a, String s, String d) {
                throw new DataAccessException("disk full");
            }

            @Override
            public List<SecurityEvent> findNewestFirst(int limit) {
                return List.of();
            }

            @Override
            public Optional<Instant> findLatest(SecurityEventType type, String subject) {
                return Optional.empty();
            }

            @Override
            public int countAfter(SecurityEventType type, String subject, Instant after) {
                return 0;
            }
        };
        AccessControl access = new AccessControl(new SecurityLog(broken, clock));

        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> access.require(null, Permission.SUBMIT_PURITY_REPORT));
        assertEquals(1, e.getSuppressed().length);
        assertEquals("disk full", e.getSuppressed()[0].getMessage());
    }

    @Test
    void accessControlAllowsPermittedActionsWithoutLogging() {
        AccessControl access = new AccessControl(log);
        access.require(admin, Permission.MANAGE_USERS);
        access.requireSelfOr(admin, admin, Permission.SUBMIT_PURITY_REPORT);
        assertEquals(0, events.findNewestFirst(10).size());
    }
}
