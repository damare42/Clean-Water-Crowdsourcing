package model.persistence;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Where the SQLite database lives: a file, or memory only.
 * <p>
 * By default the database is {@code ~/.cwc/cwc.db}. The system property
 * {@value #PROPERTY} overrides this with another file path, or with
 * {@value #IN_MEMORY} for a database that is discarded when the app exits.
 */
public final class DatabaseLocation {

    /** System property that overrides the database location. */
    public static final String PROPERTY = "cwc.db.path";
    /** Property value that selects an in-memory database. */
    public static final String IN_MEMORY = ":memory:";
    /** Directory under the user's home directory that holds the default database. */
    public static final String DEFAULT_DIRECTORY = ".cwc";
    /** File name of the default database. */
    public static final String DEFAULT_FILE = "cwc.db";

    private final Path file;

    private DatabaseLocation(Path file) {
        this.file = file;
    }

    /**
     * @param file the database file; its parent directory is created when opened
     * @return a location for that file
     */
    public static DatabaseLocation file(Path file) {
        return new DatabaseLocation(Objects.requireNonNull(file, "file").toAbsolutePath().normalize());
    }

    /**
     * @return a location for a database that only exists while it is open
     */
    public static DatabaseLocation inMemory() {
        return new DatabaseLocation(null);
    }

    /**
     * @return the location chosen by the {@value #PROPERTY} system property, or the
     *         default location in the user's home directory
     */
    public static DatabaseLocation fromSystemProperties() {
        return resolve(System.getProperty(PROPERTY), System.getProperty("user.home"));
    }

    /**
     * @param override the value of {@value #PROPERTY}, or null/blank for the default
     * @param userHome the user's home directory
     * @return the location the override selects
     */
    static DatabaseLocation resolve(String override, String userHome) {
        if (override != null && !override.isBlank()) {
            String value = override.strip();
            return IN_MEMORY.equals(value) ? inMemory() : file(Path.of(value));
        }
        if (userHome == null || userHome.isBlank()) {
            throw new DataAccessException("Cannot find the home directory; set -D" + PROPERTY);
        }
        return file(Path.of(userHome, DEFAULT_DIRECTORY, DEFAULT_FILE));
    }

    /**
     * @return the database file, or empty for an in-memory database
     */
    public Optional<Path> getFile() {
        return Optional.ofNullable(file);
    }

    /**
     * @return the JDBC URL of the database
     */
    public String jdbcUrl() {
        return "jdbc:sqlite:" + (file == null ? IN_MEMORY : file.toString());
    }

    @Override
    public String toString() {
        return file == null ? IN_MEMORY : file.toString();
    }
}
