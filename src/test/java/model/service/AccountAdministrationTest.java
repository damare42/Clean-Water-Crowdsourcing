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
import testutil.MutableClock;

import java.time.Instant;
import java.util.List;

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

    @Test
    void anonymousIsDenied() {
        assertThrows(PermissionDeniedException.class, () -> service.listAccounts(null));
        assertEquals("not logged in lacks MANAGE_USERS", last().detail());
    }
}
