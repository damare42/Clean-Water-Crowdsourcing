package model.persistence;

import model.Account;
import model.AccountType;
import model.Validation;
import model.WaterPurityCondition;
import model.WaterSourceCondition;
import model.WaterSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

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
            assertEquals(2, SqliteDatabase.SCHEMA_VERSION);
            assertTrue(tables(database).containsAll(List.of("accounts", "water_source_reports",
                            "water_purity_reports", "security_log")),
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

    @Test
    void existingAppDirectoryAndFileAreMadePrivate() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path home = dir.resolve("home");
        Path parent = home.resolve(".cwc");
        Files.createDirectories(parent);
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path file = Files.createFile(parent.resolve("cwc.db"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

        SqliteDatabase.open(DatabaseLocation.resolve(null, home.toString())).close();

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(parent)));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }

    @Test
    void chosenFileIsMadePrivateButItsDirectoryIsLeftAlone() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path shared = Files.createDirectories(dir.resolve("shared"));
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path file = Files.createFile(shared.resolve("data.db"));
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));

        SqliteDatabase.open(DatabaseLocation.file(file)).close();

        assertEquals("rwxr-xr-x", PosixFilePermissions.toString(Files.getPosixFilePermissions(shared)));
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
    }

    private static int insertReport(SqliteDatabase database, String type, String condition) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "INSERT INTO water_source_reports VALUES"
                            + " ((SELECT COALESCE(MAX(report_number), 0) + 1 FROM water_source_reports),"
                            + " 'alice', 0, 0, 0, ?, ?)")) {
                statement.setString(1, type);
                statement.setString(2, condition);
                return statement.executeUpdate();
            }
        });
    }

    @Test
    void schemaAcceptsEveryWaterTypeAndCondition() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            for (WaterSourceType type : WaterSourceType.values()) {
                assertEquals(1, insertReport(database, type.name(), WaterSourceCondition.Clear.name()));
            }
            for (WaterSourceCondition condition : WaterSourceCondition.values()) {
                assertEquals(1, insertReport(database, WaterSourceType.Lake.name(), condition.name()));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"Ocean, Clear", "lake, Clear", "Lake, Portable", "Lake, clear", "'', Clear"})
    void schemaRejectsUnknownWaterTypeOrCondition(String type, String condition) {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertThrows(DataAccessException.class, () -> insertReport(database, type, condition));
        }
    }

    private static int insertAccount(SqliteDatabase database, String key, String username, String type) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "INSERT INTO accounts (username_key, username, account_type, password_hash,"
                            + " first_name, last_name, email) VALUES (?, ?, ?, 'h', 'F', 'L', 'e@example.com')")) {
                statement.setString(1, key);
                statement.setString(2, username);
                statement.setString(3, type);
                return statement.executeUpdate();
            }
        });
    }

    @Test
    void schemaAcceptsEveryAccountTypeAndJavaLowerCasedKeys() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            for (AccountType type : AccountType.values()) {
                String id = "Id" + type.name();
                assertEquals(1, insertAccount(database, Validation.idKey(id), id, type.name()));
            }
            SqliteAccountRepository repository = new SqliteAccountRepository(database);
            for (String id : new String[]{"\u00c4rger", "\u0130stanbul", "\u212aelvin", "O'Brien\"</script>"}) {
                repository.add(Account.create(AccountType.User, id, "F", "L", "e@example.com"), "h");
                assertEquals(id, repository.findById(id.toLowerCase(Locale.ROOT)).orElseThrow().getId());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"Alice, Alice", "alice, bob", "'', ''", "alice, Alicia", "aLICE, alice"})
    void schemaRejectsInconsistentUsernameKeys(String key, String username) {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertThrows(DataAccessException.class,
                    () -> insertAccount(database, key, username, AccountType.User.name()));
        }
    }

    @Test
    void schemaRejectsUnknownAccountType() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertThrows(DataAccessException.class, () -> insertAccount(database, "root", "root", "Root"));
        }
    }

    /** The released version-1 schema, frozen here so the upgrade is tested against real v1 files. */
    private static final List<String> SCHEMA_V1 = List.of("""
            CREATE TABLE accounts (
                username_key  TEXT    NOT NULL PRIMARY KEY
                              CHECK (username_key <> '' AND username_key = lower(username_key)),
                username      TEXT    NOT NULL,
                account_type  TEXT    NOT NULL
                              CHECK (account_type IN ('User', 'Worker', 'Manager', 'Admin')),
                password_hash TEXT    NOT NULL,
                first_name    TEXT    NOT NULL,
                last_name     TEXT    NOT NULL,
                email         TEXT    NOT NULL,
                phone         TEXT    NOT NULL DEFAULT '',
                address       TEXT    NOT NULL DEFAULT '',
                CHECK (username_key = lower(username) OR username GLOB '*[^ -~]*')
            )""", """
            CREATE TABLE water_source_reports (
                report_number   INTEGER NOT NULL PRIMARY KEY CHECK (report_number > 0),
                reporter        TEXT    NOT NULL,
                reported_at_ms  INTEGER NOT NULL,
                latitude        REAL    NOT NULL CHECK (latitude BETWEEN -90 AND 90),
                longitude       REAL    NOT NULL CHECK (longitude BETWEEN -180 AND 180),
                water_type      TEXT    NOT NULL
                                CHECK (water_type IN ('Lake', 'River', 'Creek', 'Well',
                                                      'Stream', 'Pond', 'Oasis', 'Spout')),
                water_condition TEXT    NOT NULL
                                CHECK (water_condition IN ('Clear', 'Muddy', 'Potable', 'Waste'))
            )""",
            "INSERT INTO accounts (username_key, username, account_type, password_hash, first_name,"
                    + " last_name, email, phone, address) VALUES ('alice', 'Alice', 'Worker', 'hash-a',"
                    + " 'Alice', 'Smith', 'a@example.com', '555', 'Tech Square')",
            "INSERT INTO accounts (username_key, username, account_type, password_hash, first_name,"
                    + " last_name, email) VALUES ('root', 'root', 'Admin', 'hash-r', 'Root', 'Admin',"
                    + " 'r@example.com')",
            "INSERT INTO water_source_reports VALUES (1, 'Alice', 1476189045123, 33.77, -84.39, 'Lake', 'Potable')",
            "INSERT INTO water_source_reports VALUES (2, 'Alice', 1476189046000, -1.5, 2.25, 'Well', 'Muddy')",
            "PRAGMA user_version = 1");

    @Test
    void version1FileIsUpgradedKeepingItsData() throws Exception {
        Path file = dir.resolve("v1.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            for (String sql : SCHEMA_V1) {
                try (PreparedStatement statement = c.prepareStatement(sql)) {
                    statement.execute();
                }
            }
        }

        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.file(file))) {
            assertEquals(2, database.schemaVersion());
            assertTrue(tables(database).containsAll(List.of("water_purity_reports", "security_log")));
            SqliteAccountRepository accounts = new SqliteAccountRepository(database);
            Account alice = accounts.findById("ALICE").orElseThrow();
            assertEquals("Alice", alice.getId());
            assertEquals(AccountType.Worker, alice.getType());
            assertEquals("Tech Square", alice.getAddress());
            assertEquals(java.util.Optional.of("hash-a"), accounts.findPasswordHash("alice"));
            assertEquals(false, accounts.isBanned("alice"), "existing accounts start unbanned");
            assertTrue(accounts.existsByType(AccountType.Admin));
            SqliteReportRepository reports = new SqliteReportRepository(database);
            assertEquals(2, reports.findAll().size());
            assertEquals(java.time.Instant.ofEpochMilli(1476189045123L),
                    reports.findByNumber(1).orElseThrow().getReportedAt());
            assertEquals(WaterSourceCondition.Muddy, reports.findByNumber(2).orElseThrow().getCondition());
            assertEquals(3, reports.nextReportNumber());
            assertEquals(1, new SqlitePurityReportRepository(database).nextReportNumber());
            assertTrue(accounts.setBanned("alice", true));
        }
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.file(file))) {
            assertEquals(2, database.schemaVersion());
            assertTrue(new SqliteAccountRepository(database).isBanned("alice"), "reopening does not migrate again");
        }
    }

    private static int insertPurity(SqliteDatabase database, String condition, double virus, double contaminant) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "INSERT INTO water_purity_reports VALUES"
                            + " ((SELECT COALESCE(MAX(report_number), 0) + 1 FROM water_purity_reports),"
                            + " 'alice', 0, 0, 0, ?, ?, ?)")) {
                statement.setString(1, condition);
                statement.setDouble(2, virus);
                statement.setDouble(3, contaminant);
                return statement.executeUpdate();
            }
        });
    }

    @Test
    void schemaAcceptsValidPurityRows() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            for (WaterPurityCondition condition : WaterPurityCondition.values()) {
                assertEquals(1, insertPurity(database, condition.name(), 0, 1_000_000));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"safe, 0, 0", "Clean, 0, 0", "Safe, -1, 0", "Safe, 0, -0.5", "Safe, 1000000.5, 0",
            "Safe, Infinity, 0", "Safe, 0, Infinity", "Safe, NaN, 0", "Safe, -Infinity, 0"})
    void schemaRejectsInvalidPurityRows(String condition, double virus, double contaminant) {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            assertThrows(DataAccessException.class, () -> insertPurity(database, condition, virus, contaminant));
        }
    }

    @Test
    void schemaRejectsOtherBannedValues() {
        try (SqliteDatabase database = SqliteDatabase.open(DatabaseLocation.inMemory())) {
            insertAccount(database, "alice", "alice", "User");
            assertThrows(DataAccessException.class, () -> database.execute(c -> {
                try (PreparedStatement statement = c.prepareStatement(
                        "UPDATE accounts SET banned = 2 WHERE username_key = 'alice'")) {
                    return statement.executeUpdate();
                }
            }));
        }
    }

    @Test
    void symbolicLinksAreNotFollowedWhenTighteningPermissions() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path target = Files.createFile(dir.resolve("elsewhere.db"));
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));
        Path targetDir = Files.createDirectories(dir.resolve("real-dir"));
        Files.setPosixFilePermissions(targetDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        Path home = Files.createDirectories(dir.resolve("home"));
        Path appDir = Files.createSymbolicLink(home.resolve(".cwc"), targetDir);
        Files.createSymbolicLink(targetDir.resolve("cwc.db"), target);

        SqliteDatabase.open(DatabaseLocation.resolve(null, home.toString())).close();

        assertTrue(Files.isSymbolicLink(appDir));
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)));
        assertEquals("rwxr-xr-x", PosixFilePermissions.toString(Files.getPosixFilePermissions(targetDir)));
        assertTrue(Files.size(target) > 0, "the database itself still works through the link");
    }
}
