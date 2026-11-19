package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeoPointTest {

    @ParameterizedTest
    @CsvSource({
            "33.7, 33.7",
            "-84.39, -84.39",
            "0.00001, 0.00001",
            "1.0E-5, 0.00001",
            "-0.0, 0",
            "0.0000004, 0",
            "-0.0000004, 0",
            "12.3456785, 12.345679",
            "-12.3456785, -12.345679",
            "180, 180",
            "-90, -90",
            "45.100000, 45.1",
    })
    void formatIsPlainDecimalWithAtMostSixPlaces(double degrees, String expected) {
        assertEquals(expected, GeoPoint.format(degrees));
    }

    @Test
    void formatIgnoresDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("-84.39", GeoPoint.format(-84.39));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void formattedValuesParseBackToTheSamePoint() {
        GeoPoint point = new GeoPoint(-0.000012, 179.999999);

        assertEquals(point, GeoPoint.parse(point.formattedLatitude(), point.formattedLongitude()));
        assertEquals("-0.000012", point.formattedLatitude());
    }

    @Test
    void formatRejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.format(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.format(Double.POSITIVE_INFINITY));
    }

    @Test
    void constructorRejectsOutOfRangeCoordinates() {
        assertEquals(Validation.MSG_INVALID_LATITUDE,
                assertThrows(IllegalArgumentException.class, () -> new GeoPoint(90.5, 0)).getMessage());
        assertEquals(Validation.MSG_INVALID_LONGITUDE,
                assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0, -180.5)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0));
    }

    @Test
    void parseUsesTheFormValidation() {
        assertEquals(new GeoPoint(33.7, -84.4), GeoPoint.parse(" 33.7 ", "-84.4"));
        assertEquals(Validation.MSG_INVALID_LATITUDE,
                assertThrows(IllegalArgumentException.class, () -> GeoPoint.parse("1e1", "0")).getMessage());
        assertEquals(Validation.MSG_INVALID_LONGITUDE,
                assertThrows(IllegalArgumentException.class, () -> GeoPoint.parse("0", "")).getMessage());
    }

    @Test
    void tryParseIsEmptyForInvalidInput() {
        assertEquals(Optional.of(new GeoPoint(1, 2)), GeoPoint.tryParse("1", "2"));
        assertEquals(Optional.empty(), GeoPoint.tryParse("", "2"));
        assertEquals(Optional.empty(), GeoPoint.tryParse(null, null));
        assertEquals(Optional.empty(), GeoPoint.tryParse("91", "0"));
    }

    @Test
    void distanceToItselfIsZero() {
        GeoPoint p = new GeoPoint(33.7756, -84.3963);
        assertEquals(0.0, p.distanceKm(p), 1e-9);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        double expected = 2 * Math.PI * GeoPoint.EARTH_RADIUS_KM / 360;
        assertEquals(expected, new GeoPoint(10, 20).distanceKm(new GeoPoint(11, 20)), 1e-6);
        assertEquals(111.195, expected, 0.001);
    }

    @Test
    void knownCityDistance() {
        GeoPoint london = new GeoPoint(51.5074, -0.1278);
        GeoPoint paris = new GeoPoint(48.8566, 2.3522);
        assertEquals(343.5, london.distanceKm(paris), 1.0);
        assertEquals(london.distanceKm(paris), paris.distanceKm(london), 1e-9);
    }

    @Test
    void distanceAcrossTheAntimeridianIsShort() {
        double oneDegree = 2 * Math.PI * GeoPoint.EARTH_RADIUS_KM / 360;
        assertEquals(oneDegree, new GeoPoint(0, 179.5).distanceKm(new GeoPoint(0, -179.5)), 1e-6);
    }

    @Test
    void antipodesAreHalfACircumferenceApart() {
        double half = Math.PI * GeoPoint.EARTH_RADIUS_KM;
        assertEquals(half, new GeoPoint(0, 0).distanceKm(new GeoPoint(0, 180)), 1e-6);
        assertEquals(half, new GeoPoint(90, 0).distanceKm(new GeoPoint(-90, 0)), 1e-6);
        assertEquals(half, new GeoPoint(45, 10).distanceKm(new GeoPoint(-45, -170)), 1e-6);
    }

    @Test
    void longitudeDoesNotMatterAtThePole() {
        assertEquals(0.0, new GeoPoint(90, 0).distanceKm(new GeoPoint(90, 123)), 1e-6);
    }

    @Test
    void smallDistancesAreAccurate() {
        // 0.001 degrees of latitude is about 111 m
        assertEquals(0.111195, new GeoPoint(33.0, -84.0).distanceKm(new GeoPoint(33.001, -84.0)), 1e-5);
    }
}
