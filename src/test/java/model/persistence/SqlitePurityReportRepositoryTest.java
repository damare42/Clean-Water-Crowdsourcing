package model.persistence;

import model.WaterPurityReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlitePurityReportRepositoryTest extends PurityReportRepositoryContract {

    @TempDir
    Path dir;

    private SqliteDatabase database;

    @Override
    protected PurityReportRepository createRepository() {
        database = SqliteDatabase.open(DatabaseLocation.file(dir.resolve("purity.db")));
        return new SqlitePurityReportRepository(database);
    }

    @AfterEach
    void closeDatabase() {
        database.close();
    }

    @Test
    void reportsSurviveReopeningAndAreNumberedSeparatelyFromSourceReports() {
        new SqliteReportRepository(database).add(ReportRepositoryContract.draft("x"));
        WaterPurityReport first = repository.add(draft("alice", WHEN));
        database.close();

        database = SqliteDatabase.open(DatabaseLocation.file(dir.resolve("purity.db")));
        PurityReportRepository reopened = new SqlitePurityReportRepository(database);

        assertEquals(1, first.getReportNumber());
        assertEquals(List.of(first), reopened.findAll());
        assertEquals(2, reopened.nextReportNumber());
    }
}
