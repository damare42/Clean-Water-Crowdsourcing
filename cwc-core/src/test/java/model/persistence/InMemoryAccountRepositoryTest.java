package model.persistence;

class InMemoryAccountRepositoryTest extends AccountRepositoryContract {

    @Override
    protected AccountRepository createRepository() {
        return new InMemoryAccountRepository();
    }
}
