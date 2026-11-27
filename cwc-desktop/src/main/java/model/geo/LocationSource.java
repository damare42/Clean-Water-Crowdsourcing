package model.geo;

/**
 * Where an {@link ApproximateLocation} came from.
 */
public enum LocationSource {
    /** An IP geolocation service. */
    IP,
    /** The table of time-zone positions ({@link TimeZoneLocations}). */
    TIME_ZONE
}
