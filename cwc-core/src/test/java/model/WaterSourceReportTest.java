package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterSourceReportTest {

    /** 2016-10-11T12:30:45.123456789Z */
    private static final Instant WHEN = Instant.parse("2016-10-11T12:30:45.123456789Z");

    private static WaterSourceReport report(double latitude, double longitude) {
        return new WaterSourceReport(1, "alice", WHEN, latitude, longitude,
                WaterSourceType.Lake, WaterSourceCondition.Clear);
    }

    @Test
    void constructorSetsFields() {
        WaterSourceReport report = new WaterSourceReport(3, "alice", WHEN,
                33.77, -84.39, WaterSourceType.River, WaterSourceCondition.Muddy);

        assertEquals(3, report.getReportNumber());
        assertEquals("alice", report.getReporter());
        assertEquals(Instant.parse("2016-10-11T12:30:45.123Z"), report.getReportedAt());
        assertEquals(33.77, report.getLatitude());
        assertEquals(-84.39, report.getLongitude());
        assertEquals(WaterSourceType.River, report.getType());
        assertEquals(WaterSourceCondition.Muddy, report.getCondition());
    }

    @Test
    void dateAndTimeAreDisplayedInTheGivenZone() {
        WaterSourceReport report = report(0, 0);

        assertEquals("2016/10/11", report.getDate(ZoneOffset.UTC));
        assertEquals("12:30", report.getTime(ZoneOffset.UTC));
        assertEquals("2016/10/11", report.getDate(ZoneId.of("America/New_York")));
        assertEquals("8:30", report.getTime(ZoneId.of("America/New_York")));
        assertEquals("2016/10/12", report.getDate(ZoneId.of("Pacific/Kiritimati")));
        assertEquals("2:30", report.getTime(ZoneId.of("Pacific/Kiritimati")));
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
                () -> new WaterSourceReport(1, "alice", WHEN, 0, 0, null, WaterSourceCondition.Clear));
        assertEquals(Validation.MSG_TYPE_REQUIRED, e.getMessage());
    }

    @Test
    void rejectsMissingCondition() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(1, "alice", WHEN, 0, 0, WaterSourceType.Well, null));
        assertEquals(Validation.MSG_CONDITION_REQUIRED, e.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void rejectsMissingReporter(String reporter) {
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(1, reporter, WHEN, 0, 0,
                        WaterSourceType.Well, WaterSourceCondition.Clear));
    }

    @Test
    void rejectsMissingTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(1, "alice", null, 0, 0,
                        WaterSourceType.Well, WaterSourceCondition.Clear));
    }

    @Test
    void rejectsNonPositiveReportNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReport(0, "alice", WHEN, 0, 0,
                        WaterSourceType.Well, WaterSourceCondition.Clear));
    }

    @Test
    void potableConditionExists() {
        assertEquals(WaterSourceCondition.Potable, WaterSourceCondition.valueOf("Potable"));
    }

    @Test
    void equalityIsByValue() {
        WaterSourceReport a = report(1, 2);

        assertEquals(a, report(1, 2));
        assertEquals(a.hashCode(), report(1, 2).hashCode());
        assertNotEquals(a, report(1, 3));
        assertNotEquals(a, new WaterSourceReport(2, "alice", WHEN, 1, 2,
                WaterSourceType.Lake, WaterSourceCondition.Clear));
    }

    @Test
    void describeContainsAllFields() {
        WaterSourceReport report = new WaterSourceReport(7, "alice", WHEN,
                33.77, -84.39, WaterSourceType.Well, WaterSourceCondition.Potable);

        String text = report.describe(ZoneOffset.UTC);

        assertTrue(text.contains("Date: 2016/10/11"), text);
        assertTrue(text.contains("Time: 12:30"), text);
        assertTrue(text.contains("User: alice"), text);
        assertTrue(text.contains("Type: Well"), text);
        assertTrue(text.contains("Condition: Potable"), text);
        assertTrue(text.contains("Report Number: 7"), text);
        assertTrue(text.contains("Latitude: 33.77"), text);
        assertTrue(text.contains("Longitude: -84.39"), text);
        assertEquals(report.describe(ZoneId.systemDefault()), report.toString());
    }

    @Test
    void draftValidatesAndTruncatesTime() {
        WaterSourceReportDraft draft = new WaterSourceReportDraft("alice", WHEN, 1, 2,
                WaterSourceType.Pond, WaterSourceCondition.Waste);

        assertEquals(Instant.parse("2016-10-11T12:30:45.123Z"), draft.reportedAt());
        WaterSourceReport report = draft.withNumber(4);
        assertEquals(4, report.getReportNumber());
        assertEquals(new WaterSourceReport(4, "alice", WHEN, 1, 2,
                WaterSourceType.Pond, WaterSourceCondition.Waste), report);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReportDraft("alice", WHEN, 91, 2,
                        WaterSourceType.Pond, WaterSourceCondition.Waste));
        assertEquals(Validation.MSG_INVALID_LATITUDE, e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> new WaterSourceReportDraft("alice", WHEN, 0, 0, WaterSourceType.Pond, null));
    }
}
