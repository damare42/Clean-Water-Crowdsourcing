package model.service;

import model.Account;
import model.AccountType;
import model.Permission;
import model.Validation;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
import model.persistence.InMemoryReportRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.security.SecurityEvent;
import model.security.SecurityEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2016-10-11T12:30:00Z");

    private InMemoryReportRepository reports;
    private InMemorySecurityLogRepository events;
    private ReportService service;
    private Account alice;

    @BeforeEach
    void setUp() {
        reports = new InMemoryReportRepository();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        events = new InMemorySecurityLogRepository();
        service = new ReportService(reports, clock, new AccessControl(new SecurityLog(events, clock)));
        alice = Account.create(AccountType.User, "alice", "Alice", "Smith", "a@example.com");
    }

    private static Account account(AccountType type) {
        return Account.create(type, type.name().toLowerCase() + "1", "F", "L", "f@example.com");
    }

    private WaterSourceReport submit(Account reporter) {
        return service.submit(reporter, 33.77, -84.39, WaterSourceType.Pond, WaterSourceCondition.Waste);
    }

    @Test
    void numberingStartsAtOne() {
        assertEquals(1, service.nextReportNumber(alice));
        assertTrue(service.listReports(alice).isEmpty());
    }

    @Test
    void submitStampsReporterAndTimeAndAssignsSequentialNumbers() {
        WaterSourceReport first = submit(alice);
        WaterSourceReport second = submit(alice);

        assertEquals(1, first.getReportNumber());
        assertEquals(2, second.getReportNumber());
        assertEquals("alice", first.getReporter());
        assertEquals(NOW, first.getReportedAt());
        assertEquals("2016/10/11", first.getDate(ZoneOffset.UTC));
        assertEquals("12:30", first.getTime(ZoneOffset.UTC));
        assertEquals(3, service.nextReportNumber(alice));
        assertEquals(List.of(first, second), service.listReports(alice));
        assertEquals(List.of(first, second), reports.findAll());
    }

    @Test
    void submitWithInvalidDataStoresNothing() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.submit(alice, 91, 0, WaterSourceType.Lake, WaterSourceCondition.Clear));
        assertEquals(Validation.MSG_INVALID_LATITUDE, e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> service.submit(alice, 0, 0, null, WaterSourceCondition.Clear));

        assertTrue(reports.findAll().isEmpty());
        assertEquals(1, service.nextReportNumber(alice));
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"User", "Worker", "Manager"})
    void reportingRolesCanSubmitAndList(AccountType type) {
        Account reporter = account(type);

        WaterSourceReport report = submit(reporter);

        assertEquals(reporter.getId(), report.getReporter());
        assertEquals(List.of(report), service.listReports(reporter));
    }

    @Test
    void adminCannotSubmitButCanList() {
        submit(alice);
        Account admin = account(AccountType.Admin);

        PermissionDeniedException e = assertThrows(PermissionDeniedException.class, () -> submit(admin));

        assertEquals(Permission.SUBMIT_SOURCE_REPORT, e.getPermission());
        assertEquals(AccountType.Admin, e.getAccountType());
        assertEquals("Admin accounts are not allowed to submit source report", e.getMessage());
        assertEquals(1, service.listReports(admin).size());
        assertEquals(2, service.nextReportNumber(alice));
    }

    @Test
    void nobodyLoggedInIsDenied() {
        PermissionDeniedException submitDenied = assertThrows(PermissionDeniedException.class,
                () -> submit(null));
        PermissionDeniedException listDenied = assertThrows(PermissionDeniedException.class,
                () -> service.listReports(null));

        assertNull(submitDenied.getAccountType());
        assertEquals("You must be logged in to submit source report", submitDenied.getMessage());
        assertEquals(Permission.VIEW_SOURCE_REPORTS, listDenied.getPermission());
        assertTrue(reports.findAll().isEmpty());
    }

    @Test
    void nextReportNumberNeedsSubmitPermission() {
        submit(alice);
        Account admin = account(AccountType.Admin);

        PermissionDeniedException adminDenied = assertThrows(PermissionDeniedException.class,
                () -> service.nextReportNumber(admin));
        assertEquals(Permission.SUBMIT_SOURCE_REPORT, adminDenied.getPermission());
        SecurityEvent adminEvent = events.findNewestFirst(1).get(0);
        assertEquals(SecurityEventType.PERMISSION_DENIED, adminEvent.type());
        assertEquals(admin.getId(), adminEvent.actor());
        assertEquals("Admin lacks SUBMIT_SOURCE_REPORT", adminEvent.detail());

        assertThrows(PermissionDeniedException.class, () -> service.nextReportNumber(null));
        SecurityEvent nobodyEvent = events.findNewestFirst(1).get(0);
        assertEquals(SecurityEventType.PERMISSION_DENIED, nobodyEvent.type());
        assertEquals("not logged in lacks SUBMIT_SOURCE_REPORT", nobodyEvent.detail());
        assertEquals(2, events.countAfter(SecurityEventType.PERMISSION_DENIED, "", Instant.EPOCH));

        assertEquals(2, service.nextReportNumber(account(AccountType.User)));
        assertEquals(2, events.findNewestFirst(10).size(), "allowed calls are not logged");
    }

    @Test
    void findReportByNumber() {
        WaterSourceReport first = submit(alice);
        submit(alice);

        assertEquals(Optional.of(first), service.findReport(account(AccountType.Admin), 1));
        assertEquals(Optional.empty(), service.findReport(alice, 3));
    }

    @Test
    void findReportNeedsViewPermission() {
        submit(alice);

        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> service.findReport(null, 1));

        assertEquals(Permission.VIEW_SOURCE_REPORTS, e.getPermission());
    }
}
