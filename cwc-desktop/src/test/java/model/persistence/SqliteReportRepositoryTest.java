package model.persistence;

import model.WaterSourceReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteReportRepositoryTest extends ReportRepositoryContract {

    @TempDir
    Path dir;

    private SqliteDatabase database;

    private Path file() {
        return dir.resolve("reports.db");
    }

    @Override
    protected ReportRepository createRepository() {
        database = SqliteDatabase.open(DatabaseLocation.file(file()));
        return new SqliteReportRepository(database);
    }

    @AfterEach
    void closeDatabase() {
        database.close();
    }

    @Test
    void reportsAndNumberingSurviveClosingAndReopening() {
        WaterSourceReport first = repository.add(draft("alice"));
        WaterSourceReport second = repository.add(draft("bob"));
        database.close();

        database = SqliteDatabase.open(DatabaseLocation.file(file()));
        ReportRepository reopened = new SqliteReportRepository(database);

        assertEquals(List.of(first, second), reopened.findAll());
        assertEquals(3, reopened.nextReportNumber());
        assertEquals(3, reopened.add(draft("carol")).getReportNumber());
    }

    @Test
    void sharesDatabaseWithAccountRepository() {
        SqliteAccountRepository accounts = new SqliteAccountRepository(database);
        accounts.add(model.Account.create(model.AccountType.User, "alice", "A", "B",
                "a@example.com"), "hash");

        assertEquals(1, repository.add(draft("alice")).getReportNumber());
        assertEquals(true, accounts.existsById("alice"));
    }
}
