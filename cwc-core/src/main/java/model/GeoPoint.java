package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * A position on Earth in decimal degrees (WGS 84, as used by GPS and web maps).
 * Instances are always valid: latitude in [-90, 90], longitude in [-180, 180].
 *
 * @param latitude  degrees north (negative for south)
 * @param longitude degrees east (negative for west)
 */
public record GeoPoint(double latitude, double longitude) {

    /** Decimal places kept by {@link #format(double)}: 6 places is about 0.1 m. */
    public static final int DECIMALS = 6;
    /** Mean radius of the Earth in kilometres (IUGG), used by {@link #distanceKm}. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    /**
     * @throws IllegalArgumentException with a user-facing message if a coordinate is
     *         out of range or not a number
     */
    public GeoPoint {
        if (!Validation.isValidLatitude(latitude)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_LATITUDE);
        }
        if (!Validation.isValidLongitude(longitude)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_LONGITUDE);
        }
    }

    /**
     * Parses user-entered coordinates with {@link Validation#parseLatitude} and
     * {@link Validation#parseLongitude}.
     *
     * @throws IllegalArgumentException with a user-facing message for the first invalid value
     */
    public static GeoPoint parse(String latitude, String longitude) {
        return new GeoPoint(Validation.parseLatitude(latitude), Validation.parseLongitude(longitude));
    }

    /**
     * @return the parsed point, or empty if either text is not a valid coordinate
     */
    public static Optional<GeoPoint> tryParse(String latitude, String longitude) {
        try {
            return Optional.of(parse(latitude, longitude));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Formats a coordinate as a plain decimal number with at most {@value #DECIMALS}
     * decimal places, no exponent, no trailing zeros and a '.' separator whatever the
     * locale, so the result is accepted by {@link Validation#parseLatitude} and
     * {@link Validation#parseLongitude}. Negative zero is shown as "0".
     *
     * @param degrees a finite coordinate
     * @return e.g. "33.7", "-84.390001", "0.00001"
     * @throws IllegalArgumentException if the value is NaN or infinite
     */
    public static String format(double degrees) {
        if (!Double.isFinite(degrees)) {
            throw new IllegalArgumentException("Not a coordinate: " + degrees);
        }
        BigDecimal rounded = BigDecimal.valueOf(degrees).setScale(DECIMALS, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return "0";
        }
        return rounded.stripTrailingZeros().toPlainString();
    }

    /**
     * @return the latitude formatted with {@link #format(double)}
     */
    public String formattedLatitude() {
        return format(latitude);
    }

    /**
     * @return the longitude formatted with {@link #format(double)}
     */
    public String formattedLongitude() {
        return format(longitude);
    }

    /**
     * The great-circle distance to another point, by the haversine formula on a
     * sphere of radius {@link #EARTH_RADIUS_KM}. Accurate to about 0.5% (the Earth is
     * not a perfect sphere), which is plenty for "reports near here". Works across
     * the 180th meridian and at the poles.
     *
     * @param other the other point
     * @return the distance in kilometres, from 0 to about 20015
     */
    public double distanceKm(GeoPoint other) {
        double lat1 = Math.toRadians(latitude);
        double lat2 = Math.toRadians(other.latitude);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(other.longitude - longitude);
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        // min() guards against rounding pushing h a hair above 1 for antipodal points.
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(Math.min(1.0, h)));
    }
}
