package model.geo;

/**
 * How the app finds the approximate location of this computer, a setting of the
 * installation (see {@link LocationService}).
 */
public enum LocationMode {
    /** Look the location up from the internet address, falling back to the time zone. */
    IP_LOOKUP,
    /** Only use the computer's time zone; nothing leaves the computer. */
    TIME_ZONE,
    /** Do not locate the computer at all. */
    OFF
}
