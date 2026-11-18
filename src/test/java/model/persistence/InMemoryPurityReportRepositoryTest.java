package model.persistence;

class InMemoryPurityReportRepositoryTest extends PurityReportRepositoryContract {

    @Override
    protected PurityReportRepository createRepository() {
        return new InMemoryPurityReportRepository();
    }
}
