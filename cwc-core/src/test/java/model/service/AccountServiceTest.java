package model.service;

import model.Account;
import model.AccountType;
import model.Permission;
import model.Validation;
import model.persistence.DataAccessException;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemorySecurityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountServiceTest {

    private InMemoryAccountRepository accounts;
    private AccountService service;
    private Account alice;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        SecurityLog log = new SecurityLog(new InMemorySecurityLogRepository(), Clock.systemUTC());
        service = new AccountService(accounts, new AccessControl(log), log);
        alice = Account.create(AccountType.Worker, "Alice", "Alice", "Smith", "alice@example.com");
        accounts.add(alice, "hash");
    }

    @Test
    void updateProfileSavesAndUpdatesObject() {
        service.updateProfile(alice, alice, "Alicia", "Smythe", "alicia@example.com", "555", "Tech Square");

        assertEquals("Alicia", alice.getFname());
        assertEquals("555", alice.getPhone());
        Account stored = accounts.findById("alice").orElseThrow();
        assertEquals("Alicia", stored.getFname());
        assertEquals("Smythe", stored.getLname());
        assertEquals("alicia@example.com", stored.getEmail());
        assertEquals("555", stored.getPhone());
        assertEquals("Tech Square", stored.getAddress());
        assertEquals(AccountType.Worker, stored.getType());
    }

    @Test
    void invalidProfileSavesAndChangesNothing() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile(alice, alice, "Alicia", "Smythe", "bad", "555", "x"));

        assertEquals(Validation.MSG_INVALID_EMAIL, e.getMessage());
        assertEquals("Alice", alice.getFname());
        assertEquals("", alice.getPhone());
        assertEquals("Alice", accounts.findById("alice").orElseThrow().getFname());
        assertEquals("", accounts.findById("alice").orElseThrow().getPhone());
    }

    @Test
    void updatingUnstoredAccountFailsAndChangesNothing() {
        Account ghost = Account.create(AccountType.User, "ghost", "Gus", "Ghost", "g@example.com");

        assertThrows(DataAccessException.class,
                () -> service.updateProfile(ghost, ghost, "Gary", "Ghost", "g@example.com", "", ""));
        assertEquals("Gus", ghost.getFname());
    }

    @Test
    void sameAccountInAnotherCaseOrObjectCountsAsSelf() {
        Account aliceAgain = Account.create(AccountType.Worker, "ALICE", "Alice", "Smith",
                "alice@example.com");

        service.updateProfile(aliceAgain, alice, "Alicia", "Smith", "alice@example.com", "", "");

        assertEquals("Alicia", accounts.findById("alice").orElseThrow().getFname());
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"User", "Worker", "Manager"})
    void accountsWithoutManageUsersCannotEditOthers(AccountType type) {
        Account other = Account.create(type, "mallory", "Mal", "Lory", "m@example.com");
        accounts.add(other, "hash");

        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> service.updateProfile(other, alice, "Hacked", "Smith", "alice@example.com", "", ""));

        assertEquals(Permission.MANAGE_USERS, e.getPermission());
        assertEquals("Alice", alice.getFname());
        assertEquals("Alice", accounts.findById("alice").orElseThrow().getFname());
    }

    @Test
    void adminCanEditOthers() {
        Account admin = Account.create(AccountType.Admin, "root", "Ada", "Admin", "root@example.com");

        service.updateProfile(admin, alice, "Alicia", "Smith", "alice@example.com", "", "");

        assertEquals("Alicia", alice.getFname());
        assertEquals("Alicia", accounts.findById("alice").orElseThrow().getFname());
    }

    @Test
    void nobodyLoggedInIsDenied() {
        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> service.updateProfile(null, alice, "Alicia", "Smith", "alice@example.com", "", ""));

        assertNull(e.getAccountType());
        assertEquals("Alice", accounts.findById("alice").orElseThrow().getFname());
    }
}
