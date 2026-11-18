package model.service;

import model.Account;
import model.AccountSummary;
import model.AccountType;
import model.Permission;
import model.WaterSourceCondition;
import model.WaterSourceType;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemoryReportRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.security.SecurityEvent;
import model.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import testutil.FailingSecurityLogRepository;
import testutil.MutableClock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ban, unban, delete and list accounts as an administrator ({@link AccountService}).
 */
class AccountAdministrationTest {

    private InMemoryAccountRepository accounts;
    private InMemorySecurityLogRepository events;
    private SecurityLog log;
    private AccountService service;
    private Account root;
    private Account alice;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        events = new InMemorySecurityLogRepository();
        log = new SecurityLog(events, new MutableClock(Instant.parse("2026-03-01T10:00:00Z")));
        service = new AccountService(accounts, new AccessControl(log), log);
        root = Account.create(AccountType.Admin, "Root", "Root", "Admin", "r@example.com");
        alice = Account.create(AccountType.Worker, "Alice", "Alice", "Smith", "a@example.com");
        accounts.add(root, "h");
        accounts.add(alice, "h");
    }

    private SecurityEvent last() {
        return events.findNewestFirst(1).get(0);
    }

    @Test
    void adminListsAccounts() {
        List<AccountSummary> list = service.listAccounts(root);
        assertEquals(List.of("Alice", "Root"), list.stream().map(AccountSummary::id).toList());
    }

    @Test
    void banAndUnbanAreStoredAndLogged() {
        service.setBanned(root, "alice", true);
        assertTrue(accounts.isBanned("alice"));
        assertEquals(SecurityEventType.ACCOUNT_BANNED, last().type());
        assertEquals("Root", last().actor());
        assertEquals("Alice", last().subject(), "the log names the account as stored");

        service.setBanned(root, "ALICE", false);
        assertFalse(accounts.isBanned("alice"));
        assertEquals(SecurityEventType.ACCOUNT_UNBANNED, last().type());
    }

    @Test
    void adminCannotBanOrDeleteThemselves() {
        IllegalArgumentException ban = assertThrows(IllegalArgumentException.class,
                () -> service.setBanned(root, " root ", true));
        assertEquals(AccountService.MSG_CANNOT_BAN_SELF, ban.getMessage());
        IllegalArgumentException delete = assertThrows(IllegalArgumentException.class,
                () -> service.deleteAccount(root, "ROOT"));
        assertEquals(AccountService.MSG_CANNOT_DELETE_SELF, delete.getMessage());
        assertFalse(accounts.isBanned("root"));
        assertTrue(accounts.existsById("root"));
    }

    @Test
    void adminMayBanAnotherAdmin() {
        Account other = Account.create(AccountType.Admin, "other", "Other", "Admin", "o@example.com");
        accounts.add(other, "h");
        service.setBanned(root, "other", true);
        assertTrue(accounts.isBanned("other"));
    }

    @Test
    void unknownTargetsAreReported() {
        assertEquals(AccountService.MSG_NO_SUCH_ACCOUNT, assertThrows(IllegalArgumentException.class,
                () -> service.setBanned(root, "ghost", true)).getMessage());
        assertEquals(AccountService.MSG_NO_SUCH_ACCOUNT, assertThrows(IllegalArgumentException.class,
                () -> service.deleteAccount(root, "ghost")).getMessage());
        assertEquals(AccountService.MSG_NO_SUCH_ACCOUNT, assertThrows(IllegalArgumentException.class,
                () -> service.deleteAccount(root, null)).getMessage());
    }

    @Test
    void deleteRemovesTheAccountButKeepsItsReports() {
        InMemoryReportRepository reportRepository = new InMemoryReportRepository();
        ReportService reports = new ReportService(reportRepository,
                new MutableClock(Instant.parse("2026-03-01T10:00:00Z")), new AccessControl(log));
        reports.submit(alice, 1, 2, WaterSourceType.Lake, WaterSourceCondition.Clear);

        service.deleteAccount(root, "alice");

        assertFalse(accounts.existsById("alice"));
        assertEquals(SecurityEventType.ACCOUNT_DELETED, last().type());
        assertEquals("Alice", last().subject());
        assertEquals("Worker", last().detail());
        assertEquals("Alice", reports.listReports(root).get(0).getReporter(), "report keeps the reporter id");
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"User", "Worker", "Manager"})
    void otherAccountTypesAreDeniedAndTheDenialIsLogged(AccountType type) {
        Account actor = Account.create(type, "someone", "Some", "One", "s@example.com");

        assertThrows(PermissionDeniedException.class, () -> service.listAccounts(actor));
        assertEquals(SecurityEventType.PERMISSION_DENIED, last().type());
        assertEquals("someone", last().actor());
        assertTrue(last().detail().contains(Permission.MANAGE_USERS.name()), last().detail());
        assertThrows(PermissionDeniedException.class, () -> service.setBanned(actor, "alice", true));
        assertThrows(PermissionDeniedException.class, () -> service.deleteAccount(actor, "alice"));
        assertFalse(accounts.isBanned("alice"));
        assertTrue(accounts.existsById("alice"));
        assertEquals(3, events.countAfter(SecurityEventType.PERMISSION_DENIED, "", Instant.EPOCH));
    }

    /**
     * @return a service whose security log cannot be written
     */
    private AccountService serviceWithFailingLog(FailingSecurityLogRepository failing) {
        SecurityLog broken = new SecurityLog(failing,
                new MutableClock(Instant.parse("2026-03-01T10:00:00Z")));
        return new AccountService(accounts, new AccessControl(broken), broken);
    }

    @Test
    void banStandsWhenTheLogCannotBeWrittenAndGoesToTheApplicationLog() {
        FailingSecurityLogRepository failing = new FailingSecurityLogRepository();
        AccountService broken = serviceWithFailingLog(failing);
        Logger logger = Logger.getLogger(SecurityLog.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            broken.setBanned(root, "alice", true);
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(accounts.isBanned("alice"));
        assertEquals(1, failing.appendAttempts());
        LogRecord severe = records.stream().filter(r -> r.getLevel() == Level.SEVERE)
                .findFirst().orElseThrow();
        assertTrue(severe.getMessage().contains("ACCOUNT_BANNED"), severe.getMessage());
        assertTrue(severe.getMessage().contains("actor=Root"), severe.getMessage());
        assertTrue(severe.getMessage().contains("subject=Alice"), severe.getMessage());
        assertEquals(FailingSecurityLogRepository.MESSAGE, severe.getThrown().getMessage());
    }

    @Test
    void unbanStandsWhenTheLogCannotBeWritten() {
        accounts.setBanned("alice", true);
        FailingSecurityLogRepository failing = new FailingSecurityLogRepository();

        serviceWithFailingLog(failing).setBanned(root, "alice", false);

        assertFalse(accounts.isBanned("alice"));
        assertEquals(1, failing.appendAttempts());
    }

    @Test
    void deleteStandsWhenTheLogCannotBeWritten() {
        FailingSecurityLogRepository failing = new FailingSecurityLogRepository();

        serviceWithFailingLog(failing).deleteAccount(root, "alice");

        assertFalse(accounts.existsById("alice"));
        assertEquals(1, failing.appendAttempts());
    }

    @Test
    void unknownTargetsAreReportedAndNothingIsLoggedWhenTheLogCannotBeWritten() {
        FailingSecurityLogRepository failing = new FailingSecurityLogRepository();
        AccountService broken = serviceWithFailingLog(failing);

        assertEquals(AccountService.MSG_NO_SUCH_ACCOUNT, assertThrows(IllegalArgumentException.class,
                () -> broken.setBanned(root, "ghost", true)).getMessage());
        assertEquals(AccountService.MSG_NO_SUCH_ACCOUNT, assertThrows(IllegalArgumentException.class,
                () -> broken.deleteAccount(root, "ghost")).getMessage());
        assertEquals(0, failing.appendAttempts());
    }

    @Test
    void unknownTargetsLogNothing() {
        assertThrows(IllegalArgumentException.class, () -> service.setBanned(root, "ghost", false));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount(root, "ghost"));
        assertTrue(events.findNewestFirst(10).isEmpty());
    }

    @Test
    void anonymousIsDenied() {
        assertThrows(PermissionDeniedException.class, () -> service.listAccounts(null));
        assertEquals("not logged in lacks MANAGE_USERS", last().detail());
    }
}
