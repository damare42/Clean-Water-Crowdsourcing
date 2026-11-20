package model.geo;

import model.Account;
import model.GeoPoint;
import model.persistence.DataAccessException;
import model.persistence.SettingsRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds the approximate location of this computer, for centring the map at login.
 * <p>
 * The {@link LocationMode} is a setting of the installation (any signed-in user may
 * change it). With {@link LocationMode#IP_LOOKUP} the chain is: a cached IP result at
 * most {@value #CACHE_HOURS} hours old, else an IP lookup, else the time zone's
 * position. With {@link LocationMode#TIME_ZONE} only the time zone is used, and with
 * {@link LocationMode#OFF} nothing. If IP lookup is disabled for the installation
 * ({@link GeoConfig}), IP_LOOKUP behaves like TIME_ZONE.
 * <p>
 * The last IP result is kept in the settings for {@value #CACHE_HOURS} hours, so the
 * service is asked about once a day. A forced lookup ("Locate me") skips the cache but
 * reuses a result less than {@value #THROTTLE_SECONDS} seconds old. Leaving IP_LOOKUP
 * deletes the cached result.
 */
public final class LocationService {
    private static final Logger LOGGER = Logger.getLogger(LocationService.class.getName());

    /** Settings key of the mode. */
    public static final String MODE_KEY = "location.mode";
    /** Settings key of the cached IP result: {@code lat|lon|accuracyKm|epochMillis|label}. */
    public static final String CACHE_KEY = "location.cache";
    /** How long an IP result is reused. */
    public static final int CACHE_HOURS = 24;
    /** A forced lookup reuses a result younger than this. */
    public static final int THROTTLE_SECONDS = 60;

    private static final Duration CACHE_AGE = Duration.ofHours(CACHE_HOURS);
    private static final Duration THROTTLE = Duration.ofSeconds(THROTTLE_SECONDS);
    /** Tolerated clock difference for results stamped slightly in the future. */
    private static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final SettingsRepository settings;
    private final LocationProvider provider;
    private final TimeZoneLocations zones;
    private final ZoneId zone;
    private final Clock clock;
    private final GeoConfig config;

    /**
     * @param settings where the mode and the cached result are kept
     * @param provider the IP lookup
     * @param zones the time-zone positions
     * @param zone this computer's time zone
     * @param clock the clock
     * @param config whether IP lookup is available, and where it goes
     */
    public LocationService(SettingsRepository settings, LocationProvider provider, TimeZoneLocations zones,
                           ZoneId zone, Clock clock, GeoConfig config) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.zones = Objects.requireNonNull(zones, "zones");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * @return the stored mode ({@link LocationMode#IP_LOOKUP} if none or unreadable)
     */
    public LocationMode mode() {
        try {
            return settings.get(MODE_KEY).map(LocationService::parseMode).orElse(LocationMode.IP_LOOKUP);
        } catch (DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not read the location setting", e);
            return LocationMode.IP_LOOKUP;
        }
    }

    private static LocationMode parseMode(String value) {
        try {
            return LocationMode.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return LocationMode.IP_LOOKUP;
        }
    }

    /**
     * @return the mode in effect: the stored mode, except that IP_LOOKUP acts as
     *         TIME_ZONE while IP lookup is disabled for the installation
     */
    public LocationMode effectiveMode() {
        LocationMode mode = mode();
        return mode == LocationMode.IP_LOOKUP && !config.ipLookupAvailable() ? LocationMode.TIME_ZONE : mode;
    }

    /**
     * Changes the mode. Leaving IP_LOOKUP deletes the cached IP result.
     *
     * @param actor the signed-in account making the change
     * @param mode the new mode
     * @throws NullPointerException if no account is given
     * @throws DataAccessException if the setting cannot be saved
     */
    public void setMode(Account actor, LocationMode mode) {
        Objects.requireNonNull(actor, "A signed-in account is needed to change the location setting");
        Objects.requireNonNull(mode, "mode");
        if (mode != LocationMode.IP_LOOKUP) {
            settings.remove(CACHE_KEY);
        }
        settings.put(MODE_KEY, mode.name());
        LOGGER.log(Level.INFO, "{0} set the location setting to {1}", new Object[]{actor.getId(), mode});
    }

    /**
     * @return the cached IP result if IP lookup is in effect and the result is at most
     *         {@value #CACHE_HOURS} hours old
     */
    public Optional<ApproximateLocation> cached() {
        if (effectiveMode() != LocationMode.IP_LOOKUP) {
            return Optional.empty();
        }
        return readCache().filter(location -> ageOf(location).compareTo(CACHE_AGE) <= 0);
    }

    /**
     * Runs the location chain of the current mode. Blocks while an IP lookup runs (a
     * few seconds at most); call it off the UI thread.
     *
     * @param force skip the cache ("Locate me"); a result younger than
     *              {@value #THROTTLE_SECONDS} seconds is still reused
     * @return the location, or empty if none could be found or the mode is OFF
     */
    public Optional<ApproximateLocation> locate(boolean force) {
        LocationMode mode = effectiveMode();
        if (mode == LocationMode.OFF) {
            return Optional.empty();
        }
        if (mode == LocationMode.IP_LOOKUP) {
            Optional<ApproximateLocation> cache = readCache();
            Duration limit = force ? THROTTLE : CACHE_AGE;
            if (cache.isPresent() && ageOf(cache.get()).compareTo(limit) <= 0) {
                return cache;
            }
            Optional<ApproximateLocation> found = provider.lookup();
            if (found.isPresent()) {
                if (effectiveMode() != LocationMode.IP_LOOKUP) {
                    // The setting changed while the lookup ran: do not keep or use the result.
                    return effectiveMode() == LocationMode.OFF ? Optional.empty() : fromTimeZone();
                }
                writeCache(found.get());
                return found;
            }
        }
        return fromTimeZone();
    }

    private Optional<ApproximateLocation> fromTimeZone() {
        Optional<GeoPoint> point = zones.lookup(zone);
        return point.map(p -> new ApproximateLocation(p, LocationSource.TIME_ZONE,
                ApproximateLocation.TIME_ZONE_ACCURACY_KM, "", clock.instant(), zone.getId()));
    }

    /**
     * @return true if IP lookups may be made on this installation
     */
    public boolean ipLookupAvailable() {
        return config.ipLookupAvailable();
    }

    /**
     * @return the host IP lookups go to, or empty if IP lookup is disabled
     */
    public String providerHost() {
        return config.providerHost();
    }

    /**
     * @return this computer's time zone
     */
    public ZoneId zone() {
        return zone;
    }

    private Duration ageOf(ApproximateLocation location) {
        Instant now = clock.instant();
        if (location.obtainedAt().isAfter(now.plus(CLOCK_SKEW))) {
            return Duration.ofDays(36500); // stamped in the future: treat as stale
        }
        Duration age = Duration.between(location.obtainedAt(), now);
        return age.isNegative() ? Duration.ZERO : age;
    }

    private Optional<ApproximateLocation> readCache() {
        Optional<String> stored;
        try {
            stored = settings.get(CACHE_KEY);
        } catch (DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not read the cached location", e);
            return Optional.empty();
        }
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        Optional<ApproximateLocation> location = decode(stored.get());
        if (location.isEmpty()) {
            LOGGER.log(Level.WARNING, "Removing an unreadable cached location");
            try {
                settings.remove(CACHE_KEY);
            } catch (DataAccessException e) {
                LOGGER.log(Level.WARNING, "Could not remove the cached location", e);
            }
        }
        return location;
    }

    private void writeCache(ApproximateLocation location) {
        try {
            settings.put(CACHE_KEY, encode(location));
        } catch (DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not cache the location", e);
        }
    }

    static String encode(ApproximateLocation location) {
        return location.point().formattedLatitude() + "|" + location.point().formattedLongitude() + "|"
                + location.accuracyKm() + "|" + location.obtainedAt().toEpochMilli() + "|"
                + location.placeLabel();
    }

    static Optional<ApproximateLocation> decode(String value) {
        String[] parts = value.split("\\|", 5);
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            GeoPoint point = GeoPoint.parse(parts[0], parts[1]);
            double accuracy = Double.parseDouble(parts[2]);
            Instant obtainedAt = Instant.ofEpochMilli(Long.parseLong(parts[3]));
            String label = IpLocationParser.sanitizeLabel(parts[4]);
            if (!label.equals(parts[4]) || (point.latitude() == 0 && point.longitude() == 0)) {
                return Optional.empty();
            }
            return Optional.of(new ApproximateLocation(point, LocationSource.IP, accuracy, label, obtainedAt, ""));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return Optional.empty();
        }
    }
}
