package model.service;

import model.Account;
import model.AccountType;
import model.Admin;
import model.Validation;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.security.LockoutPolicy;
import model.security.PasswordHasher;
import model.security.SecurityEvent;
import model.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testutil.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bans, lockout, security log entries and the administrator bootstrap of {@link AuthService}.
 */
class AuthSecurityTest {

    private static final Instant T0 = Instant.parse("2026-03-01T10:00:00Z");

    private InMemoryAccountRepository accounts;
    private InMemorySecurityLogRepository events;
    private MutableClock clock;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        events = new InMemorySecurityLogRepository();
        clock = new MutableClock(T0);
        auth = new AuthService(accounts, new PasswordHasher(PasswordHasher.MIN_ITERATIONS),
                new SecurityLog(events, clock), LockoutPolicy.DEFAULT);
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    private Account register(String id) {
        return auth.register(id, AccountType.User, "First", "Last", "f@example.com", "f@example.com",
                pw("secret1"), pw("secret1"));
    }

    private List<SecurityEvent> log() {
        List<SecurityEvent> oldestFirst = new ArrayList<>(events.findNewestFirst(1000));
        Collections.reverse(oldestFirst);
        return oldestFirst;
    }

    private SecurityEvent last() {
        return events.findNewestFirst(1).get(0);
    }

    @Test
    void successfulLoginIsLoggedUnderTheNormalizedId() {
        register("Alice");

        LoginResult result = auth.authenticate(" ALICE ", pw("secret1"));

        assertTrue(result.isSuccess());
        assertEquals("Alice", result.getAccount().orElseThrow().getId());
        assertEquals("", result.message(T0));
        SecurityEvent event = last();
        assertEquals(SecurityEventType.LOGIN_SUCCESS, event.type());
        assertEquals("Alice", event.actor());
        assertEquals("alice", event.subject());
        assertEquals(T0, event.occurredAt());
    }

