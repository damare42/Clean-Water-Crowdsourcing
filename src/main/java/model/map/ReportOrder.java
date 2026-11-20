package model.map;

import model.GeoPoint;
import model.Report;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The order of the report list beside the map, and the distance shown on each report:
 * newest first, or nearest first when the approximate location of this computer is known.
 */
public final class ReportOrder {

    /** Newest first; reports filed at the same instant by number, highest first. */
    private static final Comparator<Report> NEWEST_FIRST = Comparator.comparing(Report::getReportedAt)
            .thenComparing(Report::getReportNumber).reversed();

    private ReportOrder() {
    }

    /**
     * @param reports the reports, in any order
     * @return a new list of the reports, newest first
     */
    public static List<Report> newestFirst(Collection<? extends Report> reports) {
        List<Report> sorted = new ArrayList<>(reports);
        sorted.sort(NEWEST_FIRST);
        return sorted;
    }

    /**
     * @param reports the reports, in any order
     * @param from    where the distances are measured from
     * @return a new list of the reports, nearest to {@code from} first (great-circle
     * distance); equally distant reports newest first
     */
    public static List<Report> nearestFirst(Collection<? extends Report> reports, GeoPoint from) {
        Objects.requireNonNull(from, "from");
        List<Report> sorted = new ArrayList<>(reports);
        sorted.sort(Comparator.comparingDouble((Report report) -> from.distanceKm(report.getLocation()))
                .thenComparing(NEWEST_FIRST));
        return sorted;
    }

    /**
     * @param reports the reports, in any order
     * @param from    the approximate location of this computer, or null if not known
     * @return {@link #nearestFirst} if a location is known, else {@link #newestFirst}
     */
    public static List<Report> forList(Collection<? extends Report> reports, GeoPoint from) {
        return from == null ? newestFirst(reports) : nearestFirst(reports, from);
    }

    /**
     * A distance for display, e.g. "3.2 km away" (one decimal below 10 km) or
     * "1,234 km away" (whole kilometres from 10 km).
     *
     * @param km a distance in kilometres, at least 0
     * @return the distance as text
     * @throws IllegalArgumentException if {@code km} is negative, NaN or infinite
     */
    public static String distanceText(double km) {
        if (!(km >= 0) || Double.isInfinite(km)) {
            throw new IllegalArgumentException("Not a distance: " + km);
        }
        if (km < 0.05) {
            return "Less than 0.1 km away";
        }
        if (km < 9.95) {
            return String.format(Locale.ROOT, "%.1f km away", km);
        }
        return String.format(Locale.ROOT, "%,d km away", Math.round(km));
    }

    /**
     * @param report a report
     * @param from   where the distance is measured from
     * @return how far the report is from {@code from}, as {@link #distanceText} writes it
     */
    public static String distanceText(Report report, GeoPoint from) {
        return distanceText(from.distanceKm(report.getLocation()));
    }
}
