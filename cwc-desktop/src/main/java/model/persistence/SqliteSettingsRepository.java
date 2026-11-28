package model.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link SettingsRepository} backed by the {@code app_settings} table of a
 * {@link SqliteDatabase} (schema version 3).
 */
public final class SqliteSettingsRepository implements SettingsRepository {

    private final SqliteDatabase database;

    public SqliteSettingsRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<String> get(String key) {
        String checked = SettingsRepository.checkKey(key);
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT value FROM app_settings WHERE key = ?")) {
                statement.setString(1, checked);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString(1)) : Optional.<String>empty();
                }
            }
        });
    }

    @Override
    public void put(String key, String value) {
        String checked = SettingsRepository.checkKey(key);
        Objects.requireNonNull(value, "value");
        database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "INSERT INTO app_settings (key, value) VALUES (?, ?)"
                            + " ON CONFLICT (key) DO UPDATE SET value = excluded.value")) {
                statement.setString(1, checked);
                statement.setString(2, value);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void remove(String key) {
        String checked = SettingsRepository.checkKey(key);
        database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement("DELETE FROM app_settings WHERE key = ?")) {
                statement.setString(1, checked);
                return statement.executeUpdate();
            }
        });
    }
}
