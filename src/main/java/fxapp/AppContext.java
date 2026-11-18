package fxapp;

import model.persistence.AccountRepository;
import model.persistence.DatabaseLocation;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemoryReportRepository;
import model.persistence.ReportRepository;
import model.persistence.SqliteAccountRepository;
import model.persistence.SqliteDatabase;
import model.persistence.SqliteReportRepository;
import model.security.PasswordHasher;
import model.service.AccountService;
import model.service.AuthService;
import model.service.ReportService;

import java.time.Clock;
import java.util.Objects;

/**
 * The services the app uses, wired to their storage. {@link Main} builds one when it
 * starts and closes it when it stops; controllers get the services from Main.
 */
public final class AppContext implements AutoCloseable {

    private final SqliteDatabase database;
    private final AuthService authService;
    private final AccountService accountService;
    private final ReportService reportService;

    private AppContext(SqliteDatabase database, AccountRepository accounts,
                       ReportRepository reports, PasswordHasher hasher, Clock clock) {
        this.database = database;
        this.authService = new AuthService(accounts, hasher);
        this.accountService = new AccountService(accounts);
        this.reportService = new ReportService(reports, clock);
    }

    /**
     * Opens the SQLite database at the given location (creating it if needed).
     *
     * @throws model.persistence.DataAccessException if the database cannot be opened
     */
    public static AppContext open(DatabaseLocation location) {
        return open(location, new PasswordHasher(), Clock.systemUTC());
    }

    /**
     * Opens the SQLite database at the given location with a specific hasher and clock.
     */
    public static AppContext open(DatabaseLocation location, PasswordHasher hasher, Clock clock) {
        SqliteDatabase database = SqliteDatabase.open(Objects.requireNonNull(location, "location"));
        return new AppContext(database, new SqliteAccountRepository(database),
                new SqliteReportRepository(database), hasher, clock);
    }

    /**
     * @return a context that keeps everything in memory (no database), for tests
     */
    public static AppContext inMemory(PasswordHasher hasher, Clock clock) {
        return new AppContext(null, new InMemoryAccountRepository(),
                new InMemoryReportRepository(), hasher, clock);
    }

    public AuthService getAuthService() {
        return authService;
    }

    public AccountService getAccountService() {
        return accountService;
    }

    public ReportService getReportService() {
        return reportService;
    }

    /**
     * Closes the database, if any.
     */
    @Override
    public void close() {
        if (database != null) {
            database.close();
        }
    }
}
