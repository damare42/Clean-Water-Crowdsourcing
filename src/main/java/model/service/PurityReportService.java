package model.service;

import model.Account;
import model.GeoPoint;
import model.MonthlyAverage;
import model.Permission;
import model.PurityHistory;
import model.PurityMetric;
import model.Validation;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterPurityReportDraft;
import model.persistence.PurityReportRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.Month;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Files and lists water purity reports and computes the historical purity chart,
 * checking the acting account's permissions.
 */
public final class PurityReportService {

    private static final Logger LOGGER = Logger.getLogger(PurityReportService.class.getName());

    private final PurityReportRepository reports;
    private final Clock clock;
    private final ZoneId zone;
    private final AccessControl access;

    /**
     * @param reports where reports are stored
     * @param clock supplies the time stamped on new reports
     * @param zone the time zone whose calendar months the history uses (the user's zone)
     * @param access checks permissions and records denials
     */
    public PurityReportService(PurityReportRepository reports, Clock clock, ZoneId zone,
                               AccessControl access) {
        this.reports = Objects.requireNonNull(reports, "reports");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.access = Objects.requireNonNull(access, "access");
    }

    /**
     * Files a purity report, stamped with the current time, under the next number.
     *
     * @param reporter the logged-in account; needs {@link Permission#SUBMIT_PURITY_REPORT}
     * @return the stored report
     * @throws PermissionDeniedException if the reporter may not submit purity reports
     * @throws IllegalArgumentException with a user-facing message if the data is
     *         invalid; nothing is stored in that case
     */
    public WaterPurityReport submit(Account reporter, GeoPoint location, WaterPurityCondition condition,
                                    double virusPpm, double contaminantPpm) {
        access.require(reporter, Permission.SUBMIT_PURITY_REPORT);
        WaterPurityReport report = reports.add(new WaterPurityReportDraft(reporter.getId(),
                clock.instant(), location, condition, virusPpm, contaminantPpm));
        LOGGER.log(Level.INFO, "Purity report {0} filed by {1}",
                new Object[]{report.getReportNumber(), reporter.getId()});
        return report;
    }

    /**
     * @param viewer the logged-in account; needs {@link Permission#VIEW_PURITY_REPORTS}
     * @return all purity reports in ascending report-number order
     * @throws PermissionDeniedException if the viewer may not view purity reports
     */
    public List<WaterPurityReport> listReports(Account viewer) {
        access.require(viewer, Permission.VIEW_PURITY_REPORTS);
        return reports.findAll();
    }

    /**
     * @param viewer the logged-in account; needs {@link Permission#VIEW_PURITY_REPORTS}
     * @param reportNumber the number of the report
     * @return the report, or empty if there is none with this number
     * @throws PermissionDeniedException if the viewer may not view purity reports
     */
    public Optional<WaterPurityReport> findReport(Account viewer, int reportNumber) {
        access.require(viewer, Permission.VIEW_PURITY_REPORTS);
        return reports.findByNumber(reportNumber);
    }

    /**
     * @return the number the next submitted purity report will get, if no other
     *         report is submitted first
     */
    public int nextReportNumber() {
        return reports.nextReportNumber();
    }

    /**
     * @return the time zone whose calendar months {@link #history} uses
     */
    public ZoneId getZone() {
        return zone;
    }

    /**
     * The monthly averages of one purity value over the reports within a radius of a
     * location, for one calendar year in this service's time zone.
     *
     * @param viewer the logged-in account; needs {@link Permission#VIEW_PURITY_HISTORY}
     * @param center the location
     * @param radiusKm how far from the location a report may be, in kilometres
     * @param year the calendar year
     * @param metric the value to average
     * @return the averages; months without reports are left out
     * @throws PermissionDeniedException if the viewer may not view the purity history
     * @throws IllegalArgumentException with a user-facing message for an invalid
     *         radius or year
     */
    public PurityHistory history(Account viewer, GeoPoint center, double radiusKm, int year,
                                 PurityMetric metric) {
        access.require(viewer, Permission.VIEW_PURITY_HISTORY);
        checkHistoryQuery(center, radiusKm, year, metric);
        Instant from = Year.of(year).atDay(1).atStartOfDay(zone).toInstant();
        Instant until = Year.of(year + 1).atDay(1).atStartOfDay(zone).toInstant();
        List<WaterPurityReport> candidates = reports.findReportedBetween(from, until);
        return new PurityHistory(center, radiusKm, year, metric,
                monthlyAverages(candidates, center, radiusKm, year, metric, zone));
    }

    private static void checkHistoryQuery(GeoPoint center, double radiusKm, int year, PurityMetric metric) {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(metric, "metric");
        if (!Validation.isValidRadiusKm(radiusKm)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_RADIUS);
        }
        if (!Validation.isValidYear(year)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_YEAR);
        }
    }

    /**
     * Averages one purity value per calendar month. Only reports filed in the given
     * year (in the given zone) and at most {@code radiusKm} from the center (great-circle
     * distance, see {@link GeoPoint#distanceKm}) count.
     *
     * @param reports any reports; others are ignored
     * @return one average per month that has at least one matching report, in
     *         calendar order
     */
    public static List<MonthlyAverage> monthlyAverages(Collection<WaterPurityReport> reports,
                                                       GeoPoint center, double radiusKm, int year,
                                                       PurityMetric metric, ZoneId zone) {
        checkHistoryQuery(center, radiusKm, year, metric);
        Objects.requireNonNull(zone, "zone");
        Map<Month, double[]> sums = new EnumMap<>(Month.class);
        for (WaterPurityReport report : reports) {
            var local = report.getReportedAt().atZone(zone);
            if (local.getYear() != year || report.getLocation().distanceKm(center) > radiusKm) {
                continue;
            }
            double[] sum = sums.computeIfAbsent(local.getMonth(), m -> new double[2]);
            sum[0] += metric.measure(report);
            sum[1]++;
        }
        List<MonthlyAverage> result = new ArrayList<>();
        sums.forEach((month, sum) -> result.add(new MonthlyAverage(month, sum[0] / sum[1], (int) sum[1])));
        return List.copyOf(result);
    }
}
