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
    private final boolean appDirectory;

    private DatabaseLocation(Path file, boolean appDirectory) {
        this.file = file;
        this.appDirectory = appDirectory;
    }

    /**
     * @param file the database file; its parent directory is created when opened
     * @return a location for that file
     * @throws IllegalArgumentException if the path contains {@code ?}, which the
     *         SQLite driver would read as the start of connection options
     */
    public static DatabaseLocation file(Path file) {
        return file(file, false);
    }

    private static DatabaseLocation file(Path file, boolean appDirectory) {
        Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        if (absolute.toString().indexOf('?') >= 0) {
            throw new IllegalArgumentException("The database path must not contain '?': " + absolute);
        }
        return new DatabaseLocation(absolute, appDirectory);
    }

    /**
     * @return a location for a database that only exists while it is open
     */
    public static DatabaseLocation inMemory() {
        return new DatabaseLocation(null, false);
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
     * @throws IllegalArgumentException if the override is not a usable path
     */
    static DatabaseLocation resolve(String override, String userHome) {
        if (override != null && !override.isBlank()) {
            String value = override.strip();
            return IN_MEMORY.equals(value) ? inMemory() : file(Path.of(value));
        }
        if (userHome == null || userHome.isBlank()) {
            throw new DataAccessException("Cannot find the home directory; set -D" + PROPERTY);
        }
        return file(Path.of(userHome, DEFAULT_DIRECTORY, DEFAULT_FILE), true);
    }

    /**
     * @return the database file, or empty for an in-memory database
     */
    public Optional<Path> getFile() {
        return Optional.ofNullable(file);
    }

    /**
     * @return true if the file's directory is the app's own default directory
     *         ({@code ~/.cwc}), which holds nothing else and may be made private.
     *         False for a file chosen with {@value #PROPERTY}, whose directory may
     *         be shared.
     */
    public boolean isAppDirectory() {
        return appDirectory;
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
