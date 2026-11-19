package model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Created by Sean on 10/6/2016.
 * A report about a water source: who filed it and when, where the source is, what
 * kind it is and its condition. Instances are immutable, always valid, and compare
 * equal when all their fields are equal.
 * <p>
 * The time of the report is a single {@link Instant} (millisecond precision); date
 * and time are only split for display, in a given time zone.
 */
public final class WaterSourceReport implements Report {
    /** Format of {@link #getDate(ZoneId)}, e.g. 2016/10/11. */
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    /** Format of {@link #getTime(ZoneId)}, e.g. 9:05. */
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private final int reportNumber;
    private final String reporter;
    private final Instant reportedAt;
    private final double latitude;
    private final double longitude;
    private final WaterSourceType type;
    private final WaterSourceCondition condition;

    /**
     * Creates a report. New reports are normally created by
     * {@code model.service.ReportService#submit}, which lets the repository assign
     * the report number.
     *
     * @param reportedAt when the report was filed; truncated to milliseconds
     * @throws IllegalArgumentException if the report number is not positive, the
     *         reporter or time is missing, the latitude is outside [-90, 90], the
     *         longitude is outside [-180, 180], or the type or condition is missing
     */
    public WaterSourceReport(int reportNumber, String reporter, Instant reportedAt,
                             double latitude, double longitude,
                             WaterSourceType type, WaterSourceCondition condition) {
        if (reportNumber < 1) {
            throw new IllegalArgumentException("Report number must be positive: " + reportNumber);
        }
        validate(reporter, reportedAt, latitude, longitude, type, condition);
        this.reportNumber = reportNumber;
        this.reporter = reporter;
        this.reportedAt = reportedAt.truncatedTo(ChronoUnit.MILLIS);
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.condition = condition;
    }

    /**
     * Checks the fields of a report that does not have a number yet.
     *
     * @throws IllegalArgumentException with a user-facing message for the first invalid field
     */
    static void validate(String reporter, Instant reportedAt, double latitude, double longitude,
                         WaterSourceType type, WaterSourceCondition condition) {
        if (reporter == null || reporter.isBlank()) {
            throw new IllegalArgumentException("Reporter is required");
        }
        if (reportedAt == null) {
            throw new IllegalArgumentException("Report time is required");
        }
        if (!Validation.isValidLatitude(latitude)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_LATITUDE);
        }
        if (!Validation.isValidLongitude(longitude)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_LONGITUDE);
        }
        if (type == null) {
            throw new IllegalArgumentException(Validation.MSG_TYPE_REQUIRED);
        }
        if (condition == null) {
            throw new IllegalArgumentException(Validation.MSG_CONDITION_REQUIRED);
        }
    }

    @Override
    public int getReportNumber() {
        return reportNumber;
    }

    /**
     * @return the id of the account that filed the report
     */
    @Override
    public String getReporter() {
        return reporter;
    }

    /**
     * @return when the report was filed
     */
    @Override
    public Instant getReportedAt() {
        return reportedAt;
    }

    /**
     * @param zone the time zone to display the date in
     * @return the date the report was filed, formatted with {@link #DATE_FORMAT}
     */
    public String getDate(ZoneId zone) {
        return DATE_FORMAT.format(reportedAt.atZone(zone));
    }

    /**
     * @param zone the time zone to display the time in
     * @return the time the report was filed, formatted with {@link #TIME_FORMAT}
     */
    public String getTime(ZoneId zone) {
        return TIME_FORMAT.format(reportedAt.atZone(zone));
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public WaterSourceType getType() {
        return type;
    }

    public WaterSourceCondition getCondition() {
        return condition;
    }

    /**
     * @return where the water source is
     */
    @Override
    public GeoPoint getLocation() {
        return new GeoPoint(latitude, longitude);
    }

    /**
     * @return a short title, e.g. "Water source report 7"
     */
    @Override
    public String getTitle() {
        return "Water source report " + reportNumber;
    }

    /**
     * @param zone the time zone to display date and time in
     * @return every field of the report, labelled and formatted for display
     */
    @Override
    public List<ReportField> fields(ZoneId zone) {
        return List.of(
                new ReportField("Report number", String.valueOf(reportNumber)),
                new ReportField("Date", getDate(zone)),
                new ReportField("Time", getTime(zone)),
                new ReportField("Reporter", reporter),
                new ReportField("Type", type.name()),
                new ReportField("Condition", condition.name()),
                new ReportField("Latitude", GeoPoint.format(latitude)),
                new ReportField("Longitude", GeoPoint.format(longitude)));
    }

    /**
     * @param zone the time zone to display date and time in
     * @return a one-line description of the report
     */
    public String describe(ZoneId zone) {
        return "Date: " + getDate(zone)
                + " Time: " + getTime(zone)
                + " User: " + reporter
                + " Type: " + type
                + " Condition: " + condition
                + " Report Number: " + reportNumber
                + " Latitude: " + latitude
                + " Longitude: " + longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WaterSourceReport other)) {
            return false;
        }
        return reportNumber == other.reportNumber
                && Double.compare(latitude, other.latitude) == 0
                && Double.compare(longitude, other.longitude) == 0
                && reporter.equals(other.reporter)
                && reportedAt.equals(other.reportedAt)
                && type == other.type
                && condition == other.condition;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportNumber, reporter, reportedAt, latitude, longitude, type, condition);
    }

    /**
     * @return {@link #describe(ZoneId)} in the system default time zone
     */
    @Override
    public String toString() {
        return describe(ZoneId.systemDefault());
    }
}
