package model.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SqliteDatabaseTest {

    @TempDir
    Path dir;

    private static List<String> tables(SqliteDatabase database) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name");
                 ResultSet rs = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
                return names;
            }
        });
    }

    @Test
    void openCreatesParentDirectoriesFileAndSchema() {
        Path file = dir.resolve("a/b/cwc.db");

        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.file(file))) {
            assertTrue(Files.isRegularFile(file));
            assertEquals(SqliteDatabase.SCHEMA_VERSION, database.schemaVersion());
            assertEquals(1, SqliteDatabase.SCHEMA_VERSION);
            assertTrue(tables(database).containsAll(List.of("accounts", "water_source_reports")),
                    tables(database).toString());
            assertEquals(file.toAbsolutePath().normalize().toString(), database.getLocation().toString());
        }
    }

    @Test
    void reopeningExistingDatabaseKeepsSchemaVersion() {
        Path file = dir.resolve("cwc.db");
        SqliteDatabase.open(DatabaseLocation.file(file)).close();

        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.file(file))) {
            assertEquals(SqliteDatabase.SCHEMA_VERSION, database.schemaVersion());
        }
    }

    @Test
    void databaseFromNewerAppIsRejected() throws Exception {
        Path file = dir.resolve("cwc.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
             PreparedStatement statement = c.prepareStatement("PRAGMA user_version = 99")) {
            statement.execute();
        }

        DataAccessException e = assertThrows(DataAccessException.class,
                () -> SqliteDatabase.open(DatabaseLocation.file(file)));
        assertTrue(e.getMessage().contains("99"), e.getMessage());
    }

    @Test
    void fileThatIsNotADatabaseIsRejected() throws Exception {
        Path file = dir.resolve("cwc.db");
        Files.writeString(file, "this is not a database, just some text that is long enough");

        assertThrows(DataAccessException.class, () -> SqliteDatabase.open(DatabaseLocation.file(file)));
    }

    @Test
    void newFilesAreOnlyAccessibleByTheOwner() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path parent = dir.resolve("home/.cwc");
        Path file = parent.resolve("cwc.db");

        SqliteDatabase.open(DatabaseLocation.file(file)).close();

        assertTrue(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                .containsAll(Files.getPosixFilePermissions(file)), Files.getPosixFilePermissions(file).toString());
        assertTrue(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE).containsAll(Files.getPosixFilePermissions(parent)),
                Files.getPosixFilePermissions(parent).toString());
    }

    @Test
    void inMemoryDatabaseHasSchemaAndLeavesNoFile() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertEquals(SqliteDatabase.SCHEMA_VERSION, database.schemaVersion());
            assertTrue(tables(database).contains("accounts"));
        }
    }

    @Test
    void failedTransactionIsRolledBack() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertThrows(IllegalStateException.class, () -> database.inTransaction(c -> {
                try (PreparedStatement statement = c.prepareStatement(
                        "INSERT INTO water_source_reports VALUES (1, 'alice', 0, 0, 0, 'Lake', 'Clear')")) {
                    statement.executeUpdate();
                }
                throw new IllegalStateException("boom");
            }));

            assertEquals(1, new SqliteReportRepository(database).nextReportNumber());
            // the connection is back in auto-commit mode and still usable
            assertEquals(Boolean.TRUE, database.execute(Connection::getAutoCommit));
        }
    }

    @Test
    void schemaRejectsOutOfRangeCoordinates() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertThrows(DataAccessException.class, () -> database.execute(c -> {
                try (PreparedStatement statement = c.prepareStatement(
                        "INSERT INTO water_source_reports VALUES (1, 'alice', 0, 91, 0, 'Lake', 'Clear')")) {
                    return statement.executeUpdate();
                }
            }));
        }
    }

    @Test
    void closingTwiceIsHarmlessAndClosedDatabaseFails() {
        SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory());
        database.close();

        assertDoesNotThrow(database::close);
        assertThrows(DataAccessException.class, database::schemaVersion);
    }
}