    @Test
    void failedLoginsAreLoggedWithoutPasswords() {
        register("alice");

        assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("alice", pw("wrong-pass-77")).getStatus());
        assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("Ghost", pw("other-pass-88")).getStatus());

        List<SecurityEvent> failures = log().stream()
                .filter(e -> e.type() == SecurityEventType.LOGIN_FAILURE).toList();
        assertEquals(2, failures.size());
        assertEquals("alice", failures.get(0).subject());
        assertEquals("wrong password", failures.get(0).detail());
        assertEquals("ghost", failures.get(1).subject());
        assertEquals("unknown id", failures.get(1).detail());
        for (SecurityEvent e : log()) {
            String text = e.actor() + e.subject() + e.detail();
            assertFalse(text.contains("secret1") || text.contains("wrong-pass") || text.contains("other-pass"), text);
        }
        assertEquals(LoginResult.MSG_INVALID, auth.authenticate("alice", pw("nope123")).message(T0));
    }

    @Test
    void registrationIsLogged() {
        register("Alice");

        SecurityEvent event = last();
        assertEquals(SecurityEventType.REGISTRATION, event.type());
        assertEquals("Alice", event.subject());
        assertEquals("User", event.detail());
    }

    @Test
    void bannedAccountIsOnlyToldAboutTheBanAfterTheCorrectPassword() {
        register("alice");
        accounts.setBanned("alice", true);

        LoginResult wrong = auth.authenticate("alice", pw("wrong12"));
        assertEquals(LoginResult.Status.INVALID_CREDENTIALS, wrong.getStatus(),
                "a wrong password must not reveal the ban");
        assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("ghost", pw("wrong12")).getStatus());

        LoginResult right = auth.authenticate("alice", pw("secret1"));
        assertEquals(LoginResult.Status.BANNED, right.getStatus());
        assertTrue(right.getAccount().isEmpty());
        assertEquals(LoginResult.MSG_BANNED, right.message(T0));
        assertEquals("account is banned", last().detail());
        assertTrue(auth.login("alice", pw("secret1")).isEmpty());

        accounts.setBanned("alice", false);
        assertTrue(auth.authenticate("alice", pw("secret1")).isSuccess());
    }

    @Test
    void fiveFailuresWithinFifteenMinutesLockTheIdForFifteenMinutes() {
        register("alice");
        for (int i = 0; i < 4; i++) {
            assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("alice", pw("wrong12")).getStatus());
            clock.advance(Duration.ofMinutes(3));
        }
        LoginResult fifth = auth.authenticate("ALICE", pw("wrong12"));
        assertEquals(LoginResult.Status.LOCKED, fifth.getStatus());
        Instant lockedAt = clock.instant();
        assertEquals(lockedAt.plus(Duration.ofMinutes(15)), fifth.getLockedUntil().orElseThrow());
        assertEquals("Too many failed log-ins. Please try again in 15 minutes.", fifth.message(lockedAt));
        assertEquals(SecurityEventType.LOGIN_LOCKED, last().type());
        assertEquals("alice", last().subject());

        clock.advance(Duration.ofMinutes(14));
        LoginResult stillLocked = auth.authenticate("alice", pw("secret1"));
        assertEquals(LoginResult.Status.LOCKED, stillLocked.getStatus(), "even the right password is refused");
        assertEquals("Too many failed log-ins. Please try again in 1 minute.", stillLocked.message(clock.instant()));
        assertEquals("id is locked", last().detail());

        clock.advance(Duration.ofMinutes(1));
        assertTrue(auth.authenticate("alice", pw("secret1")).isSuccess(), "lock ends after 15 minutes");
    }

    @Test
    void failuresOutsideTheWindowDoNotCount() {
        register("alice");
        for (int i = 0; i < 4; i++) {
            auth.authenticate("alice", pw("wrong12"));
        }
        clock.advance(Duration.ofMinutes(15));
        assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("alice", pw("wrong12")).getStatus(),
                "the first four are exactly 15 minutes old and no longer count");
    }

    @Test
    void successResetsTheFailureCount() {
        register("alice");
        for (int i = 0; i < 4; i++) {
            auth.authenticate("alice", pw("wrong12"));
        }
        clock.advance(Duration.ofSeconds(1));
        assertTrue(auth.authenticate("alice", pw("secret1")).isSuccess());
        clock.advance(Duration.ofSeconds(1));
        for (int i = 0; i < 4; i++) {
            assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("alice", pw("wrong12")).getStatus());
        }
    }

    @Test
    void attemptsDuringALockDoNotExtendIt() {
        register("alice");
        for (int i = 0; i < 5; i++) {
            auth.authenticate("alice", pw("wrong12"));
        }
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofMinutes(1));
            auth.authenticate("alice", pw("wrong12"));
        }
        clock.advance(Duration.ofMinutes(5));
        assertTrue(auth.authenticate("alice", pw("secret1")).isSuccess());
    }

    @Test
    void afterALockFiveNewFailuresLockAgain() {
        register("alice");
        for (int i = 0; i < 5; i++) {
            auth.authenticate("alice", pw("wrong12"));
        }
        clock.advance(Duration.ofMinutes(15));
        for (int i = 0; i < 4; i++) {
            assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("alice", pw("wrong12")).getStatus());
        }
        assertEquals(LoginResult.Status.LOCKED, auth.authenticate("alice", pw("wrong12")).getStatus());
    }

    @Test
    void unknownIdsLockLikeRealOnesSoLocksRevealNothing() {
        for (int i = 0; i < 4; i++) {
            assertEquals(LoginResult.Status.INVALID_CREDENTIALS, auth.authenticate("ghost", pw("wrong12")).getStatus());
        }
        assertEquals(LoginResult.Status.LOCKED, auth.authenticate("Ghost", pw("wrong12")).getStatus());
        register("bob");
        assertTrue(auth.authenticate("bob", pw("secret1")).isSuccess(), "locks are per id");
    }

    @Test
    void customPolicy() {
        AuthService strict = new AuthService(accounts, new PasswordHasher(PasswordHasher.MIN_ITERATIONS),
                new SecurityLog(events, clock), new LockoutPolicy(2, Duration.ofMinutes(1), Duration.ofSeconds(30)));
        register("alice");
        strict.authenticate("alice", pw("wrong12"));
        assertEquals(LoginResult.Status.LOCKED, strict.authenticate("alice", pw("wrong12")).getStatus());
        clock.advance(Duration.ofSeconds(30));
        assertTrue(strict.authenticate("alice", pw("secret1")).isSuccess());
    }

    @Test
    void lockSurvivesARestartBecauseItIsInTheLog() {
        register("alice");
        for (int i = 0; i < 5; i++) {
            auth.authenticate("alice", pw("wrong12"));
        }
        AuthService restarted = new AuthService(accounts, new PasswordHasher(PasswordHasher.MIN_ITERATIONS),
                new SecurityLog(events, clock), LockoutPolicy.DEFAULT);
        assertEquals(LoginResult.Status.LOCKED, restarted.authenticate("alice", pw("secret1")).getStatus());
    }

    @Test
    void lockedMessageRoundsUpToWholeMinutes() {
        assertEquals("Too many failed log-ins. Please try again in 1 minute.",
                LoginResult.lockedMessage(Duration.ofSeconds(1)));
        assertEquals("Too many failed log-ins. Please try again in 1 minute.",
                LoginResult.lockedMessage(Duration.ZERO));
        assertEquals("Too many failed log-ins. Please try again in 2 minutes.",
                LoginResult.lockedMessage(Duration.ofSeconds(61)));
    }

    @Test
    void firstAdministratorCanBeCreatedOnceAndLogsIn() {
        assertFalse(auth.hasAdministrator());
        register("alice");

        Account admin = auth.createFirstAdministrator(" Root ", "Root", "Admin", "r@example.com",
                "r@example.com", pw("admin123"), pw("admin123"));

        assertEquals(Admin.class, admin.getClass());
        assertEquals("Root", admin.getId());
        assertTrue(auth.hasAdministrator());
        assertEquals(SecurityEventType.ADMIN_CREATED, last().type());
        assertEquals("Root", last().subject());
        assertEquals(AccountType.Admin, auth.login("root", pw("admin123")).orElseThrow().getType());

        IllegalArgumentException again = assertThrows(IllegalArgumentException.class,
                () -> auth.createFirstAdministrator("root2", "Root", "Two", "r@example.com", "r@example.com",
                        pw("admin123"), pw("admin123")));
        assertEquals(AuthService.MSG_ADMIN_EXISTS, again.getMessage());
        assertFalse(accounts.existsById("root2"));
    }

    @Test
    void firstAdministratorFormIsValidated() {
        register("alice");

        IllegalArgumentException taken = assertThrows(IllegalArgumentException.class,
                () -> auth.createFirstAdministrator("ALICE", "Root", "Admin", "r@example.com", "r@example.com",
                        pw("admin123"), pw("admin123")));
        assertEquals(Validation.MSG_ID_TAKEN, taken.getMessage());
        IllegalArgumentException weak = assertThrows(IllegalArgumentException.class,
                () -> auth.createFirstAdministrator("root", "Root", "Admin", "r@example.com", "r@example.com",
                        pw("short"), pw("short")));
        assertEquals(Validation.MSG_INVALID_PASSWORD, weak.getMessage());
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> auth.createFirstAdministrator("root", "Root", "Admin", "r@example.com", "r@example.com",
                        pw("admin123"), pw("admin124")));
        assertEquals(Validation.MSG_PASSWORD_MISMATCH, mismatch.getMessage());
        assertFalse(auth.hasAdministrator());
    }

    @Test
    void administratorCannotBeSelfRegisteredEvenWhenNoneExists() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.register("root", AccountType.Admin, "Root", "Admin", "r@example.com", "r@example.com",
                        pw("admin123"), pw("admin123")));
        assertEquals(AuthService.MSG_ACCOUNT_TYPE_NOT_ALLOWED, e.getMessage());
        assertFalse(auth.hasAdministrator());
    }
}
