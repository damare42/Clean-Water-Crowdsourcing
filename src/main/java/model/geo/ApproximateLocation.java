package model.geo;

import model.GeoPoint;

import java.time.Instant;
import java.util.Objects;

/**
 * An approximate position of this computer.
 *
 * @param point the position
 * @param source where it came from
 * @param accuracyKm roughly how far off it may be, in kilometres (positive)
 * @param placeLabel a short, cleaned place name such as "New York, New York, United
 *                   States", or empty; plain text for labels, never HTML
 * @param obtainedAt when it was found
 * @param zoneId the time zone it came from for {@link LocationSource#TIME_ZONE}, else empty
 */
public record ApproximateLocation(GeoPoint point, LocationSource source, double accuracyKm,
                                  String placeLabel, Instant obtainedAt, String zoneId) {

    /** The accuracy of an IP lookup, used for the circle on the map. */
    public static final double IP_ACCURACY_KM = 25;
    /** The accuracy of a time-zone position (a zone covers a large area). */
    public static final double TIME_ZONE_ACCURACY_KM = 250;

    public ApproximateLocation {
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(placeLabel, "placeLabel");
        Objects.requireNonNull(obtainedAt, "obtainedAt");
        Objects.requireNonNull(zoneId, "zoneId");
        if (!Double.isFinite(accuracyKm) || accuracyKm <= 0 || accuracyKm > 20_000) {
            throw new IllegalArgumentException("Not an accuracy: " + accuracyKm);
        }
    }
}
