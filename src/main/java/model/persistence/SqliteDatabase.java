package model.persistence;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An open SQLite database (via the xerial {@code sqlite-jdbc} driver) with the app's
 * schema. Opening creates the file, its parent directory and the schema if needed,
 * and upgrades an older schema. The schema version is kept in SQLite's
 * {@code PRAGMA user_version}.
 * <p>
 * All access goes through one connection and is serialized, which suits a desktop
 * app. Close the database when the app exits.
 */
public final class SqliteDatabase implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(SqliteDatabase.class.getName());

    /**
     * Schema migrations: entry {@code i} upgrades the schema from version {@code i}
     * to {@code i + 1}. Append new entries; never change existing ones once a
     * version has been released.
     * <p>
     * {@code username_key} is the id lower-cased by Java ({@code Locale.ROOT}, all of
     * Unicode), while SQLite's {@code lower()} only folds ASCII. So the checks are:
     * the key has no ASCII upper-case letters, and for ids made only of printable
     * ASCII the key equals {@code lower(username)}. Ids with other characters are
     * only checked by the Java code, since SQLite cannot lower-case them the same way.
     * The enum columns only accept the constant names of the Java enums
     * ({@code AccountType}, {@code WaterSourceType}, {@code WaterSourceCondition},
     * {@code WaterPurityCondition}, {@code SecurityEventType}).
     * <p>
     * Version 2 adds the {@code banned} flag of accounts, water purity reports (PPM
     * values from 0 to 1,000,000; NaN cannot be stored because SQLite turns it into
     * NULL, and infinity fails the range check) and the security log, which triggers
     * keep append-only.
     */
    private static final List<List<String>> MIGRATIONS = List.of(
            List.of("""
                    CREATE TABLE IF NOT EXISTS accounts (
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
                    CREATE TABLE IF NOT EXISTS water_source_reports (
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
                    )"""),
            List.of("""
                    ALTER TABLE accounts ADD COLUMN banned INTEGER NOT NULL DEFAULT 0
                        CHECK (banned IN (0, 1))""", """
                    CREATE TABLE water_purity_reports (
                        report_number     INTEGER NOT NULL PRIMARY KEY CHECK (report_number > 0),
                        reporter          TEXT    NOT NULL CHECK (reporter <> ''),
                        reported_at_ms    INTEGER NOT NULL,
                        latitude          REAL    NOT NULL CHECK (latitude BETWEEN -90 AND 90),
                        longitude         REAL    NOT NULL CHECK (longitude BETWEEN -180 AND 180),
                        overall_condition TEXT    NOT NULL
                                          CHECK (overall_condition IN ('Safe', 'Treatable', 'Unsafe')),
                        virus_ppm         REAL    NOT NULL CHECK (virus_ppm BETWEEN 0 AND 1000000),
                        contaminant_ppm   REAL    NOT NULL CHECK (contaminant_ppm BETWEEN 0 AND 1000000)
                    )""", """
                    CREATE INDEX water_purity_reports_by_time ON water_purity_reports (reported_at_ms)""", """
                    CREATE TABLE security_log (
                        event_id       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        occurred_at_ms INTEGER NOT NULL,
                        event_type     TEXT    NOT NULL
                                       CHECK (event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGIN_LOCKED',
                                                             'REGISTRATION', 'ADMIN_CREATED', 'ACCOUNT_BANNED',
                                                             'ACCOUNT_UNBANNED', 'ACCOUNT_DELETED',
                                                             'PERMISSION_DENIED')),
                        actor          TEXT    NOT NULL DEFAULT '',
                        subject        TEXT    NOT NULL DEFAULT '',
                        detail         TEXT    NOT NULL DEFAULT ''
                    )""", """
                    CREATE INDEX security_log_by_subject ON security_log (subject, event_type, occurred_at_ms)""", """
                    CREATE TRIGGER security_log_no_update BEFORE UPDATE ON security_log
                    BEGIN
                        SELECT RAISE(ABORT, 'the security log is append-only');
                    END""", """
                    CREATE TRIGGER security_log_no_delete BEFORE DELETE ON security_log
                    BEGIN
                        SELECT RAISE(ABORT, 'the security log is append-only');
                    END"""));

    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE =
            PosixFilePermissions.fromString("rw-------");

    /** The schema version this code creates and understands. */
    public static final int SCHEMA_VERSION = MIGRATIONS.size();

    /**
     * Work done with the connection.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final Connection connection;
    private final DatabaseLocation location;

    private SqliteDatabase(Connection connection, DatabaseLocation location) {
        this.connection = connection;
        this.location = location;
    }

    /**
     * Opens (and if needed creates) the database and brings its schema up to date.
     *
     * @param location where the database lives
     * @return the open database
     * @throws DataAccessException if the database cannot be opened, or was written by
     *         a newer version of the app
     */
    public static SqliteDatabase open(DatabaseLocation location) {
        location.getFile().ifPresent(file -> prepareFile(location, file));
        Connection connection;
        try {
            connection = DriverManager.getConnection(location.jdbcUrl());
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database " + location, e);
        }
        SqliteDatabase database = new SqliteDatabase(connection, location);
        try {
            database.configure();
            database.migrate();
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
        LOGGER.log(Level.INFO, "Opened database {0} (schema version {1})",
                new Object[]{location, SCHEMA_VERSION});
        return database;
    }

    /**
     * Creates the parent directory and an empty database file, readable only by the
     * owner where the file system supports it, so password hashes are not world-readable.
     * An existing file, and an existing default directory, are made private too if
     * the current user owns them; that is best effort and only logged if it fails.
     */
    private static void prepareFile(DatabaseLocation location, Path file) {
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                if (posix) {
                    Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(PRIVATE_DIRECTORY));
                } else {
                    Files.createDirectories(parent);
                }
            } else if (posix && parent != null && location.isAppDirectory()) {
                makePrivate(parent, PRIVATE_DIRECTORY);
            }
            if (posix && !Files.exists(file)) {
                try {
                    Files.createFile(file, PosixFilePermissions.asFileAttribute(PRIVATE_FILE));
                } catch (FileAlreadyExistsException e) {
                    // created concurrently; fine
                }
            } else if (posix) {
                makePrivate(file, PRIVATE_FILE);
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot create database file " + file, e);
        }
    }

    /**
     * Sets the permissions of an existing file or directory if they differ and the
     * current user owns it. Symbolic links are skipped (and logged), so the target of
     * a link is never changed. Failures are logged, not thrown.
     */
    private static void makePrivate(Path path, Set<PosixFilePermission> permissions) {
        try {
            if (Files.isSymbolicLink(path)) {
                // chmod would change the link's target, which may be anything; leave it.
                LOGGER.log(Level.WARNING, "{0} is a symbolic link; leaving the permissions of its "
                        + "target as they are", path);
                return;
            }
            if (Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(permissions)) {
                return;
            }
            UserPrincipal me = path.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name"));
            if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(me)) {
                LOGGER.log(Level.WARNING, "{0} is not owned by the current user; leaving its "
                        + "permissions as they are", path);
                return;
            }
            // NOFOLLOW_LINKS: should the path be swapped for a link meanwhile, fail
            // rather than change the link's target.
            Files.getFileAttributeView(path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                    .setPermissions(permissions);
            LOGGER.log(Level.INFO, "Restricted the permissions of {0} to {1}",
                    new Object[]{path, PosixFilePermissions.toString(permissions)});
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Could not restrict the permissions of " + path, e);
        }
    }

    private void configure() {
        execute(c -> {
            try (PreparedStatement foreignKeys = c.prepareStatement("PRAGMA foreign_keys = ON");
                 PreparedStatement busyTimeout = c.prepareStatement("PRAGMA busy_timeout = 5000")) {
                foreignKeys.execute();
                busyTimeout.execute();
            }
            return null;
        });
    }

    private void migrate() {
        int version = schemaVersion();
        if (version > SCHEMA_VERSION) {
            throw new DataAccessException("Database " + location + " has schema version " + version
                    + ", but this version of the app only supports up to " + SCHEMA_VERSION);
        }
        for (int v = version; v < SCHEMA_VERSION; v++) {
            int target = v + 1;
            List<String> statements = MIGRATIONS.get(v);
            inTransaction(c -> {
                for (String sql : statements) {
                    try (PreparedStatement statement = c.prepareStatement(sql)) {
                        statement.execute();
                    }
                }
                // PRAGMA values cannot be bound as parameters; target is a trusted int.
                try (PreparedStatement statement = c.prepareStatement("PRAGMA user_version = " + target)) {
                    statement.execute();
                }
                return null;
            });
            LOGGER.log(Level.INFO, "Upgraded database schema to version {0}", target);
        }
    }

    /**
     * @return the schema version stored in the database
     */
    public int schemaVersion() {
        return execute(c -> {
            try (PreparedStatement statement = c.prepareStatement("PRAGMA user_version");
                 ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    /**
     * @return where this database lives
     */
    public DatabaseLocation getLocation() {
        return location;
    }

    /**
     * Runs work with the connection in auto-commit mode.
     *
     * @throws DataAccessException if the work throws an {@link SQLException}
     */
    public synchronized <T> T execute(SqlWork<T> work) {
        ensureOpen();
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    /**
     * Runs work in one transaction: it is committed if the work returns normally
     * and rolled back if it throws.
     *
     * @throws DataAccessException if the work throws an {@link SQLException}
     */
    public synchronized <T> T inTransaction(SqlWork<T> work) {
        ensureOpen();
        try {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(e);
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        }
    }

    private void rollbackQuietly(Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }

    private void ensureOpen() {
        try {
            if (connection.isClosed()) {
                throw new DataAccessException("Database " + location + " is closed");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database " + location + " is not usable", e);
        }
    }

    /**
     * Closes the database. Closing twice has no effect.
     */
    @Override
    public synchronized void close() {
        try {
            if (!connection.isClosed()) {
                connection.close();
                LOGGER.log(Level.INFO, "Closed database {0}", location);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Could not close database " + location, e);
        }
    }
}
