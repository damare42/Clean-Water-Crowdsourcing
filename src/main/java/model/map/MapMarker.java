package model.map;

import model.GeoPoint;
import model.Report;
import model.ReportField;
import model.WaterPurityReport;
import model.WaterSourceReport;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * A pin on the map: where it is, what it stands for, and the text of its popup.
 * The {@code kind} and {@code id} together identify the report behind the pin, so
 * the map can ask for its details; the map page also draws each kind differently.
 *
 * @param kind    what the pin stands for: {@link #KIND_SOURCE} or {@link #KIND_PURITY}
 * @param id      the number of the report, unique within its kind
 * @param position where the pin is
 * @param title   the heading of the popup
 * @param fields  the labelled values shown in the popup
 */
public record MapMarker(String kind, int id, GeoPoint position, String title, List<ReportField> fields) {

    /** Kind of the markers for water source reports. */
    public static final String KIND_SOURCE = "source";
    /** Kind of the markers for water purity reports. */
    public static final String KIND_PURITY = "purity";

    public MapMarker {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(title, "title");
        fields = List.copyOf(fields);
    }

    /**
     * @param report a water source or water purity report
     * @return the kind of marker that stands for it
     * @throws IllegalArgumentException for any other kind of report
     */
    public static String kindOf(Report report) {
        if (report instanceof WaterSourceReport) {
            return KIND_SOURCE;
        }
        if (report instanceof WaterPurityReport) {
            return KIND_PURITY;
        }
        throw new IllegalArgumentException("No marker kind for " + report);
    }

    /**
     * @param report the report to show
     * @param zone   the time zone for the report's date and time
     * @return a marker at the report's location whose popup shows all its fields
     */
    public static MapMarker forReport(Report report, ZoneId zone) {
        return new MapMarker(kindOf(report), report.getReportNumber(), report.getLocation(),
                report.getTitle(), report.fields(zone));
    }

    /**
     * @return markers for all the reports, in the same order
     */
    public static List<MapMarker> forReports(List<? extends Report> reports, ZoneId zone) {
        return reports.stream().map(r -> forReport(r, zone)).toList();
    }

}
