package model.map;

import model.GeoPoint;
import model.Report;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportOrderTest {

    private static final Instant T0 = Instant.parse("2016-10-11T09:00:00Z");
    /** Atlanta, Georgia Tech area. */
    private static final GeoPoint HERE = new GeoPoint(33.7756, -84.3963);

    private static WaterSourceReport source(int number, long minutes, double lat, double lon) {
        return new WaterSourceReport(number, "alice", T0.plusSeconds(60 * minutes), lat, lon,
                WaterSourceType.Lake, WaterSourceCondition.Clear);
    }

    private static WaterPurityReport purity(int number, long minutes, double lat, double lon) {
        return new WaterPurityReport(number, "wendy", T0.plusSeconds(60 * minutes), new GeoPoint(lat, lon),
                WaterPurityCondition.Safe, 1, 2);
    }

    private static List<String> titles(List<Report> reports) {
        return reports.stream().map(Report::getTitle).toList();
    }

    private final WaterSourceReport far = source(1, 0, 40.7128, -74.006);   // New York, oldest
    private final WaterSourceReport near = source(2, 10, 33.78, -84.40);     // a few hundred metres
    private final WaterPurityReport middle = purity(1, 20, 33.95, -84.55);   // about 24 km
    private final WaterSourceReport newest = source(3, 30, 34.05, -84.10);   // about 41 km

    @Test
    void withoutALocationNewestComeFirst() {
        List<Report> sorted = ReportOrder.forList(List.of(far, near, middle, newest), null);

        assertEquals(List.of("Water source report 3", "Water purity report 1", "Water source report 2",
                "Water source report 1"), titles(sorted));
    }

    @Test
    void sameInstantIsOrderedByNumberHighestFirst() {
        WaterSourceReport a = source(4, 0, 1, 1);
        WaterSourceReport b = source(5, 0, 1, 1);

        assertEquals(List.of(b, a), ReportOrder.newestFirst(List.of(a, b)));
    }

    @Test
    void withALocationNearestComeFirst() {
        List<Report> sorted = ReportOrder.forList(List.of(far, newest, middle, near), HERE);

        assertEquals(List.of(near, middle, newest, far), sorted);
    }

    @Test
    void equallyDistantReportsAreNewestFirst() {
        WaterSourceReport older = source(7, 0, 33.8, -84.4);
        WaterPurityReport newer = purity(9, 5, 33.8, -84.4);

        assertEquals(List.of(newer, older), ReportOrder.nearestFirst(List.of(older, newer), HERE));
    }

    @Test
    void inputIsNotChanged() {
        List<Report> input = new java.util.ArrayList<>(List.of(far, near));

        ReportOrder.nearestFirst(input, HERE);
        ReportOrder.newestFirst(input);

        assertEquals(List.of(far, near), input);
    }

    @Test
    void emptyStaysEmpty() {
        assertEquals(List.of(), ReportOrder.forList(List.of(), HERE));
        assertEquals(List.of(), ReportOrder.forList(List.of(), null));
    }

    @Test
    void nearestFirstNeedsALocation() {
        assertThrows(NullPointerException.class, () -> ReportOrder.nearestFirst(List.of(near), null));
    }

    @Test
    void distanceTexts() {
        assertEquals("Less than 0.1 km away", ReportOrder.distanceText(0));
        assertEquals("Less than 0.1 km away", ReportOrder.distanceText(0.049));
        assertEquals("0.1 km away", ReportOrder.distanceText(0.05));
        assertEquals("3.2 km away", ReportOrder.distanceText(3.24));
        assertEquals("9.9 km away", ReportOrder.distanceText(9.94));
        assertEquals("10 km away", ReportOrder.distanceText(9.95));
        assertEquals("12 km away", ReportOrder.distanceText(12.4));
        assertEquals("1,234 km away", ReportOrder.distanceText(1234.4));
        assertEquals("20,015 km away", ReportOrder.distanceText(20015.1));
    }

    @Test
    void distanceOfAReport() {
        assertEquals("0.6 km away", ReportOrder.distanceText(near, HERE));
        assertEquals("1,199 km away", ReportOrder.distanceText(far, HERE));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void notADistance(double km) {
        assertThrows(IllegalArgumentException.class, () -> ReportOrder.distanceText(km));
    }
}
