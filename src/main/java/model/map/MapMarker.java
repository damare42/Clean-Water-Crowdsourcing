package model.map;

import model.GeoPoint;
import model.Report;
import model.ReportField;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterSourceCondition;
import model.WaterSourceReport;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * A pin on the map: where it is, what it stands for, how good the water is, and the
 * text of its popup. The {@code kind} and {@code id} together identify the report
 * behind the pin, so the map can ask for its details; the map page draws each kind
 * with its own shape and each {@link Status} in its own colour.
 *
 * @param kind    what the pin stands for: {@link #KIND_SOURCE} or {@link #KIND_PURITY}
 * @param id      the number of the report, unique within its kind
 * @param position where the pin is
 * @param title   the heading of the popup
 * @param fields  the labelled values shown in the popup
 * @param status  how good the water is, from the report's condition
 */
public record MapMarker(String kind, int id, GeoPoint position, String title, List<ReportField> fields,
                        Status status) {

    /** Kind of the markers for water source reports. */
    public static final String KIND_SOURCE = "source";
    /** Kind of the markers for water purity reports. */
    public static final String KIND_PURITY = "purity";

    /**
     * How good the water at a marker is. The map page colours markers by it; its
     * {@link #word()} is the only status text the page ever receives, so the page can
     * pick a style from a fixed list.
     */
    public enum Status {
        /** Drinkable: a Potable source or a Safe purity report. */
        SUCCESS("success"),
        /** Clear water (a source; not known to be drinkable). */
        INFO("info"),
        /** Usable after treatment: Muddy sources and Treatable purity reports. */
        WARNING("warning"),
        /** Not usable: Waste sources and Unsafe purity reports. */
        DANGER("danger"),
        /** No condition known. */
        UNKNOWN("unknown");

        private final String word;

        Status(String word) {
            this.word = word;
        }

        /**
         * @return the status as written to the map page: a lower-case ASCII word
         */
        public String word() {
            return word;
        }
    }

    public MapMarker {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        fields = List.copyOf(fields);
    }

    /**
     * A marker whose status is not known ({@link Status#UNKNOWN}).
     */
    public MapMarker(String kind, int id, GeoPoint position, String title, List<ReportField> fields) {
        this(kind, id, position, title, fields, Status.UNKNOWN);
    }

    /**
     * @param condition the condition of a water source or purity report, or null
     * @return the status that stands for it ({@link Status#UNKNOWN} for anything else)
     */
    public static Status statusOf(Object condition) {
        if (condition instanceof WaterSourceCondition source) {
            return switch (source) {
                case Potable -> Status.SUCCESS;
                case Clear -> Status.INFO;
                case Muddy -> Status.WARNING;
                case Waste -> Status.DANGER;
            };
        }
        if (condition instanceof WaterPurityCondition purity) {
            return switch (purity) {
                case Safe -> Status.SUCCESS;
                case Treatable -> Status.WARNING;
                case Unsafe -> Status.DANGER;
            };
        }
        return Status.UNKNOWN;
    }

    /**
     * @param report a water source or water purity report
     * @return the status of its condition
     */
    public static Status statusOf(Report report) {
        if (report instanceof WaterSourceReport source) {
            return statusOf(source.getCondition());
        }
        if (report instanceof WaterPurityReport purity) {
            return statusOf(purity.getCondition());
        }
        return Status.UNKNOWN;
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
     * @return a marker at the report's location, coloured by its condition, whose popup
     * shows all its fields
     */
    public static MapMarker forReport(Report report, ZoneId zone) {
        return new MapMarker(kindOf(report), report.getReportNumber(), report.getLocation(),
                report.getTitle(), report.fields(zone), statusOf(report));
    }

    /**
     * @return markers for all the reports, in the same order
     */
    public static List<MapMarker> forReports(List<? extends Report> reports, ZoneId zone) {
        return reports.stream().map(r -> forReport(r, zone)).toList();
    }

}
