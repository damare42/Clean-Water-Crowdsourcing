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
}
