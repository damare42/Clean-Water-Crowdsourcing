package model.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseLocationTest {

    @TempDir
    Path home;

    @Test
    void defaultIsDotCwcInHomeDirectory() {
        DatabaseLocation location = DatabaseLocation.resolve(null, home.toString());

        Path expected = home.resolve(".cwc").resolve("cwc.db").toAbsolutePath().normalize();
        assertEquals(Optional.of(expected), location.getFile());
        assertEquals("jdbc:sqlite:" + expected, location.jdbcUrl());
    }

    @Test
    void blankOverrideMeansDefault() {
        assertEquals(DatabaseLocation.resolve(null, home.toString()).getFile(),
                DatabaseLocation.resolve("  ", home.toString()).getFile());
    }

    @Test
    void overrideSelectsAnotherFile() {
        Path file = home.resolve("custom/data.db");

        DatabaseLocation location = DatabaseLocation.resolve(" " + file + " ", "/ignored");

        assertEquals(Optional.of(file.toAbsolutePath().normalize()), location.getFile());
    }

    @Test
    void relativeOverrideIsMadeAbsolute() {
        DatabaseLocation location = DatabaseLocation.resolve("data/../cwc-test.db", home.toString());

        Path file = location.getFile().orElseThrow();
        assertTrue(file.isAbsolute());
        assertEquals(Path.of("cwc-test.db").toAbsolutePath(), file);
    }

    @Test
    void memoryOverrideSelectsInMemoryDatabase() {
        DatabaseLocation location = DatabaseLocation.resolve(":memory:", home.toString());

        assertTrue(location.getFile().isEmpty());
        assertEquals("jdbc:sqlite::memory:", location.jdbcUrl());
        assertEquals(":memory:", location.toString());
    }

    @Test
    void missingHomeWithoutOverrideFails() {
        assertThrows(DataAccessException.class, () -> DatabaseLocation.resolve(null, null));
        assertThrows(DataAccessException.class, () -> DatabaseLocation.resolve(null, " "));
    }

    @Test
    void fromSystemPropertiesReadsOverride() {
        String previous = System.getProperty(DatabaseLocation.PROPERTY);
        Path file = home.resolve("prop.db");
        try {
            System.setProperty(DatabaseLocation.PROPERTY, file.toString());
            assertEquals(Optional.of(file.toAbsolutePath().normalize()),
                    DatabaseLocation.fromSystemProperties().getFile());

            System.clearProperty(DatabaseLocation.PROPERTY);
            assertEquals(Optional.of(Path.of(System.getProperty("user.home"), ".cwc", "cwc.db")
                            .toAbsolutePath().normalize()),
                    DatabaseLocation.fromSystemProperties().getFile());
        } finally {
            if (previous == null) {
                System.clearProperty(DatabaseLocation.PROPERTY);
            } else {
                System.setProperty(DatabaseLocation.PROPERTY, previous);
            }
        }
    }
}
