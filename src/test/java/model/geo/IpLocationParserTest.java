package model.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpLocationParserTest {

    static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");

    /** ipapi.co documentation sample (https://ipapi.co/api/#complete-location). */
    static final String IPAPI = "{\"ip\":\"8.8.8.8\",\"network\":\"8.8.8.0/24\",\"version\":\"IPv4\","
            + "\"city\":\"Mountain View\",\"region\":\"California\",\"region_code\":\"CA\",\"country\":\"US\","
            + "\"country_name\":\"United States\",\"country_code\":\"US\",\"country_code_iso3\":\"USA\","
            + "\"country_capital\":\"Washington\",\"country_tld\":\".us\",\"continent_code\":\"NA\","
            + "\"in_eu\":false,\"postal\":\"94043\",\"latitude\":37.42301,\"longitude\":-122.083352,"
            + "\"timezone\":\"America/Los_Angeles\",\"utc_offset\":\"-0700\",\"country_calling_code\":\"+1\","
            + "\"currency\":\"USD\",\"currency_name\":\"Dollar\",\"languages\":\"en-US,es-US,haw,fr\","
            + "\"country_area\":9629091.0,\"country_population\":327167434,\"asn\":\"AS15169\",\"org\":\"GOOGLE\"}";
    /** ipwho.is documentation sample (https://ipwhois.io/documentation). */
    static final String IPWHO = "{\"ip\":\"8.8.4.4\",\"success\":true,\"type\":\"IPv4\",\"continent\":\"North America\","
            + "\"continent_code\":\"NA\",\"country\":\"United States\",\"country_code\":\"US\",\"region\":\"California\","
            + "\"region_code\":\"CA\",\"city\":\"Mountain View\",\"latitude\":37.3860517,\"longitude\":-122.0838511,"
            + "\"is_eu\":false,\"postal\":\"94039\",\"calling_code\":\"1\",\"capital\":\"Washington D.C.\","
            + "\"borders\":\"CA,MX\",\"flag\":{\"img\":\"https://cdn.ipwhois.io/flags/us.svg\","
            + "\"emoji\":\"\uD83C\uDDFA\uD83C\uDDF8\",\"emoji_unicode\":\"U+1F1FA U+1F1F8\"},"
            + "\"connection\":{\"asn\":15169,\"org\":\"Google LLC\",\"isp\":\"Google LLC\",\"domain\":\"google.com\"},"
            + "\"timezone\":{\"id\":\"America/Los_Angeles\",\"abbr\":\"PDT\",\"is_dst\":true,\"offset\":-25200,"
            + "\"utc\":\"-07:00\",\"current_time\":\"2023-06-21T05:33:52-07:00\"}}";
    /** ipinfo.io documentation sample (https://ipinfo.io/developers). */
    static final String IPINFO = "{\"ip\":\"8.8.8.8\",\"hostname\":\"dns.google\",\"city\":\"Mountain View\","
            + "\"region\":\"California\",\"country\":\"US\",\"loc\":\"37.4056,-122.0775\",\"org\":\"AS15169 Google LLC\","
            + "\"postal\":\"94043\",\"timezone\":\"America/Los_Angeles\",\"readme\":\"https://ipinfo.io/missingauth\","
            + "\"anycast\":true}";

    @Test
    void readsIpapi() {
        ApproximateLocation location = IpLocationParser.parse(IPAPI, NOW).orElseThrow();
        assertEquals(37.42301, location.point().latitude());
        assertEquals(-122.083352, location.point().longitude());
        assertEquals("Mountain View, California, United States", location.placeLabel());
        assertEquals(LocationSource.IP, location.source());
        assertEquals(ApproximateLocation.IP_ACCURACY_KM, location.accuracyKm());
        assertEquals(NOW, location.obtainedAt());
        assertEquals("", location.zoneId());
    }

    @Test
    void readsIpwho() {
        ApproximateLocation location = IpLocationParser.parse(IPWHO, NOW).orElseThrow();
        assertEquals(37.3860517, location.point().latitude());
        assertEquals("Mountain View, California, United States", location.placeLabel());
    }

    @Test
    void readsIpinfo() {
        ApproximateLocation location = IpLocationParser.parse(IPINFO, NOW).orElseThrow();
        assertEquals(37.4056, location.point().latitude());
        assertEquals(-122.0775, location.point().longitude());
        assertEquals("Mountain View, California, US", location.placeLabel());
    }

    @Test
    void labelMayBeEmpty() {
        assertEquals("", IpLocationParser.parse("{\"latitude\":40.7,\"longitude\":-74}", NOW).orElseThrow().placeLabel());
        assertEquals("Paris", IpLocationParser.parse(
                "{\"latitude\":48.85,\"longitude\":2.35,\"city\":\"Paris\",\"region\":7,\"country\":null}", NOW)
                .orElseThrow().placeLabel());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"error\":true,\"reason\":\"RateLimited\",\"message\":\"Visit https://ipapi.co/ratelimited/ for details\"}",
            "{\"error\":true,\"latitude\":40.7,\"longitude\":-74.0}",
            "{\"success\":false,\"message\":\"Reserved range\"}",
            "{\"success\":false,\"latitude\":40.7,\"longitude\":-74.0}",
            "{\"latitude\":\"40.7\",\"longitude\":\"-74.0\"}",
            "{\"latitude\":\"abc\",\"longitude\":-74.0}",
            "{\"latitude\":999,\"longitude\":-74.0}",
            "{\"latitude\":40.7,\"longitude\":-181}",
            "{\"latitude\":1e400,\"longitude\":-74.0}",
            "{\"latitude\":0,\"longitude\":0}",
            "{\"latitude\":0.0,\"longitude\":-0.0}",
            "{\"latitude\":40.7}",
            "{\"latitude\":40.7,\"loc\":\"40.7,-74\"}",
            "{\"latitude\":null,\"longitude\":null}",
            "{\"loc\":\"40.7\"}",
            "{\"loc\":\"40.7,-74,5\"}",
            "{\"loc\":\"91,-74\"}",
            "{\"loc\":\"0,0\"}",
            "{\"loc\":[40.7,-74]}",
            "{\"city\":\"Nowhere\"}",
            "<!DOCTYPE html><html><body>Rate limited</body></html>",
            "[{\"latitude\":40.7,\"longitude\":-74.0}]",
            "",
    })
    void rejectsUnusableAnswers(String body) {
        assertTrue(IpLocationParser.parse(body, NOW).isEmpty(), body);
    }

    @Test
    void hostilePlaceNamesStayPlainCleanText() {
        String body = "{\"latitude\":40.7128,\"longitude\":-74.006,"
                + "\"city\":\"<img src=x onerror=alert(1)>\\u202e\\u0007\\u200b  New\\n\\tYork\","
                + "\"region\":\"\\u0000NY\\u2066\",\"country_name\":\"" + "U".repeat(200) + "\"}";
        String label = IpLocationParser.parse(body, NOW).orElseThrow().placeLabel();
        assertTrue(label.startsWith("<img src=x onerror=alert(1)> New York, NY, UUU"), label);
        assertEquals(IpLocationParser.MAX_LABEL_LENGTH, label.length(), label);
        assertFalse(label.chars().anyMatch(c -> Character.getType(c) == Character.CONTROL
                || Character.getType(c) == Character.FORMAT), label);
    }

    @Test
    void sanitizeLabelCollapsesWhitespaceAndCaps() {
        assertEquals("a b c", IpLocationParser.sanitizeLabel("  a \u2003\u00a0b\r\nc  "));
        assertEquals("", IpLocationParser.sanitizeLabel(null));
        assertEquals("x".repeat(80), IpLocationParser.sanitizeLabel("x".repeat(500)));
    }
}
