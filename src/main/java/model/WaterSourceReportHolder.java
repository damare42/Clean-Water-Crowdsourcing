package model;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Created by Sean on 10/11/2016.
 * Holds all water source reports, ordered by report number, and hands out report
 * numbers. The next number is always one more than the highest stored number.
 */
public final class WaterSourceReportHolder {
    private static final SortedMap<Integer, WaterSourceReport> REPORTS = new TreeMap<>();

    private WaterSourceReportHolder() {
    }

    /**
     * @return the number the next submitted report will get
     */
    public static synchronized int nextReportNumber() {
        return REPORTS.isEmpty() ? 1 : REPORTS.lastKey() + 1;
    }

    /**
     * Creates a report with the next report number and stores it.
     *
     * @return the stored report
     * @throws IllegalArgumentException if the report data is invalid; nothing is stored
     */
    public static synchronized WaterSourceReport submit(String user, String date, String time,
                                                        double latitude, double longitude,
                                                        WaterSourceType type,
                                                        WaterSourceCondition condition) {
        WaterSourceReport report = new WaterSourceReport(nextReportNumber(), user, date, time,
                latitude, longitude, type, condition);
        REPORTS.put(report.getReportNumber(), report);
        return report;
    }

    /**
     * Stores an existing report under its own report number.
     *
     * @throws IllegalArgumentException if a report with that number is already stored
     */
    public static synchronized void addReport(WaterSourceReport report) {
        if (REPORTS.containsKey(report.getReportNumber())) {
            throw new IllegalArgumentException(
                    "Report number already in use: " + report.getReportNumber());
        }
        REPORTS.put(report.getReportNumber(), report);
    }

    /**
     * @return an unmodifiable snapshot of all reports in ascending report-number order
     */
    public static synchronized List<WaterSourceReport> getReports() {
        return List.copyOf(REPORTS.values());
    }

    /**
     * Removes all reports, so numbering starts again at 1.
     */
    static synchronized void clear() {
        REPORTS.clear();
    }
}
