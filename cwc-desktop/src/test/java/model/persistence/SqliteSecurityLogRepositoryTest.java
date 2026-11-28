package model.persistence;

import model.security.SecurityEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSecurityLogRepositoryTest extends SecurityLogRepositoryContract {

    @TempDir
    Path dir;

    private SqliteDatabase database;

    @Override
    protected SecurityLogRepository createRepository() {
        database = SqliteDatabase.open(DatabaseLocation.file(dir.resolve("log.db")));
        return new SqliteSecurityLogRepository(database);
    }

    @AfterEach
    void closeDatabase() {
        database.close();
    }

    private int run(String sql) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(sql)) {
                return statement.executeUpdate();
            }
        });
    }

    @Test
    void logIsAppendOnlyInTheDatabase() {
        repository.append(T0, SecurityEventType.ACCOUNT_BANNED, "root", "bob", "");

        DataAccessException update = assertThrows(DataAccessException.class,
                () -> run("UPDATE security_log SET detail = 'tampered'"));
        assertTrue(update.getMessage().contains("append-only"), update.getMessage());
        assertThrows(DataAccessException.class, () -> run("DELETE FROM security_log"));
        assertEquals("", repository.findNewestFirst(1).get(0).detail());
    }

    @Test
    void schemaRejectsUnknownEventTypes() {
        assertThrows(DataAccessException.class, () -> run(
                "INSERT INTO security_log (occurred_at_ms, event_type) VALUES (0, 'login_success')"));
    }

    @Test
    void eventsSurviveReopening() {
        repository.append(T0, SecurityEventType.LOGIN_LOCKED, "", "alice", "5 failed");
        database.close();
        database = SqliteDatabase.open(DatabaseLocation.file(dir.resolve("log.db")));
        SqliteSecurityLogRepository reopened = new SqliteSecurityLogRepository(database);

        assertEquals("5 failed", reopened.findNewestFirst(5).get(0).detail());
        assertEquals(2, reopened.append(T0, SecurityEventType.LOGIN_SUCCESS, "", "", "").sequence());
    }
}
