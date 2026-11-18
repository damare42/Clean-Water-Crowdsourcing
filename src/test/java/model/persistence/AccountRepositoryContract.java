package model.persistence;

import model.Account;
import model.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour every {@link AccountRepository} must have. Subclasses supply the
 * implementation under test.
 */
abstract class AccountRepositoryContract {

    protected AccountRepository repository;

    /**
     * @return a new, empty repository
     */
    protected abstract AccountRepository createRepository();

    @BeforeEach
    void setUpRepository() {
        repository = createRepository();
    }

    protected static Account account(AccountType type, String id) {
        return Account.create(type, id, "First", "Last", id.strip() + "@example.com",
                "555-0100", "1 Main St");
    }

    protected static void assertSameAccount(Account expected, Account actual) {
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getFname(), actual.getFname());
        assertEquals(expected.getLname(), actual.getLname());
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.getPhone(), actual.getPhone());
        assertEquals(expected.getAddress(), actual.getAddress());
    }

    @Test
    void emptyRepositoryFindsNothing() {
        assertTrue(repository.findById("alice").isEmpty());
        assertTrue(repository.findPasswordHash("alice").isEmpty());
        assertFalse(repository.existsById("alice"));
        assertTrue(repository.findById(null).isEmpty());
        assertFalse(repository.existsById(null));
        assertTrue(repository.findPasswordHash(null).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void addThenFindReturnsEqualAccountOfSameSubclass(AccountType type) {
        Account stored = account(type, "Alice");

        repository.add(stored, "hash-1");

        assertSameAccount(stored, repository.findById("Alice").orElseThrow());
        assertEquals(Optional.of("hash-1"), repository.findPasswordHash("Alice"));
        assertTrue(repository.existsById("Alice"));
    }

    @Test
    void lookupIgnoresCaseAndSurroundingWhitespace() {
        repository.add(account(AccountType.User, "Alice"), "hash-1");

        for (String id : new String[]{"alice", "ALICE", "aLiCe", "  Alice ", "\talice\n"}) {
            assertTrue(repository.existsById(id), id);
            assertEquals("Alice", repository.findById(id).orElseThrow().getId(), id);
            assertEquals(Optional.of("hash-1"), repository.findPasswordHash(id), id);
        }
        assertFalse(repository.existsById("alice2"));
    }

    @Test
    void addRejectsIdTakenIgnoringCaseAndKeepsOriginal() {
        repository.add(account(AccountType.User, "Alice"), "hash-1");

        assertThrows(DuplicateKeyException.class,
                () -> repository.add(account(AccountType.Manager, "ALICE"), "hash-2"));
        assertThrows(DuplicateKeyException.class,
                () -> repository.add(account(AccountType.Manager, "Alice"), "hash-2"));

        Account found = repository.findById("alice").orElseThrow();
        assertEquals(AccountType.User, found.getType());
        assertEquals("Alice", found.getId());
        assertEquals(Optional.of("hash-1"), repository.findPasswordHash("alice"));
    }

    @Test
    void accountsAreIndependent() {
        repository.add(account(AccountType.User, "alice"), "hash-a");
        repository.add(account(AccountType.Worker, "bob"), "hash-b");

        assertEquals(AccountType.User, repository.findById("alice").orElseThrow().getType());
        assertEquals(AccountType.Worker, repository.findById("bob").orElseThrow().getType());
        assertEquals(Optional.of("hash-a"), repository.findPasswordHash("alice"));
        assertEquals(Optional.of("hash-b"), repository.findPasswordHash("bob"));
    }

    @Test
    void returnedAccountsAreCopies() {
        Account stored = account(AccountType.User, "alice");
        repository.add(stored, "hash-1");

        Account first = repository.findById("alice").orElseThrow();
        first.updateProfile("Changed", "Name", "changed@example.com", "", "");
        stored.updateProfile("Other", "Name", "other@example.com", "", "");

        Account second = repository.findById("alice").orElseThrow();
        assertNotSame(first, second);
        assertEquals("First", second.getFname());
        assertEquals("alice@example.com", second.getEmail());
    }

    @Test
    void updateProfileOverwritesProfileFieldsOnly() {
        repository.add(account(AccountType.Worker, "Alice"), "hash-1");
        Account edited = Account.create(AccountType.Worker, "ALICE", "Alicia", "Smythe",
                "alicia@example.com", "555-0199", "Tech Square");

        assertTrue(repository.updateProfile(edited));

        Account found = repository.findById("alice").orElseThrow();
        assertEquals("Alice", found.getId());
        assertEquals(AccountType.Worker, found.getType());
        assertEquals("Alicia", found.getFname());
        assertEquals("Smythe", found.getLname());
        assertEquals("alicia@example.com", found.getEmail());
        assertEquals("555-0199", found.getPhone());
        assertEquals("Tech Square", found.getAddress());
        assertEquals(Optional.of("hash-1"), repository.findPasswordHash("alice"));
    }

    @Test
    void updateProfileOfUnknownAccountReturnsFalse() {
        assertFalse(repository.updateProfile(account(AccountType.User, "ghost")));
        assertFalse(repository.existsById("ghost"));
    }

    @Test
    void updatePasswordHashReplacesHashOnly() {
        repository.add(account(AccountType.User, "Alice"), "hash-1");

        assertTrue(repository.updatePasswordHash("ALICE", "hash-2"));

        assertEquals(Optional.of("hash-2"), repository.findPasswordHash("alice"));
        assertEquals("First", repository.findById("alice").orElseThrow().getFname());
        assertFalse(repository.updatePasswordHash("ghost", "hash-3"));
        assertFalse(repository.updatePasswordHash(null, "hash-3"));
        assertFalse(repository.existsById("ghost"));
    }

    @Test
    void valuesWithSqlMetacharactersAreStoredVerbatim() {
        String tricky = "'); DROP TABLE accounts; --";
        Account stored = Account.create(AccountType.User, "o'brien", "Pat", "Obrien",
                "pat@example.com", tricky, "Line 1\nLine 2 \"quoted\"");

        repository.add(stored, "hash'with\"quotes");

        assertSameAccount(stored, repository.findById("O'BRIEN").orElseThrow());
        assertEquals(Optional.of("hash'with\"quotes"), repository.findPasswordHash("o'brien"));
        assertTrue(repository.findById("x' OR '1'='1").isEmpty());
    }
}
