package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation tests for the current behaviour of {@link Authenticator}.
 */
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
    void addAccountWithSameIdOverwritesPreviousEntry() {
        Account first = account("alice");
        Account second = account("alice");

        Authenticator.addAccount(first, "one");
        Authenticator.addAccount(second, "two");

        assertEquals("two", Authenticator.ACCOUNTS.get("alice"));
        assertSame(second, Account.accountList.get("alice"));
        assertEquals(1, Account.accountList.size());
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
    void validateIdReturnsTrueWhenIdIsFree() {
        assertTrue(Authenticator.validateID("alice"));
    }

    @Test
    void validateIdReturnsFalseWhenIdIsTaken() {
        Authenticator.addAccount(account("alice"), "secret");

        assertFalse(Authenticator.validateID("alice"));
        assertTrue(Authenticator.validateID("bob"));
    }

    @Test
    void validateIdOnlyLooksAtAccountListNotPasswordMap() {
        // Current behaviour: an id present only in the password map is reported as free.
        Authenticator.ACCOUNTS.put("ghost", "pw");

        assertTrue(Authenticator.validateID("ghost"));
    }

    @Test
    void validateIdTreatsIdAsTakenAfterAccountOfCreatesIt() {
        // Current behaviour: Account.of registers a placeholder account, which then blocks the id.
        Account.of("phantom");

        assertFalse(Authenticator.validateID("phantom"));
    }
}
