package fxapp;

import model.geo.ApproximateLocation;
import model.geo.GeoConfig;
import model.geo.IpLocationProvider;
import model.geo.LocationProvider;
import model.geo.LocationService;
import model.geo.TimeZoneLocations;
import model.persistence.AccountRepository;
import model.persistence.DatabaseLocation;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemoryPurityReportRepository;
import model.persistence.InMemoryReportRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.persistence.InMemorySettingsRepository;
import model.persistence.PurityReportRepository;
import model.persistence.ReportRepository;
import model.persistence.SecurityLogRepository;
import model.persistence.SettingsRepository;
import model.persistence.SqliteAccountRepository;
import model.persistence.SqliteDatabase;
import model.persistence.SqlitePurityReportRepository;
import model.persistence.SqliteReportRepository;
import model.persistence.SqliteSecurityLogRepository;
import model.persistence.SqliteSettingsRepository;
import model.security.LockoutPolicy;
import model.security.PasswordHasher;
import model.service.AccessControl;
import model.service.AccountService;
import model.service.AuthService;
import model.service.PurityReportService;
import model.service.ReportService;
import model.service.SecurityLog;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

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
    private final LocationService locationService;

    /** Identifies the app to the IP location service (and nothing else is sent). */
    public static final String USER_AGENT = "CleanWaterCrowdsourcing/1.0 (JavaFX desktop app)";

    private AppContext(SqliteDatabase database, AccountRepository accounts, ReportRepository reports,
                       PurityReportRepository purityReports, SecurityLogRepository securityEvents,
                       SettingsRepository settings, PasswordHasher hasher, Clock clock, ZoneId zone,
                       GeoConfig geo) {
        this.database = database;
        this.securityLog = new SecurityLog(securityEvents, clock);
        AccessControl access = new AccessControl(securityLog);
        this.authService = new AuthService(accounts, hasher, securityLog, LockoutPolicy.DEFAULT);
        this.accountService = new AccountService(accounts, access, securityLog);
        this.reportService = new ReportService(reports, clock, access);
        this.purityReportService = new PurityReportService(purityReports, clock, zone, access);
        this.locationService = new LocationService(settings, ipProvider(geo, clock), TimeZoneLocations.load(),
                zone, clock, geo);
    }

    /**
     * The IP lookup for a configuration; its HTTP client is only created on the first
     * lookup, so contexts that never look anything up open no connections or threads.
     */
    private static LocationProvider ipProvider(GeoConfig geo, Clock clock) {
        if (geo.url().isEmpty()) {
            return Optional::empty;
        }
        return new LocationProvider() {
            private IpLocationProvider provider;

            @Override
            public synchronized Optional<ApproximateLocation> lookup() {
                if (provider == null) {
                    HttpClient client = IpLocationProvider.newClient();
                    provider = new IpLocationProvider(client, geo.url().get(), IpLocationProvider.TIMEOUT,
                            USER_AGENT, clock);
                }
                return provider.lookup();
            }
        };
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
        return open(location, hasher, clock, zone, GeoConfig.fromSystemProperties());
    }

    /**
     * Like {@link #open(DatabaseLocation, PasswordHasher, Clock, ZoneId)}, with a given
     * IP location configuration (for tests).
     */
    public static AppContext open(DatabaseLocation location, PasswordHasher hasher, Clock clock,
                                  ZoneId zone, GeoConfig geo) {
        SqliteDatabase database = SqliteDatabase.open(Objects.requireNonNull(location, "location"));
        try {
            return new AppContext(database, new SqliteAccountRepository(database),
                    new SqliteReportRepository(database), new SqlitePurityReportRepository(database),
                    new SqliteSecurityLogRepository(database), new SqliteSettingsRepository(database),
                    hasher, clock, zone, Objects.requireNonNull(geo, "geo"));
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
    }

    /**
     * @return a context that keeps everything in memory (no database) and never looks
     *         up the location over the network, for tests
     */
    public static AppContext inMemory(PasswordHasher hasher, Clock clock, ZoneId zone) {
        return new AppContext(null, new InMemoryAccountRepository(), new InMemoryReportRepository(),
                new InMemoryPurityReportRepository(), new InMemorySecurityLogRepository(),
                new InMemorySettingsRepository(), hasher, clock, zone, GeoConfig.off());
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

    public LocationService getLocationService() {
        return locationService;
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
