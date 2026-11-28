package model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * A report about the purity of water at a location: who filed it and when, where,
 * the overall condition, and the measured virus and contaminant concentrations in
 * parts per million (PPM). Instances are immutable, always valid, and compare equal
 * when all their fields are equal.
 */
public final class WaterPurityReport implements Report {

    private final int reportNumber;
    private final String reporter;
    private final Instant reportedAt;
    private final GeoPoint location;
    private final WaterPurityCondition condition;
    private final double virusPpm;
    private final double contaminantPpm;

    /**
     * Creates a report. New reports are normally created by
     * {@code model.service.PurityReportService#submit}, which lets the repository
     * assign the report number.
     *
     * @param reportedAt when the report was filed; truncated to milliseconds
     * @throws IllegalArgumentException if the report number is not positive or any
     *         other field is invalid (see {@link WaterPurityReportDraft})
     */
    public WaterPurityReport(int reportNumber, String reporter, Instant reportedAt, GeoPoint location,
                             WaterPurityCondition condition, double virusPpm, double contaminantPpm) {
        if (reportNumber < 1) {
            throw new IllegalArgumentException("Report number must be positive: " + reportNumber);
        }
        validate(reporter, reportedAt, location, condition, virusPpm, contaminantPpm);
        this.reportNumber = reportNumber;
        this.reporter = reporter;
        this.reportedAt = reportedAt.truncatedTo(ChronoUnit.MILLIS);
        this.location = location;
        this.condition = condition;
        // + 0.0 turns -0.0 into 0.0
        this.virusPpm = virusPpm + 0.0;
        this.contaminantPpm = contaminantPpm + 0.0;
    }

    /**
     * Checks the fields of a report that does not have a number yet.
     *
     * @throws IllegalArgumentException with a user-facing message for the first invalid field
     */
    static void validate(String reporter, Instant reportedAt, GeoPoint location,
                         WaterPurityCondition condition, double virusPpm, double contaminantPpm) {
        if (reporter == null || reporter.isBlank()) {
            throw new IllegalArgumentException("Reporter is required");
        }
        if (reportedAt == null) {
            throw new IllegalArgumentException("Report time is required");
        }
        if (location == null) {
            throw new IllegalArgumentException("Location is required");
        }
        if (condition == null) {
            throw new IllegalArgumentException(Validation.MSG_PURITY_CONDITION_REQUIRED);
        }
        if (!Validation.isValidPpm(virusPpm)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_VIRUS_PPM);
        }
        if (!Validation.isValidPpm(contaminantPpm)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_CONTAMINANT_PPM);
        }
    }

    /**
     * Formats a PPM value as a plain decimal number without exponent or trailing
     * zeros, e.g. "0.5", "120", "1000000".
     *
     * @param ppm a valid PPM value
     * @return the formatted value
     */
    public static String formatPpm(double ppm) {
        if (!Double.isFinite(ppm)) {
            throw new IllegalArgumentException("Not a PPM value: " + ppm);
        }
        BigDecimal value = BigDecimal.valueOf(ppm);
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    @Override
    public int getReportNumber() {
        return reportNumber;
    }

    @Override
    public String getReporter() {
        return reporter;
    }

    @Override
    public Instant getReportedAt() {
        return reportedAt;
    }

    @Override
    public GeoPoint getLocation() {
        return location;
    }

    public WaterPurityCondition getCondition() {
        return condition;
    }

    /**
     * @return the virus concentration in parts per million
     */
    public double getVirusPpm() {
        return virusPpm;
    }

    /**
     * @return the contaminant concentration in parts per million
     */
    public double getContaminantPpm() {
        return contaminantPpm;
    }

    @Override
    public String getTitle() {
        return "Water purity report " + reportNumber;
    }

    @Override
    public List<ReportField> fields(ZoneId zone) {
        return List.of(
                new ReportField("Report number", String.valueOf(reportNumber)),
                new ReportField("Date", WaterSourceReport.DATE_FORMAT.format(reportedAt.atZone(zone))),
                new ReportField("Time", WaterSourceReport.TIME_FORMAT.format(reportedAt.atZone(zone))),
                new ReportField("Reporter", reporter),
                new ReportField("Condition", condition.name()),
                new ReportField("Virus PPM", formatPpm(virusPpm)),
                new ReportField("Contaminant PPM", formatPpm(contaminantPpm)),
                new ReportField("Latitude", location.formattedLatitude()),
                new ReportField("Longitude", location.formattedLongitude()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WaterPurityReport other)) {
            return false;
        }
        return reportNumber == other.reportNumber
                && Double.compare(virusPpm, other.virusPpm) == 0
                && Double.compare(contaminantPpm, other.contaminantPpm) == 0
                && reporter.equals(other.reporter)
                && reportedAt.equals(other.reportedAt)
                && location.equals(other.location)
                && condition == other.condition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportNumber, reporter, reportedAt, location, condition, virusPpm, contaminantPpm);
    }

    @Override
    public String toString() {
        return getTitle() + " by " + reporter + " at " + location.formattedLatitude() + ", "
                + location.formattedLongitude() + ": " + condition + ", virus "
                + formatPpm(virusPpm) + " PPM, contaminant " + formatPpm(contaminantPpm) + " PPM";
    }
}
