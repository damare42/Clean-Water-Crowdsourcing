package model.persistence;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
     * to {@code i + 1}. Append new entries; never change existing ones.
     */
    private static final List<List<String>> MIGRATIONS = List.of(
            List.of("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        username_key  TEXT    NOT NULL PRIMARY KEY,
                        username      TEXT    NOT NULL,
                        account_type  TEXT    NOT NULL
                                      CHECK (account_type IN ('User', 'Worker', 'Manager', 'Admin')),
                        password_hash TEXT    NOT NULL,
                        first_name    TEXT    NOT NULL,
                        last_name     TEXT    NOT NULL,
                        email         TEXT    NOT NULL,
                        phone         TEXT    NOT NULL DEFAULT '',
                        address       TEXT    NOT NULL DEFAULT ''
                    )""", """
                    CREATE TABLE IF NOT EXISTS water_source_reports (
                        report_number   INTEGER NOT NULL PRIMARY KEY CHECK (report_number > 0),
                        reporter        TEXT    NOT NULL,
                        reported_at_ms  INTEGER NOT NULL,
                        latitude        REAL    NOT NULL CHECK (latitude BETWEEN -90 AND 90),
                        longitude       REAL    NOT NULL CHECK (longitude BETWEEN -180 AND 180),
                        water_type      TEXT    NOT NULL,
                        water_condition TEXT    NOT NULL
                    )"""));

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
        location.getFile().ifPresent(SqliteDatabase::prepareFile);
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
     */
    private static void prepareFile(Path file) {
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.isDirectory(parent)) {
                if (posix) {
                    Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rwx------")));
                } else {
                    Files.createDirectories(parent);
                }
            }
            if (posix && !Files.exists(file)) {
                try {
                    Files.createFile(file, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
                } catch (FileAlreadyExistsException e) {
                    // created concurrently; fine
                }
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot create database file " + file, e);
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
