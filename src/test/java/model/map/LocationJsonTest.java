package model.map;

import model.GeoPoint;
import model.geo.ApproximateLocation;
import model.geo.LocationSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocationJsonTest {

    @Test
    void ipLocation() {
        ApproximateLocation location = new ApproximateLocation(new GeoPoint(40.7128, -74.006), LocationSource.IP,
                25, "<b>New York</b>", Instant.EPOCH, "");
        assertEquals("{\"lat\":40.7128,\"lon\":-74.006,\"accuracyM\":25000,\"kind\":\"ip\"}", LocationJson.toJson(location));
    }

    @Test
    void timeZoneLocationWithoutPlaceText() {
        ApproximateLocation location = new ApproximateLocation(new GeoPoint(-33.8667, 151.2167), LocationSource.TIME_ZONE,
                250, "", Instant.EPOCH, "Australia/Sydney");
        assertEquals("{\"lat\":-33.8667,\"lon\":151.2167,\"accuracyM\":250000,\"kind\":\"timezone\"}",
                LocationJson.toJson(location));
    }

    @Test
    void numbersIgnoreTheLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            ApproximateLocation location = new ApproximateLocation(new GeoPoint(0.00001, -0.5), LocationSource.IP,
                    0.0125, "", Instant.EPOCH, "");
            assertEquals("{\"lat\":0.00001,\"lon\":-0.5,\"accuracyM\":13,\"kind\":\"ip\"}", LocationJson.toJson(location));
        } finally {
            Locale.setDefault(before);
        }
    }
}
