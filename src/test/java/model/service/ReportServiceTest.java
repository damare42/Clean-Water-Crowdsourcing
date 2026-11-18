package model.service;

import model.Account;
import model.AccountType;
import model.Permission;
import model.Validation;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
import model.persistence.InMemoryReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {

    private static final Instant NOW = Instant.parse("2016-10-11T12:30:00Z");

    private InMemoryReportRepository reports;
    private ReportService service;
    private Account alice;

    @BeforeEach
    void setUp() {
        reports = new InMemoryReportRepository();
        service = new ReportService(reports, Clock.fixed(NOW, ZoneOffset.UTC));
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
        assertEquals(1, service.nextReportNumber());
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
        assertEquals(3, service.nextReportNumber());
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
        assertEquals(1, service.nextReportNumber());
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
        assertEquals(2, service.nextReportNumber());
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
}
