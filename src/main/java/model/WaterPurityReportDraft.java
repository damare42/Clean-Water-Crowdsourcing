package model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A valid water purity report that has not been stored yet and therefore has no
 * report number. Repositories turn drafts into {@link WaterPurityReport}s.
 *
 * @param reporter       the id of the account filing the report
 * @param reportedAt     when the report was filed; truncated to milliseconds
 * @param location       where the water was tested
 * @param condition      the overall condition of the water
 * @param virusPpm       virus concentration, parts per million, 0 to {@value Validation#MAX_PPM}
 * @param contaminantPpm contaminant concentration, parts per million, 0 to {@value Validation#MAX_PPM}
 */
public record WaterPurityReportDraft(String reporter, Instant reportedAt, GeoPoint location,
                                     WaterPurityCondition condition, double virusPpm,
                                     double contaminantPpm) {

    /**
     * @throws IllegalArgumentException with a user-facing message if a field is invalid
     */
    public WaterPurityReportDraft {
        WaterPurityReport.validate(reporter, reportedAt, location, condition, virusPpm, contaminantPpm);
        reportedAt = reportedAt.truncatedTo(ChronoUnit.MILLIS);
        virusPpm = virusPpm + 0.0;
        contaminantPpm = contaminantPpm + 0.0;
    }

    /**
     * @param reportNumber the number assigned to the report
     * @return the stored report
     */
    public WaterPurityReport withNumber(int reportNumber) {
        return new WaterPurityReport(reportNumber, reporter, reportedAt, location, condition,
                virusPpm, contaminantPpm);
    }
}
