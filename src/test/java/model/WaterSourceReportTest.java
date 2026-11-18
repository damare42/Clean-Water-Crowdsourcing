package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation tests for the current behaviour of {@link WaterSourceReport}.
 *
 * <p>The report counter is a private static field that cannot be reset, so numbering
 * is asserted relative to previously created reports.</p>
 */
class WaterSourceReportTest {

    @BeforeEach
    void clearStaticState() {
        WaterSourceReport.reportList.clear();
        WaterSourceReportHolder.reports.clear();
    }

    private static WaterSourceReport report(String user) {
        return new WaterSourceReport(user, "10/11/2016", "12:30", "-84.39", "33.77",
                WaterSourceType.Lake, WaterSourceCondition.Clear);
    }

    @Test
    void constructorSetsFields() {
        WaterSourceReport report = new WaterSourceReport("alice", "10/11/2016", "12:30",
                "-84.39", "33.77", WaterSourceType.River, WaterSourceCondition.Muddy);

        assertEquals("10/11/2016", report.getDate());
        assertEquals("12:30", report.getTime());
        assertEquals("-84.39", report.getLongitude());
        assertEquals("33.77", report.getLatitude());
        assertEquals(WaterSourceType.River, report.getType());
        assertEquals(WaterSourceCondition.Muddy, report.getCondition());
        assertTrue(report.getReportNumber() > 0);
    }

    @Test
    void reportNumbersIncrementByOne() {
        WaterSourceReport first = report("alice");
        WaterSourceReport second = report("bob");
        WaterSourceReport third = report("carol");

        assertEquals(first.getReportNumber() + 1, second.getReportNumber());
        assertEquals(second.getReportNumber() + 1, third.getReportNumber());
    }

    @Test
    void toStringContainsAllFields() {
        WaterSourceReport report = new WaterSourceReport("alice", "10/11/2016", "12:30",
                "-84.39", "33.77", WaterSourceType.Well, WaterSourceCondition.Clear);

        String text = report.toString();

        assertTrue(text.contains("Date: 10/11/2016"), text);
        assertTrue(text.contains("Time: 12:30"), text);
        assertTrue(text.contains("User: alice"), text);
        assertTrue(text.contains("Type: Well"), text);
        assertTrue(text.contains("Condition: Clear"), text);
        assertTrue(text.contains("Report Number: " + report.getReportNumber()), text);
        assertTrue(text.contains("Longitude: -84.39"), text);
        assertTrue(text.contains("Latitude: 33.77"), text);
    }

    @Test
    void constructorDoesNotRegisterReport() {
        report("alice");

        assertTrue(WaterSourceReport.reportList.isEmpty());
        assertTrue(WaterSourceReportHolder.reports.isEmpty());
    }

    @Test
    void getReportListReturnsSharedStaticMap() {
        assertSame(WaterSourceReport.reportList, report("alice").getReportList());
    }
}
