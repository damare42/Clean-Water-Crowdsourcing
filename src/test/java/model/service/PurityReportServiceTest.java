package model.service;

import model.Account;
import model.AccountType;
import model.GeoPoint;
import model.MonthlyAverage;
import model.Permission;
import model.PurityHistory;
import model.PurityMetric;
import model.Validation;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.persistence.InMemoryPurityReportRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.security.SecurityEvent;
import model.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import testutil.MutableClock;

import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PurityReportServiceTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final GeoPoint ATLANTA = new GeoPoint(33.7490, -84.3880);

    private InMemoryPurityReportRepository repository;
    private InMemorySecurityLogRepository events;
    private MutableClock clock;
    private PurityReportService service;
    private Account worker;
    private Account manager;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurityReportRepository();
        events = new InMemorySecurityLogRepository();
        clock = new MutableClock(Instant.parse("2016-10-11T12:30:00Z"));
        service = new PurityReportService(repository, clock, NEW_YORK,
                new AccessControl(new SecurityLog(events, clock)));
        worker = Account.create(AccountType.Worker, "wendy", "Wendy", "Ward", "w@example.com");
        manager = Account.create(AccountType.Manager, "max", "Max", "Mann", "m@example.com");
    }

    private WaterPurityReport fileAt(String instant, GeoPoint where, double virus, double contaminant) {
        clock.set(Instant.parse(instant));
        return service.submit(worker, where, WaterPurityCondition.Safe, virus, contaminant);
    }

    @Test
    void workerSubmitsAndReportIsStampedAndNumbered() {
        WaterPurityReport first = service.submit(worker, ATLANTA, WaterPurityCondition.Unsafe, 3.5, 7);
        WaterPurityReport second = service.submit(manager, ATLANTA, WaterPurityCondition.Safe, 0, 0);

        assertEquals(1, first.getReportNumber());
        assertEquals("wendy", first.getReporter());
        assertEquals(Instant.parse("2016-10-11T12:30:00Z"), first.getReportedAt());
        assertEquals(2, second.getReportNumber());
        assertEquals(3, service.nextReportNumber(worker));
        assertEquals(List.of(first, second), service.listReports(worker));
        assertEquals(Optional.of(second), service.findReport(manager, 2));
    }

    @Test
    void invalidReportStoresNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(worker, ATLANTA, WaterPurityCondition.Safe, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(worker, ATLANTA, null, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(worker, null, WaterPurityCondition.Safe, 1, 0));
        assertEquals(1, service.nextReportNumber(worker));
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"User", "Admin"})
    void usersAndAdminsCannotSubmitOrViewPurityReports(AccountType type) {
        Account actor = Account.create(type, "someone", "Some", "One", "s@example.com");

        assertThrows(PermissionDeniedException.class,
                () -> service.submit(actor, ATLANTA, WaterPurityCondition.Safe, 0, 0));
        assertThrows(PermissionDeniedException.class, () -> service.listReports(actor));
        assertThrows(PermissionDeniedException.class, () -> service.findReport(actor, 1));
        assertThrows(PermissionDeniedException.class,
                () -> service.history(actor, ATLANTA, 1, 2016, PurityMetric.Virus));
        assertEquals(1, service.nextReportNumber(worker));
        assertEquals(4, events.countAfter(SecurityEventType.PERMISSION_DENIED, "", Instant.EPOCH));
    }

    @Test
    void nextReportNumberNeedsSubmitPermission() {
        service.submit(worker, ATLANTA, WaterPurityCondition.Safe, 0, 0);
        Account user = Account.create(AccountType.User, "ursula", "Ur", "Sula", "u@example.com");

        PermissionDeniedException userDenied = assertThrows(PermissionDeniedException.class,
                () -> service.nextReportNumber(user));
        assertEquals(Permission.SUBMIT_PURITY_REPORT, userDenied.getPermission());
        SecurityEvent userEvent = events.findNewestFirst(1).get(0);
        assertEquals(SecurityEventType.PERMISSION_DENIED, userEvent.type());
        assertEquals("ursula", userEvent.actor());
        assertEquals("User lacks SUBMIT_PURITY_REPORT", userEvent.detail());

        assertThrows(PermissionDeniedException.class, () -> service.nextReportNumber(null));
        SecurityEvent nobodyEvent = events.findNewestFirst(1).get(0);
        assertEquals(SecurityEventType.PERMISSION_DENIED, nobodyEvent.type());
        assertEquals("not logged in lacks SUBMIT_PURITY_REPORT", nobodyEvent.detail());
        assertEquals(2, events.countAfter(SecurityEventType.PERMISSION_DENIED, "", Instant.EPOCH));

        assertEquals(2, service.nextReportNumber(worker));
        assertEquals(2, events.findNewestFirst(10).size(), "allowed calls are not logged");
    }

    @Test
    void onlyManagersSeeTheHistory() {
        assertThrows(PermissionDeniedException.class,
                () -> service.history(worker, ATLANTA, 1, 2016, PurityMetric.Virus));
        assertThrows(PermissionDeniedException.class,
                () -> service.history(null, ATLANTA, 1, 2016, PurityMetric.Virus));
        assertTrue(service.history(manager, ATLANTA, 1, 2016, PurityMetric.Virus).isEmpty());
    }

    @Test
    void historyAveragesPerMonthInTheUsersZone() {
        // 2016-01-01T04:59Z is still 2015-12-31 in New York: not part of 2016
        fileAt("2016-01-01T04:59:59Z", ATLANTA, 1000, 1000);
        // 2016-01-01T05:00Z is midnight in New York: January
        fileAt("2016-01-01T05:00:00Z", ATLANTA, 10, 1);
        fileAt("2016-01-31T12:00:00Z", ATLANTA, 20, 3);
        // 2016-02-01T03:00Z is still January 31 in New York
        fileAt("2016-02-01T03:00:00Z", ATLANTA, 30, 5);
        fileAt("2016-03-15T12:00:00Z", ATLANTA, 5, 0);
        // 2017-01-01T04:00Z is still 2016-12-31 in New York: December 2016
        fileAt("2017-01-01T04:00:00Z", ATLANTA, 8, 8);
        fileAt("2017-01-01T05:00:00Z", ATLANTA, 999, 999);

        PurityHistory virus = service.history(manager, ATLANTA, 1, 2016, PurityMetric.Virus);
        PurityHistory contaminant = service.history(manager, ATLANTA, 1, 2016, PurityMetric.Contaminant);

        assertEquals(List.of(
                new MonthlyAverage(Month.JANUARY, 20, 3),
                new MonthlyAverage(Month.MARCH, 5, 1),
                new MonthlyAverage(Month.DECEMBER, 8, 1)), virus.months());
        assertEquals(List.of(3.0, 0.0, 8.0), contaminant.months().stream().map(MonthlyAverage::average).toList());
        assertEquals(2016, virus.year());
        assertEquals(ATLANTA, virus.center());
        assertEquals(PurityMetric.Contaminant, contaminant.metric());
    }

    @Test
    void historyOnlyCountsReportsWithinTheRadius() {
        GeoPoint near = new GeoPoint(ATLANTA.latitude() + 0.008, ATLANTA.longitude()); // about 0.89 km
        GeoPoint far = new GeoPoint(ATLANTA.latitude() + 0.01, ATLANTA.longitude());   // about 1.11 km
        fileAt("2016-05-10T12:00:00Z", ATLANTA, 2, 0);
        fileAt("2016-05-11T12:00:00Z", near, 4, 0);
        fileAt("2016-05-12T12:00:00Z", far, 100, 0);
        fileAt("2016-05-12T12:00:00Z", new GeoPoint(-33.7, 151.2), 100, 0);

        assertEquals(List.of(new MonthlyAverage(Month.MAY, 3, 2)),
                service.history(manager, ATLANTA, 1, 2016, PurityMetric.Virus).months());
        assertEquals(List.of(new MonthlyAverage(Month.MAY, 106.0 / 3, 3)),
                service.history(manager, ATLANTA, 1.2, 2016, PurityMetric.Virus).months());
    }

    @Test
    void emptyHistory() {
        fileAt("2015-05-10T12:00:00Z", ATLANTA, 2, 0);

        PurityHistory history = service.history(manager, ATLANTA, 5, 2016, PurityMetric.Virus);

        assertTrue(history.isEmpty());
        assertTrue(PurityReportService.monthlyAverages(List.of(), ATLANTA, 1, 2016, PurityMetric.Virus,
                ZoneOffset.UTC).isEmpty());
    }

    @Test
    void historyValidatesRadiusAndYear() {
        assertEquals(Validation.MSG_INVALID_RADIUS, assertThrows(IllegalArgumentException.class,
                () -> service.history(manager, ATLANTA, 0, 2016, PurityMetric.Virus)).getMessage());
        assertEquals(Validation.MSG_INVALID_RADIUS, assertThrows(IllegalArgumentException.class,
                () -> service.history(manager, ATLANTA, Double.NaN, 2016, PurityMetric.Virus)).getMessage());
        assertEquals(Validation.MSG_INVALID_YEAR, assertThrows(IllegalArgumentException.class,
                () -> service.history(manager, ATLANTA, 1, 1899, PurityMetric.Virus)).getMessage());
        assertThrows(NullPointerException.class, () -> service.history(manager, ATLANTA, 1, 2016, null));
    }

    @Test
    void monthlyAveragesUsesTheGivenZone() {
        WaterPurityReport newYearsEveUtc = new WaterPurityReport(1, "a", Instant.parse("2016-12-31T23:30:00Z"),
                ATLANTA, WaterPurityCondition.Safe, 4, 0);

        assertEquals(List.of(new MonthlyAverage(Month.DECEMBER, 4, 1)), PurityReportService.monthlyAverages(
                List.of(newYearsEveUtc), ATLANTA, 1, 2016, PurityMetric.Virus, ZoneOffset.UTC));
        assertTrue(PurityReportService.monthlyAverages(List.of(newYearsEveUtc), ATLANTA, 1, 2016,
                PurityMetric.Virus, ZoneId.of("Asia/Tokyo")).isEmpty(), "already 2017 in Tokyo");
    }
}
