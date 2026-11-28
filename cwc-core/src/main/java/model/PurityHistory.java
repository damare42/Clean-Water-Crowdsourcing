package model;

import java.util.List;
import java.util.Objects;

/**
 * The monthly averages of one purity value for the reports near a location in one
 * year, as shown by the history chart.
 *
 * @param center   the location the reports are near
 * @param radiusKm how far from the center (great-circle distance) a report may be
 * @param year     the calendar year
 * @param metric   which value is averaged
 * @param months   the averages in calendar order; months without reports are left out
 */
public record PurityHistory(GeoPoint center, double radiusKm, int year, PurityMetric metric,
                            List<MonthlyAverage> months) {

    public PurityHistory {
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(metric, "metric");
        months = List.copyOf(months);
    }

    /**
     * @return true if no report matched, so there is nothing to draw
     */
    public boolean isEmpty() {
        return months.isEmpty();
    }
}
