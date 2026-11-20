package model.geo;

import model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeZoneLocationsTest {

    private static final TimeZoneLocations ZONES = TimeZoneLocations.load();

    @Test
    void everyRegionZoneOfTheJdkHasAPosition() {
        List<String> missing = new ArrayList<>();
        for (String id : ZoneId.getAvailableZoneIds()) {
            if (!id.contains("/") || id.startsWith("Etc/") || id.startsWith("SystemV/")) {
                continue;
            }
            if (ZONES.lookup(ZoneId.of(id)).isEmpty()) {
                missing.add(id);
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void newYork() {
        GeoPoint point = ZONES.lookup(ZoneId.of("America/New_York")).orElseThrow();
        assertEquals(40.714, point.latitude(), 0.01);
        assertEquals(-74.006, point.longitude(), 0.01);
    }

    @Test
    void aliasesResolve() {
        assertEquals(ZONES.lookup(ZoneId.of("Asia/Kolkata")), ZONES.lookup(ZoneId.of("Asia/Calcutta")));
        assertTrue(ZONES.lookup(ZoneId.of("Asia/Calcutta")).isPresent());
        assertEquals(ZONES.lookup(ZoneId.of("America/New_York")), ZONES.lookup(ZoneId.of("US/Eastern")));
    }

    @Test
    void zonesWithoutAPlaceHaveNoPosition() {
        for (String id : new String[]{"Etc/UTC", "GMT", "Z", "+02:00", "UTC", "Etc/GMT+5"}) {
            assertEquals(Optional.empty(), ZONES.lookup(ZoneId.of(id)), id);
        }
        assertEquals(Optional.empty(), ZONES.lookup(null));
    }

    @Test
    void tableIsSmallAndWellFormed() throws IOException {
        try (InputStream in = TimeZoneLocations.class.getResourceAsStream(TimeZoneLocations.RESOURCE)) {
            byte[] bytes = in.readAllBytes();
            assertTrue(bytes.length < 32 * 1024, bytes.length + " bytes");
            String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(text.startsWith("#"), "has a header");
            assertTrue(text.contains("tzdata"), "names its source");
            for (String line : text.split("\n")) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t");
                assertEquals(3, parts.length, line);
                new GeoPoint(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            }
        }
        assertTrue(ZONES.size() > 500, String.valueOf(ZONES.size()));
    }
}
