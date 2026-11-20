package model.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests every {@link SettingsRepository} implementation must pass.
 */
abstract class SettingsRepositoryContract {

    protected SettingsRepository repository;

    protected abstract SettingsRepository createRepository();

    @BeforeEach
    void setUp() {
        repository = createRepository();
    }

    @Test
    void missingKeyIsEmpty() {
        assertEquals(Optional.empty(), repository.get("location.mode"));
    }

    @Test
    void putThenGet() {
        repository.put("location.mode", "OFF");
        assertEquals(Optional.of("OFF"), repository.get("location.mode"));
    }

    @Test
    void putReplacesTheValue() {
        repository.put("location.mode", "OFF");
        repository.put("location.mode", "TIME_ZONE");
        assertEquals(Optional.of("TIME_ZONE"), repository.get("location.mode"));
    }

    @Test
    void valuesAreStoredLiterally() {
        String value = "33.7|-84.4|25.0|1700000000000|O'Brien \"x\" é ; DROP TABLE app_settings";
        repository.put("location.cache", value);
        repository.put("empty", "");
        assertEquals(Optional.of(value), repository.get("location.cache"));
        assertEquals(Optional.of(""), repository.get("empty"));
    }

    @Test
    void keysAreIndependent() {
        repository.put("a", "1");
        repository.put("b", "2");
        repository.remove("a");
        assertEquals(Optional.empty(), repository.get("a"));
        assertEquals(Optional.of("2"), repository.get("b"));
    }

    @Test
    void removeOfMissingKeyDoesNothing() {
        repository.remove("nothing");
        assertEquals(Optional.empty(), repository.get("nothing"));
    }

    @Test
    void blankKeysAndNullValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.get(""));
        assertThrows(IllegalArgumentException.class, () -> repository.put(" ", "x"));
        assertThrows(IllegalArgumentException.class, () -> repository.remove(null));
        assertThrows(NullPointerException.class, () -> repository.put("k", null));
    }
}
