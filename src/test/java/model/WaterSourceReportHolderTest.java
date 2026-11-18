package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Characterisation tests for the current behaviour of {@link WaterSourceReportHolder}.
 */
class WaterSourceReportHolderTest {

    @BeforeEach
    void clearStaticState() {
        WaterSourceReportHolder.reports.clear();
        WaterSourceReport.reportList.clear();
    }

    private static WaterSourceReport report() {
        return new WaterSourceReport("alice", "10/11/2016", "12:30", "-84.39", "33.77",
                WaterSourceType.Pond, WaterSourceCondition.Waste);
    }

    @Test
    void addReportStoresReportUnderGivenNumber() {
        WaterSourceReport report = report();

        WaterSourceReportHolder.addReport(report.getReportNumber(), report);

        assertEquals(1, WaterSourceReportHolder.reports.size());
        assertSame(report, WaterSourceReportHolder.reports.get(report.getReportNumber()));
    }

    @Test
    void addReportStoresMultipleReports() {
        WaterSourceReport first = report();
        WaterSourceReport second = report();

        WaterSourceReportHolder.addReport(first.getReportNumber(), first);
        WaterSourceReportHolder.addReport(second.getReportNumber(), second);

        assertEquals(2, WaterSourceReportHolder.reports.size());
        assertSame(first, WaterSourceReportHolder.reports.get(first.getReportNumber()));
        assertSame(second, WaterSourceReportHolder.reports.get(second.getReportNumber()));
    }

    @Test
    void addReportWithExistingKeyReplacesReport() {
        WaterSourceReport first = report();
        WaterSourceReport second = report();

        WaterSourceReportHolder.addReport(7, first);
        WaterSourceReportHolder.addReport(7, second);

        assertEquals(1, WaterSourceReportHolder.reports.size());
        assertSame(second, WaterSourceReportHolder.reports.get(7));
    }

    @Test
    void addReportUsesCallerSuppliedKeyNotReportNumber() {
        // Current behaviour: the key is whatever the caller passes; it is not checked
        // against the report's own number.
        WaterSourceReport report = report();

        WaterSourceReportHolder.addReport(-1, report);

        assertSame(report, WaterSourceReportHolder.reports.get(-1));
        assertNull(WaterSourceReportHolder.reports.get(report.getReportNumber()));
    }

    @Test
    void addReportDoesNotTouchWaterSourceReportList() {
        WaterSourceReport report = report();

        WaterSourceReportHolder.addReport(report.getReportNumber(), report);

        assertEquals(0, WaterSourceReport.reportList.size());
    }
}
