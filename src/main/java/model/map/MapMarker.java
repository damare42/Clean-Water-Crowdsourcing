package model.map;

import model.GeoPoint;
import model.ReportField;
import model.WaterSourceReport;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * A pin on the map: where it is, what it stands for, and the text of its popup.
 * The {@code kind} and {@code id} together identify the report behind the pin, so
 * the map can ask for its details (other kinds of reports can get their own kind).
 *
 * @param kind    what the pin stands for, e.g. {@link #KIND_SOURCE}
 * @param id      the number of the report, unique within its kind
 * @param position where the pin is
 * @param title   the heading of the popup
 * @param fields  the labelled values shown in the popup
 */
public record MapMarker(String kind, int id, GeoPoint position, String title, List<ReportField> fields) {

    /** Kind of the markers made by {@link #forSourceReport}. */
    public static final String KIND_SOURCE = "source";

    public MapMarker {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(title, "title");
        fields = List.copyOf(fields);
    }

    /**
     * @param report the report to show
     * @param zone   the time zone for the report's date and time
     * @return a marker at the report's location whose popup shows all its fields
     */
    public static MapMarker forSourceReport(WaterSourceReport report, ZoneId zone) {
        return new MapMarker(KIND_SOURCE, report.getReportNumber(), report.getLocation(),
                report.getTitle(), report.fields(zone));
    }

    /**
     * @return markers for all the reports, in the same order
     */
    public static List<MapMarker> forSourceReports(List<WaterSourceReport> reports, ZoneId zone) {
        return reports.stream().map(r -> forSourceReport(r, zone)).toList();
    }
}
