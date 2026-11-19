package model.persistence;

import model.Account;
import model.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteAccountRepositoryTest extends AccountRepositoryContract {

    @TempDir
    Path dir;

    private SqliteDatabase database;

    private Path file() {
        return dir.resolve("accounts.db");
    }

    @Override
    protected AccountRepository createRepository() {
        database = SqliteDatabase.open(DatabaseLocation.file(file()));
        return new SqliteAccountRepository(database);
    }

    @AfterEach
    void closeDatabase() {
        database.close();
    }

    @Test
    void accountsSurviveClosingAndReopening() {
        Account alice = account(AccountType.Manager, "Alice");
        repository.add(alice, "hash-1");
        repository.updateProfile(Account.create(AccountType.Manager, "alice", "Alicia", "Smythe",
                "alicia@example.com", "555", "Tech Square"));
        database.close();

        database = SqliteDatabase.open(DatabaseLocation.file(file()));
        AccountRepository reopened = new SqliteAccountRepository(database);

        Account found = reopened.findById("ALICE").orElseThrow();
        assertEquals("Alice", found.getId());
        assertEquals(AccountType.Manager, found.getType());
        assertEquals("Alicia", found.getFname());
        assertEquals("555", found.getPhone());
        assertEquals(Optional.of("hash-1"), reopened.findPasswordHash("alice"));
        assertThrows(DuplicateKeyException.class,
                () -> reopened.add(account(AccountType.User, "alice"), "hash-2"));
    }

    @Test
    void closedDatabaseFailsWithDataAccessException() {
        database.close();

        assertThrows(DataAccessException.class, () -> repository.findById("alice"));
        assertThrows(DataAccessException.class,
                () -> repository.add(account(AccountType.User, "alice"), "hash"));
    }
}
