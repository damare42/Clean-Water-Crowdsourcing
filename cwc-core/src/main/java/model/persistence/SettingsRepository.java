package model.persistence;

import java.util.Optional;

/**
 * Settings of this installation of the app, as text values under text keys. They
 * belong to the computer, not to an account.
 */
public interface SettingsRepository {

    /**
     * @param key the setting's key, not blank
     * @return the stored value, or empty if there is none
     * @throws DataAccessException if the storage fails
     */
    Optional<String> get(String key);

    /**
     * Stores a value, replacing an earlier one.
     *
     * @param key the setting's key, not blank
     * @param value the value, not null
     * @throws DataAccessException if the storage fails
     */
    void put(String key, String value);

    /**
     * Removes a setting; does nothing if it is not stored.
     *
     * @param key the setting's key, not blank
     * @throws DataAccessException if the storage fails
     */
    void remove(String key);

    /**
     * Checks a key.
     *
     * @param key the key
     * @return the key
     * @throws IllegalArgumentException if the key is null or blank
     */
    static String checkKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A setting needs a key");
        }
        return key;
    }
}
