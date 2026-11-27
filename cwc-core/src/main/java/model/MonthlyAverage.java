package model;

import java.time.Month;
import java.util.Objects;

/**
 * The average of one purity value over the reports of one month.
 *
 * @param month       the month
 * @param average     the average value in parts per million
 * @param reportCount how many reports the average is taken over (at least 1)
 */
public record MonthlyAverage(Month month, double average, int reportCount) {

    public MonthlyAverage {
        Objects.requireNonNull(month, "month");
        if (reportCount < 1) {
            throw new IllegalArgumentException("An average needs at least one report: " + reportCount);
        }
    }
}
