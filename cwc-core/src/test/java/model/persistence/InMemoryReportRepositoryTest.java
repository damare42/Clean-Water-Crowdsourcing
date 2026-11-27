package model.persistence;

class InMemoryReportRepositoryTest extends ReportRepositoryContract {

    @Override
    protected ReportRepository createRepository() {
        return new InMemoryReportRepository();
    }
}
