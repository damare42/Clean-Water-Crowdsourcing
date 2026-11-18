package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterSourceReportHolderTest {

    @BeforeEach
    void clearStaticState() {
        WaterSourceReportHolder.clear();
    }

    private static WaterSourceReport submit(String user) {
        return WaterSourceReportHolder.submit(user, "2016/10/11", "12:30", 33.77, -84.39,
                WaterSourceType.Pond, WaterSourceCondition.Waste);
    }

    private static WaterSourceReport report(int number) {
        return new WaterSourceReport(number, "alice", "2016/10/11", "12:30", 33.77, -84.39,
                WaterSourceType.Pond, WaterSourceCondition.Waste);
    }

    private static List<Integer> numbers() {
        return WaterSourceReportHolder.getReports().stream()
                .map(WaterSourceReport::getReportNumber).toList();
    }

    @Test
    void numberingStartsAtOne() {
        assertEquals(1, WaterSourceReportHolder.nextReportNumber());
        assertTrue(WaterSourceReportHolder.getReports().isEmpty());
    }

    @Test
    void submitAssignsSequentialNumbers() {
        WaterSourceReport first = submit("alice");
        WaterSourceReport second = submit("bob");
        WaterSourceReport third = submit("carol");

        assertEquals(1, first.getReportNumber());
        assertEquals(2, second.getReportNumber());
        assertEquals(3, third.getReportNumber());
        assertEquals(4, WaterSourceReportHolder.nextReportNumber());
        assertEquals(List.of(first, second, third), WaterSourceReportHolder.getReports());
    }

    @Test
    void clearResetsNumbering() {
        submit("alice");
        submit("bob");

        WaterSourceReportHolder.clear();

        assertEquals(1, submit("carol").getReportNumber());
    }

    @Test
    void submitWithInvalidDataStoresNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> WaterSourceReportHolder.submit("alice", "d", "t", 91, 0,
                        WaterSourceType.Lake, WaterSourceCondition.Clear));
        assertThrows(IllegalArgumentException.class,
                () -> WaterSourceReportHolder.submit("alice", "d", "t", 0, 0,
                        null, WaterSourceCondition.Clear));

        assertTrue(WaterSourceReportHolder.getReports().isEmpty());
        assertEquals(1, WaterSourceReportHolder.nextReportNumber());
    }

    @Test
    void addReportKeysByReportNumber() {
        WaterSourceReport report = report(5);

        WaterSourceReportHolder.addReport(report);

        assertSame(report, WaterSourceReportHolder.getReports().get(0));
    }

    @Test
    void addReportRejectsDuplicateNumber() {
        WaterSourceReport original = report(5);
        WaterSourceReportHolder.addReport(original);

        assertThrows(IllegalArgumentException.class, () -> WaterSourceReportHolder.addReport(report(5)));
        assertEquals(List.of(original), WaterSourceReportHolder.getReports());
    }

    @Test
    void reportsAreOrderedByNumberRegardlessOfInsertionOrder() {
        WaterSourceReportHolder.addReport(report(10));
        WaterSourceReportHolder.addReport(report(2));
        WaterSourceReportHolder.addReport(report(7));

        assertEquals(List.of(2, 7, 10), numbers());
    }

    @Test
    void nextNumberIsOneMoreThanHighest() {
        WaterSourceReportHolder.addReport(report(2));
        WaterSourceReportHolder.addReport(report(10));

        assertEquals(11, WaterSourceReportHolder.nextReportNumber());
        assertEquals(11, submit("alice").getReportNumber());
        assertEquals(List.of(2, 10, 11), numbers());
    }

    @Test
    void getReportsIsUnmodifiableSnapshot() {
        submit("alice");
        List<WaterSourceReport> snapshot = WaterSourceReportHolder.getReports();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(report(9)));
        submit("bob");
        assertEquals(1, snapshot.size());
    }
}
