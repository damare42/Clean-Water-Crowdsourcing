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
    private final AccessControl access;

    /**
     * @param reports where reports are stored
     * @param clock supplies the time stamped on new reports
     * @param access checks permissions and records denials
     */
    public ReportService(ReportRepository reports, Clock clock, AccessControl access) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.access = Objects.requireNonNull(access, "access");
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
        access.require(reporter, Permission.SUBMIT_SOURCE_REPORT);
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
        access.require(viewer, Permission.VIEW_SOURCE_REPORTS);
        return reports.findAll();
    }

    /**
     * @param viewer the logged-in account
     * @param reportNumber the number of the report
     * @return the report, or empty if there is none with this number
     * @throws PermissionDeniedException if the viewer is null or may not view reports
     */
    public Optional<WaterSourceReport> findReport(Account viewer, int reportNumber) {
        access.require(viewer, Permission.VIEW_SOURCE_REPORTS);
        return reports.findByNumber(reportNumber);
    }

    /**
     * @param actor the logged-in account; needs {@link Permission#SUBMIT_SOURCE_REPORT}
     * @return the number the next submitted report will get, if no other report is
     *         submitted first
     * @throws PermissionDeniedException if the actor may not submit source reports
     */
    public int nextReportNumber(Account actor) {
        access.require(actor, Permission.SUBMIT_SOURCE_REPORT);
        return reports.nextReportNumber();
    }
}
