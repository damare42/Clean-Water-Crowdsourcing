package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterSourceReportTest {

    private static WaterSourceReport report(double latitude, double longitude) {
        return new WaterSourceReport(1, "alice", "2016/10/11", "12:30", latitude, longitude,
                WaterSourceType.Lake, WaterSourceCondition.Clear);
    }

    @Test
    void constructorSetsFields() {
        WaterSourceReport report = new WaterSourceReport(3, "alice", "2016/10/11", "12:30",
                33.77, -84.39, WaterSourceType.River, WaterSourceCondition.Muddy);

        assertEquals(3, report.getReportNumber());
        assertEquals("alice", report.getUser());
        assertEquals("2016/10/11", report.getDate());
        assertEquals("12:30", report.getTime());
        assertEquals(33.77, report.getLatitude());
        assertEquals(-84.39, report.getLongitude());
        assertEquals(WaterSourceType.River, report.getType());
        assertEquals(WaterSourceCondition.Muddy, report.getCondition());
    }

    @Test
    void acceptsBoundaryCoordinates() {
        assertDoesNotThrow(() -> report(-90, -180));
        assertDoesNotThrow(() -> report(90, 180));
        assertDoesNotThrow(() -> report(0, 0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-90.0001, 90.0001, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidLatitude(double latitude) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> report(latitude, 0));
        assertEquals(Validation.MSG_INVALID_LATITUDE, e.getMessage());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-180.0001, 180.0001, Double.NaN, Double.NEGATIVE_INFINITY})
    void rejectsInvalidLongitude(double longitude) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> report(0, longitude));
        assertEquals(Validation.MSG_INVALID_LONGITUDE, e.getMessage());
    }

    @Test
    void rejectsMissingType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(1, "alice", "d", "t", 0, 0, null, WaterSourceCondition.Clear));
        assertEquals(Validation.MSG_TYPE_REQUIRED, e.getMessage());
    }

    @Test
    void rejectsMissingCondition() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(1, "alice", "d", "t", 0, 0, WaterSourceType.Well, null));
        assertEquals(Validation.MSG_CONDITION_REQUIRED, e.getMessage());
    }

    @Test
    void rejectsNonPositiveReportNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(0, "alice", "d", "t", 0, 0,
                        WaterSourceType.Well, WaterSourceCondition.Clear));
    }

    @Test
    void potableConditionExists() {
        assertEquals(WaterSourceCondition.Potable, WaterSourceCondition.valueOf("Potable"));
    }

    @Test
    void toStringContainsAllFields() {
        WaterSourceReport report = new WaterSourceReport(7, "alice", "2016/10/11", "12:30",
                33.77, -84.39, WaterSourceType.Well, WaterSourceCondition.Potable);

        String text = report.toString();

        assertTrue(text.contains("Date: 2016/10/11"), text);
        assertTrue(text.contains("Time: 12:30"), text);
        assertTrue(text.contains("User: alice"), text);
        assertTrue(text.contains("Type: Well"), text);
        assertTrue(text.contains("Condition: Potable"), text);
        assertTrue(text.contains("Report Number: 7"), text);
        assertTrue(text.contains("Latitude: 33.77"), text);
        assertTrue(text.contains("Longitude: -84.39"), text);
    }
}
