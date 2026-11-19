package model;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * What every kind of report (water source or water purity) has in common: a number,
 * who filed it and when, where it is, and its fields for display. Lets the detail
 * window and the map show any kind of report the same way.
 */
public interface Report {

    /**
     * @return the report number, unique within its kind of report
     */
    int getReportNumber();

    /**
     * @return the id of the account that filed the report
     */
    String getReporter();

    /**
     * @return when the report was filed
     */
    Instant getReportedAt();

    /**
     * @return where the report is about
     */
    GeoPoint getLocation();

    /**
     * @return a short title, e.g. "Water source report 7"
     */
    String getTitle();

    /**
     * @param zone the time zone to display date and time in
     * @return every field of the report, labelled and formatted for display
     */
    List<ReportField> fields(ZoneId zone);
}
