package model.map;

import model.GeoPoint;
import model.Report;
import model.ReportField;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerJsonTest {

    private static final Instant WHEN = Instant.parse("2016-10-11T09:05:00Z");

    private static WaterSourceReport report(int number, String reporter) {
        return new WaterSourceReport(number, reporter, WHEN, 33.7, -84.39,
                WaterSourceType.Lake, WaterSourceCondition.Potable);
    }

    @Test
    void sourceReportMarkerHasAllFieldsInOrder() {
        String json = MarkerJson.toJson(List.of(MapMarker.forReport(report(7, "alice"), ZoneOffset.UTC)));

        assertEquals("[{\"kind\":\"source\",\"id\":7,\"lat\":33.7,\"lon\":-84.39,"
                + "\"title\":\"Water source report 7\",\"status\":\"success\",\"fields\":["
                + "{\"label\":\"Report number\",\"value\":\"7\"},"
                + "{\"label\":\"Date\",\"value\":\"2016/10/11\"},"
                + "{\"label\":\"Time\",\"value\":\"9:05\"},"
                + "{\"label\":\"Reporter\",\"value\":\"alice\"},"
                + "{\"label\":\"Type\",\"value\":\"Lake\"},"
                + "{\"label\":\"Condition\",\"value\":\"Potable\"},"
                + "{\"label\":\"Latitude\",\"value\":\"33.7\"},"
                + "{\"label\":\"Longitude\",\"value\":\"-84.39\"}]}]", json);
    }

    @Test
    void emptyListIsEmptyArray() {
        assertEquals("[]", MarkerJson.toJson(List.of()));
    }

    @Test
    void markersKeepTheirOrder() {
        List<MapMarker> markers = MapMarker.forReports(
                List.of(report(2, "bob"), report(1, "alice")), ZoneOffset.UTC);

        String json = MarkerJson.toJson(markers);

        assertTrue(json.startsWith("[{\"kind\":\"source\",\"id\":2,"), json);
        assertTrue(json.contains("},{\"kind\":\"source\",\"id\":1,"), json);
    }

    @Test
    void quotesAndBackslashesAreEscaped() {
        assertEquals("\"a\\\"b\\\\c\"", MarkerJson.quote("a\"b\\c"));
    }

    @Test
    void htmlAndScriptBreakersAreEscaped() {
        String quoted = MarkerJson.quote("</script><b onclick='x'>&amp;");

        assertEquals("\"\\u003c/script\\u003e\\u003cb onclick=\\u0027x\\u0027\\u003e\\u0026amp;\"", quoted);
        assertFalse(quoted.contains("<"));
        assertFalse(quoted.contains(">"));
        assertFalse(quoted.contains("'"));
    }

    @Test
    void controlCharactersAreEscaped() {
        assertEquals("\"\\n\\r\\t\\u0000\\u001f\\u007f\"", MarkerJson.quote("\n\r\t\u0000\u001f\u007f"));
    }

    @Test
    void nonAsciiIsEscapedAsUtf16Units() {
        assertEquals("\"\\u00c4rger \\u2028\\u2029 \\ud83d\\udca7\"",
                MarkerJson.quote("\u00c4rger \u2028\u2029 \uD83D\uDCA7"));
    }

    @Test
    void outputIsPrintableAscii() {
        MapMarker marker = new MapMarker("source", 1, new GeoPoint(-0.00001, 179.5),
                "T\u00edtulo \"x\"", List.of(new ReportField("L\u00e4bel", "</script>\u0007")));

        String json = MarkerJson.toJson(List.of(marker));

        assertTrue(json.chars().allMatch(c -> c >= 0x20 && c <= 0x7e), json);
        assertTrue(json.contains("\"lat\":-1.0E-5,\"lon\":179.5"), json);
    }

    @Test
    void purityReportsGetTheirOwnKind() {
        WaterPurityReport purity = new WaterPurityReport(4, "bob", Instant.EPOCH,
                new GeoPoint(1.5, -2), WaterPurityCondition.Unsafe, 0.5, 12);

        MapMarker marker = MapMarker.forReport(purity, ZoneOffset.UTC);

        assertEquals(MapMarker.KIND_PURITY, marker.kind());
        assertEquals(MapMarker.KIND_PURITY, MapMarker.kindOf(purity));
        assertEquals(MapMarker.KIND_SOURCE, MapMarker.kindOf(report(1, "a")));
        assertEquals(4, marker.id());
        assertEquals("Water purity report 4", marker.title());
        String json = MarkerJson.toJson(List.of(marker));
        assertTrue(json.startsWith("[{\"kind\":\"purity\",\"id\":4,\"lat\":1.5,\"lon\":-2.0,"), json);
        assertTrue(json.contains("{\"label\":\"Virus PPM\",\"value\":\"0.5\"}"), json);
    }

    @Test
    void unknownReportKindsAreRejected() {
        Report other = new Report() {
            @Override
            public int getReportNumber() {
                return 1;
            }

            @Override
            public String getReporter() {
                return "x";
            }

            @Override
            public Instant getReportedAt() {
                return Instant.EPOCH;
            }

            @Override
            public GeoPoint getLocation() {
                return new GeoPoint(0, 0);
            }

            @Override
            public String getTitle() {
                return "x";
            }

            @Override
            public List<ReportField> fields(ZoneId zone) {
                return List.of();
            }
        };
        assertThrows(IllegalArgumentException.class, () -> MapMarker.kindOf(other));
    }

    @Test
    void sourceConditionsMapToStatuses() {
        assertEquals(MapMarker.Status.SUCCESS, MapMarker.statusOf(WaterSourceCondition.Potable));
        assertEquals(MapMarker.Status.INFO, MapMarker.statusOf(WaterSourceCondition.Clear));
        assertEquals(MapMarker.Status.WARNING, MapMarker.statusOf(WaterSourceCondition.Muddy));
        assertEquals(MapMarker.Status.DANGER, MapMarker.statusOf(WaterSourceCondition.Waste));
    }

    @Test
    void purityConditionsMapToStatuses() {
        assertEquals(MapMarker.Status.SUCCESS, MapMarker.statusOf(WaterPurityCondition.Safe));
        assertEquals(MapMarker.Status.WARNING, MapMarker.statusOf(WaterPurityCondition.Treatable));
        assertEquals(MapMarker.Status.DANGER, MapMarker.statusOf(WaterPurityCondition.Unsafe));
    }

    @Test
    void anythingElseHasUnknownStatus() {
        assertEquals(MapMarker.Status.UNKNOWN, MapMarker.statusOf((Object) null));
        // Text that looks like a condition or a status is not one: only the enums count.
        assertEquals(MapMarker.Status.UNKNOWN, MapMarker.statusOf("Potable"));
        assertEquals(MapMarker.Status.UNKNOWN, MapMarker.statusOf("danger"));
        assertEquals(MapMarker.Status.UNKNOWN,
                new MapMarker("source", 1, new GeoPoint(1, 2), "t", List.of()).status());
    }

    @Test
    void statusIsRequired() {
        assertThrows(NullPointerException.class,
                () -> new MapMarker("source", 1, new GeoPoint(1, 2), "t", List.of(), null));
    }

    @Test
    void statusWordsAreAFixedListOfPlainWords() {
        List<String> words = java.util.Arrays.stream(MapMarker.Status.values()).map(MapMarker.Status::word).toList();

        assertEquals(List.of("success", "info", "warning", "danger", "unknown"), words);
        assertTrue(words.stream().allMatch(w -> w.matches("[a-z]+")), words.toString());
    }

    @Test
    void everyReportCarriesTheStatusOfItsCondition() {
        for (WaterSourceCondition condition : WaterSourceCondition.values()) {
            WaterSourceReport source = new WaterSourceReport(1, "a", WHEN, 1, 2, WaterSourceType.Well, condition);
            String json = MarkerJson.toJson(List.of(MapMarker.forReport(source, ZoneOffset.UTC)));
            assertTrue(json.contains(",\"status\":\"" + MapMarker.statusOf(condition).word() + "\",\"fields\":"), json);
        }
        for (WaterPurityCondition condition : WaterPurityCondition.values()) {
            WaterPurityReport purity = new WaterPurityReport(1, "a", WHEN, new GeoPoint(1, 2), condition, 1, 2);
            MapMarker marker = MapMarker.forReport(purity, ZoneOffset.UTC);
            assertEquals(MapMarker.statusOf(condition), marker.status());
            String json = MarkerJson.toJson(List.of(marker));
            assertTrue(json.contains(",\"status\":\"" + marker.status().word() + "\",\"fields\":"), json);
        }
    }

    @Test
    void hostileTextCannotChangeTheStatus() {
        MapMarker marker = new MapMarker("source", 1, new GeoPoint(1, 2),
                "x\",\"status\":\"<b>danger</b>", List.of(new ReportField("Condition", "\"danger")),
                MapMarker.Status.SUCCESS);

        String json = MarkerJson.toJson(List.of(marker));

        assertTrue(json.contains("\"title\":\"x\\\",\\\"status\\\":\\\"\\u003cb\\u003edanger\\u003c/b\\u003e\""), json);
        assertTrue(json.contains("\",\"status\":\"success\",\"fields\":["), json);
        assertEquals(1, json.split("\"status\":", -1).length - 1, json);
        assertTrue(json.chars().allMatch(c -> c >= 0x20 && c <= 0x7e), json);
    }
}
