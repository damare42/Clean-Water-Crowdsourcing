package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaterPurityReportTest {

    private static final Instant WHEN = Instant.parse("2016-10-11T12:30:45.123456Z");
    private static final GeoPoint WHERE = new GeoPoint(33.77, -84.39);

    private static WaterPurityReportDraft draft(double virus, double contaminant) {
        return new WaterPurityReportDraft("alice", WHEN, WHERE, WaterPurityCondition.Treatable, virus, contaminant);
    }

    @Test
    void draftKeepsFieldsAndTruncatesTime() {
        WaterPurityReport report = draft(12.5, 0.25).withNumber(3);

        assertEquals(3, report.getReportNumber());
        assertEquals("alice", report.getReporter());
        assertEquals(Instant.parse("2016-10-11T12:30:45.123Z"), report.getReportedAt());
        assertEquals(WHERE, report.getLocation());
        assertEquals(WaterPurityCondition.Treatable, report.getCondition());
        assertEquals(12.5, report.getVirusPpm());
        assertEquals(0.25, report.getContaminantPpm());
        assertEquals("Water purity report 3", report.getTitle());
    }

    @Test
    void fieldsForDisplay() {
        List<ReportField> fields = draft(12.5, 0).withNumber(3).fields(ZoneId.of("America/New_York"));

        assertEquals(List.of(
                new ReportField("Report number", "3"),
                new ReportField("Date", "2016/10/11"),
                new ReportField("Time", "8:30"),
                new ReportField("Reporter", "alice"),
                new ReportField("Condition", "Treatable"),
                new ReportField("Virus PPM", "12.5"),
                new ReportField("Contaminant PPM", "0"),
                new ReportField("Latitude", "33.77"),
                new ReportField("Longitude", "-84.39")), fields);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "-0.0, 0", "0.5, 0.5", "120, 120", "1000000, 1000000", "0.000001, 0.000001", "1.0E-7, 0.0000001"})
    void formatPpmIsPlain(double value, String expected) {
        assertEquals(expected, WaterPurityReport.formatPpm(value));
    }

    @Test
    void negativeZeroBecomesZero() {
        WaterPurityReport report = draft(-0.0, -0.0).withNumber(1);
        assertEquals(0, Double.compare(0.0, report.getVirusPpm()));
        assertEquals(0, Double.compare(0.0, report.getContaminantPpm()));
        assertEquals(draft(0, 0).withNumber(1), report);
    }

    @ParameterizedTest
    @CsvSource({"-1, 0", "0, -0.5", "NaN, 0", "0, NaN", "Infinity, 0", "0, Infinity", "1000000.5, 0"})
    void invalidPpmIsRejected(double virus, double contaminant) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> draft(virus, contaminant));
        String expected = Validation.isValidPpm(virus) ? Validation.MSG_INVALID_CONTAMINANT_PPM
                : Validation.MSG_INVALID_VIRUS_PPM;
        assertEquals(expected, e.getMessage());
    }

    @Test
    void missingFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new WaterPurityReportDraft(null, WHEN, WHERE,
                WaterPurityCondition.Safe, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaterPurityReportDraft(" ", WHEN, WHERE,
                WaterPurityCondition.Safe, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaterPurityReportDraft("a", null, WHERE,
                WaterPurityCondition.Safe, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaterPurityReportDraft("a", WHEN, null,
                WaterPurityCondition.Safe, 0, 0));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new WaterPurityReportDraft("a", WHEN, WHERE, null, 0, 0));
        assertEquals(Validation.MSG_PURITY_CONDITION_REQUIRED, e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> draft(0, 0).withNumber(0));
    }

    @Test
    void equalityCoversAllFields() {
        WaterPurityReport a = draft(1, 2).withNumber(1);
        assertEquals(a, draft(1, 2).withNumber(1));
        assertEquals(a.hashCode(), draft(1, 2).withNumber(1).hashCode());
        assertNotEquals(a, draft(1, 2).withNumber(2));
        assertNotEquals(a, draft(1.5, 2).withNumber(1));
        assertNotEquals(a, draft(1, 2.5).withNumber(1));
        assertNotEquals(a, new WaterPurityReport(1, "alice", WHEN, WHERE, WaterPurityCondition.Unsafe, 1, 2));
        assertNotEquals(a, new WaterPurityReport(1, "bob", WHEN, WHERE, WaterPurityCondition.Treatable, 1, 2));
        assertNotEquals(a, new WaterPurityReport(1, "alice", WHEN, new GeoPoint(0, 0),
                WaterPurityCondition.Treatable, 1, 2));
    }

    @Test
    void purityMetricMeasuresTheRightValue() {
        WaterPurityReport report = draft(7, 9).withNumber(1);
        assertEquals(7, PurityMetric.Virus.measure(report));
        assertEquals(9, PurityMetric.Contaminant.measure(report));
        assertEquals("Virus PPM", PurityMetric.Virus.label());
    }

    @Test
    void sourceAndPurityReportsShareTheReportInterface() {
        Report purity = draft(1, 1).withNumber(5);
        Report source = new WaterSourceReport(5, "bob", WHEN, 1, 2, WaterSourceType.Lake, WaterSourceCondition.Clear);
        assertEquals(WHERE, purity.getLocation());
        assertEquals(new GeoPoint(1, 2), source.getLocation());
        assertEquals("Water source report 5", source.getTitle());
    }
}
