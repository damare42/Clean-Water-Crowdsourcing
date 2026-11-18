package model.service;

import model.Account;
import model.Permission;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceReportDraft;
import model.WaterSourceType;
import model.persistence.ReportRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Files and lists water source reports, checking the acting account's permissions.
 */
public final class ReportService {

    private static final Logger LOGGER = Logger.getLogger(ReportService.class.getName());

    private final ReportRepository reports;
    private final Clock clock;

    /**
     * @param reports where reports are stored
     * @param clock supplies the time stamped on new reports
     */
    public ReportService(ReportRepository reports, Clock clock) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Files a report, stamped with the current time, under the next report number.
     *
     * @param reporter the logged-in account filing the report
     * @return the stored report
     * @throws PermissionDeniedException if the reporter is null or may not submit reports
     * @throws IllegalArgumentException with a user-facing message if the data is
     *         invalid; nothing is stored in that case
     */
    public WaterSourceReport submit(Account reporter, double latitude, double longitude,
                                    WaterSourceType type, WaterSourceCondition condition) {
        Authorization.require(reporter, Permission.SUBMIT_SOURCE_REPORT);
        WaterSourceReport report = reports.add(new WaterSourceReportDraft(reporter.getId(),
                clock.instant(), latitude, longitude, type, condition));
        LOGGER.log(Level.INFO, "Report {0} filed by {1}",
                new Object[]{report.getReportNumber(), reporter.getId()});
        return report;
    }

    /**
     * @param viewer the logged-in account
     * @return all reports in ascending report-number order
     * @throws PermissionDeniedException if the viewer is null or may not view reports
     */
    public List<WaterSourceReport> listReports(Account viewer) {
        Authorization.require(viewer, Permission.VIEW_SOURCE_REPORTS);
        return reports.findAll();
    }

    /**
     * @param viewer the logged-in account
     * @param reportNumber the number of the report
     * @return the report, or empty if there is none with this number
     * @throws PermissionDeniedException if the viewer is null or may not view reports
     */
    public Optional<WaterSourceReport> findReport(Account viewer, int reportNumber) {
        Authorization.require(viewer, Permission.VIEW_SOURCE_REPORTS);
        return reports.findByNumber(reportNumber);
    }

    /**
     * @return the number the next submitted report will get, if no other report is
     *         submitted first
     */
    public int nextReportNumber() {
        return reports.nextReportNumber();
    }
}
