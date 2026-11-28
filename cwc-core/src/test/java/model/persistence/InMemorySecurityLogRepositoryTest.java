package model.persistence;

class InMemorySecurityLogRepositoryTest extends SecurityLogRepositoryContract {

    @Override
    protected SecurityLogRepository createRepository() {
        return new InMemorySecurityLogRepository();
    }
}
