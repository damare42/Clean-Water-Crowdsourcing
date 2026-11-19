package model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A valid water source report that has not been stored yet and therefore has no
 * report number. Repositories turn drafts into {@link WaterSourceReport}s.
 *
 * @param reporter the id of the account filing the report
 * @param reportedAt when the report was filed; truncated to milliseconds
 */
public record WaterSourceReportDraft(String reporter, Instant reportedAt,
                                     double latitude, double longitude,
                                     WaterSourceType type, WaterSourceCondition condition) {

    /**
     * @throws IllegalArgumentException with a user-facing message if a field is invalid
     */
    public WaterSourceReportDraft {
        WaterSourceReport.validate(reporter, reportedAt, latitude, longitude, type, condition);
        reportedAt = reportedAt.truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * @param reportNumber the number assigned to the report
     * @return the stored report
     */
    public WaterSourceReport withNumber(int reportNumber) {
        return new WaterSourceReport(reportNumber, reporter, reportedAt, latitude, longitude,
                type, condition);
    }
}
