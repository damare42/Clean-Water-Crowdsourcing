package model.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link SettingsRepository} that keeps the settings in memory, for tests.
 */
public final class InMemorySettingsRepository implements SettingsRepository {

    private final ConcurrentMap<String, String> values = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(SettingsRepository.checkKey(key)));
    }

    @Override
    public void put(String key, String value) {
        values.put(SettingsRepository.checkKey(key), Objects.requireNonNull(value, "value"));
    }

    @Override
    public void remove(String key) {
        values.remove(SettingsRepository.checkKey(key));
    }
}
