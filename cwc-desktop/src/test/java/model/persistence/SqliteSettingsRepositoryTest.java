package model.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSettingsRepositoryTest extends SettingsRepositoryContract {

    @TempDir
    Path dir;

    private SqliteDatabase database;

    @Override
    protected SettingsRepository createRepository() {
        database = SqliteDatabase.open(DatabaseLocation.file(dir.resolve("settings.db")));
        return new SqliteSettingsRepository(database);
    }

    @AfterEach
    void closeDatabase() {
        database.close();
    }

    @Test
    void settingsSurviveReopening() {
        repository.put("location.mode", "TIME_ZONE");
        database.close();

        database = SqliteDatabase.open(DatabaseLocation.file(dir.resolve("settings.db")));
        assertEquals(Optional.of("TIME_ZONE"), new SqliteSettingsRepository(database).get("location.mode"));
    }

    @Test
    void schemaRejectsEmptyKeys() {
        assertThrows(DataAccessException.class, () -> database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "INSERT INTO app_settings (key, value) VALUES ('', 'x')")) {
                return statement.executeUpdate();
            }
        }));
    }

    /** A version-2 file (as released) with one account, upgraded to version 3. */
    @Test
    void version2FileIsUpgradedKeepingItsData() throws Exception {
        database.close();
        Path file = dir.resolve("v2.db");
        // Build a real v2 file with the app itself, then mark it as version 2 again after
        // dropping the v3 table, which is exactly what a v2 release left behind.
        try (SqliteDatabase v2 = SqliteDatabase.open(DatabaseLocation.file(file))) {
            new SqliteAccountRepository(v2);
            v2.execute(c -> {
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO accounts (username_key, username, account_type, password_hash, first_name,"
                                + " last_name, email) VALUES ('alice', 'Alice', 'Worker', 'hash-a', 'Alice',"
                                + " 'Smith', 'a@example.com')");
                     PreparedStatement drop = c.prepareStatement("DROP TABLE app_settings");
                     PreparedStatement version = c.prepareStatement("PRAGMA user_version = 2")) {
                    insert.executeUpdate();
                    drop.executeUpdate();
                    version.execute();
                    return null;
                }
            });
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
             PreparedStatement statement = c.prepareStatement(
                     "SELECT count(*) FROM sqlite_master WHERE name = 'app_settings'");
             ResultSet rs = statement.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "the test file is a version-2 file");
        }

        database = SqliteDatabase.open(DatabaseLocation.file(file));
        assertEquals(3, database.schemaVersion());
        assertEquals("Alice", new SqliteAccountRepository(database).findById("alice").orElseThrow().getId());
        SqliteSettingsRepository settings = new SqliteSettingsRepository(database);
        assertEquals(Optional.empty(), settings.get("location.mode"));
        settings.put("location.mode", "OFF");
        assertEquals(Optional.of("OFF"), settings.get("location.mode"));
    }
}
