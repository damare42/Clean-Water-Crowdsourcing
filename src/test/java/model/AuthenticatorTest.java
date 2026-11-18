package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticatorTest {

    @BeforeEach
    void clearStaticState() {
        Authenticator.ACCOUNTS.clear();
        Account.accountList.clear();
    }

    private static Account account(String id) {
        return new Account(id, "First", "Last", id + "@example.com", AccountType.User);
    }

    @Test
    void addAccountStoresPasswordAndAccountKeyedById() {
        Account alice = account("alice");

        Authenticator.addAccount(alice, "secret");

        assertEquals("secret", Authenticator.ACCOUNTS.get("alice"));
        assertSame(alice, Account.accountList.get("alice"));
        assertEquals(1, Authenticator.ACCOUNTS.size());
        assertEquals(1, Account.accountList.size());
    }

    @Test
    void addAccountWithTakenIdIsRejectedAndKeepsOriginal() {
        Account first = account("alice");
        Authenticator.addAccount(first, "one");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Authenticator.addAccount(account("alice"), "two"));

        assertEquals(Validation.MSG_ID_TAKEN, e.getMessage());
        assertEquals("one", Authenticator.ACCOUNTS.get("alice"));
        assertSame(first, Account.accountList.get("alice"));
    }

    @Test
    void validatePasswordAcceptsMatchingPassword() {
        Authenticator.addAccount(account("alice"), "secret");

        assertTrue(Authenticator.validatePassword("alice", "secret"));
    }

    @Test
    void validatePasswordRejectsWrongPassword() {
        Authenticator.addAccount(account("alice"), "secret");

        assertFalse(Authenticator.validatePassword("alice", "wrong"));
        assertFalse(Authenticator.validatePassword("alice", "SECRET"));
        assertFalse(Authenticator.validatePassword("alice", ""));
        assertFalse(Authenticator.validatePassword("alice", null));
    }

    @Test
    void validatePasswordRejectsUnknownUser() {
        assertFalse(Authenticator.validatePassword("nobody", "secret"));
        assertFalse(Authenticator.validatePassword(null, null));
    }

    @Test
    void validatePasswordIsCaseSensitiveOnId() {
        Authenticator.addAccount(account("alice"), "secret");

        assertFalse(Authenticator.validatePassword("Alice", "secret"));
    }

    @Test
    void isIdAvailableWhenIdIsFree() {
        assertTrue(Authenticator.isIdAvailable("alice"));
    }

    @Test
    void isIdAvailableIsFalseForRegisteredId() {
        Authenticator.addAccount(account("alice"), "secret");

        assertFalse(Authenticator.isIdAvailable("alice"));
        assertTrue(Authenticator.isIdAvailable("bob"));
    }

    @Test
    void isIdAvailableChecksBothMaps() {
        Authenticator.ACCOUNTS.put("onlyPassword", "pw");
        Account.accountList.put("onlyAccount", account("onlyAccount"));

        assertFalse(Authenticator.isIdAvailable("onlyPassword"));
        assertFalse(Authenticator.isIdAvailable("onlyAccount"));
    }

    @Test
    void isIdAvailableIsFalseForNull() {
        assertFalse(Authenticator.isIdAvailable(null));
    }

    @Test
    void registerCreatesAndStoresSubclassForType() {
        Account account = Authenticator.register("worker1", AccountType.Worker, "Wendy", "Ward",
                "wendy@example.com", "wendy@example.com", "secret1", "secret1");

        assertEquals(Worker.class, account.getClass());
        assertEquals(AccountType.Worker, account.getType());
        assertSame(account, Account.find("worker1").orElseThrow());
        assertTrue(Authenticator.validatePassword("worker1", "secret1"));
    }

    @Test
    void registerWithInvalidFormStoresNothing() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Authenticator.register("worker1", AccountType.Worker, "Wendy", "Ward",
                        "wendy@example.com", "wendy@example.com", "short", "short"));

        assertEquals(Validation.MSG_INVALID_PASSWORD, e.getMessage());
        assertTrue(Account.accountList.isEmpty());
        assertTrue(Authenticator.ACCOUNTS.isEmpty());
    }

    @Test
    void registerWithTakenIdIsRejected() {
        Authenticator.register("alice", AccountType.User, "Alice", "Smith",
                "alice@example.com", "alice@example.com", "secret1", "secret1");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Authenticator.register("alice", AccountType.Manager, "Mallory", "Jones",
                        "m@example.com", "m@example.com", "other12", "other12"));

        assertEquals(Validation.MSG_ID_TAKEN, e.getMessage());
        assertEquals(AccountType.User, Account.find("alice").orElseThrow().getType());
        assertTrue(Authenticator.validatePassword("alice", "secret1"));
    }
}
