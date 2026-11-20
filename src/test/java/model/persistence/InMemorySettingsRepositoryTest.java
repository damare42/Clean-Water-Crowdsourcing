package model.persistence;

class InMemorySettingsRepositoryTest extends SettingsRepositoryContract {

    @Override
    protected SettingsRepository createRepository() {
        return new InMemorySettingsRepository();
    }
}
