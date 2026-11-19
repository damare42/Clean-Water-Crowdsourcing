package fxapp;

import model.persistence.AccountRepository;
import model.persistence.DatabaseLocation;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemoryPurityReportRepository;
import model.persistence.InMemoryReportRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.persistence.PurityReportRepository;
import model.persistence.ReportRepository;
import model.persistence.SecurityLogRepository;
import model.persistence.SqliteAccountRepository;
import model.persistence.SqliteDatabase;
import model.persistence.SqlitePurityReportRepository;
import model.persistence.SqliteReportRepository;
import model.persistence.SqliteSecurityLogRepository;
import model.security.LockoutPolicy;
import model.security.PasswordHasher;
import model.service.AccessControl;
import model.service.AccountService;
import model.service.AuthService;
import model.service.PurityReportService;
import model.service.ReportService;
import model.service.SecurityLog;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The services the app uses, wired to their storage. {@link Main} builds one when it
 * starts and closes it when it stops; controllers get the services from Main.
 * <p>
 * Building a context computes one password hash (see {@link AuthService}), so it takes
 * a fraction of a second.
 */
public final class AppContext implements AutoCloseable {

    private final SqliteDatabase database;
    private final SecurityLog securityLog;
    private final AuthService authService;
    private final AccountService accountService;
    private final ReportService reportService;
    private final PurityReportService purityReportService;

    private AppContext(SqliteDatabase database, AccountRepository accounts, ReportRepository reports,
                       PurityReportRepository purityReports, SecurityLogRepository securityEvents,
                       PasswordHasher hasher, Clock clock, ZoneId zone) {
        this.database = database;
        this.securityLog = new SecurityLog(securityEvents, clock);
        AccessControl access = new AccessControl(securityLog);
        this.authService = new AuthService(accounts, hasher, securityLog, LockoutPolicy.DEFAULT);
        this.accountService = new AccountService(accounts, access, securityLog);
        this.reportService = new ReportService(reports, clock, access);
        this.purityReportService = new PurityReportService(purityReports, clock, zone, access);
    }

    /**
     * Opens the SQLite database at the given location (creating it if needed).
     *
     * @throws model.persistence.DataAccessException if the database cannot be opened
     */
    public static AppContext open(DatabaseLocation location) {
        return open(location, new PasswordHasher(), Clock.systemUTC(), ZoneId.systemDefault());
    }

    /**
     * Opens the SQLite database at the given location with a specific hasher, clock
     * and time zone (the zone sets the calendar months of the purity history).
     */
    public static AppContext open(DatabaseLocation location, PasswordHasher hasher, Clock clock,
                                  ZoneId zone) {
        SqliteDatabase database = SqliteDatabase.open(Objects.requireNonNull(location, "location"));
        try {
            return new AppContext(database, new SqliteAccountRepository(database),
                    new SqliteReportRepository(database), new SqlitePurityReportRepository(database),
                    new SqliteSecurityLogRepository(database), hasher, clock, zone);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }

    /**
     * @return a context that keeps everything in memory (no database), for tests
     */
    public static AppContext inMemory(PasswordHasher hasher, Clock clock, ZoneId zone) {
        return new AppContext(null, new InMemoryAccountRepository(), new InMemoryReportRepository(),
                new InMemoryPurityReportRepository(), new InMemorySecurityLogRepository(),
                hasher, clock, zone);
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

    public PurityReportService getPurityReportService() {
        return purityReportService;
    }

    public SecurityLog getSecurityLog() {
        return securityLog;
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
