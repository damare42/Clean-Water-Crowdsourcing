package model.geo;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Where the IP location lookup goes, from the system property {@value #PROPERTY}:
 * <ul>
 *     <li>not set, or {@value #DEFAULT_KEYWORD} (what {@code mvn javafx:run} passes when
 *     nothing is given): {@value #DEFAULT_URL};</li>
 *     <li>{@code off}, or empty: no IP lookup at all (the kill switch);</li>
 *     <li>an {@code https://} URL: that URL;</li>
 *     <li>an {@code http://} URL on 127.0.0.1, localhost or [::1]: that URL (for tests);</li>
 *     <li>anything else (another {@code http://} URL, a URL with user info, a malformed
 *     value): no IP lookup, and one warning in the log.</li>
 * </ul>
 */
public final class GeoConfig {
    private static final Logger LOGGER = Logger.getLogger(GeoConfig.class.getName());

    /** The system property with the lookup URL. */
    public static final String PROPERTY = "cwc.geo.url";
    /** The lookup URL used when the property is not set. */
    public static final String DEFAULT_URL = "https://ipapi.co/json/";
    /** A property value meaning "not set" (Maven passes it when no URL was given). */
    public static final String DEFAULT_KEYWORD = "default";

    private static final Set<String> LOOPBACK_HOSTS = Set.of("127.0.0.1", "localhost", "[::1]");

    private final URI url;
    private final boolean switchedOff;

    private GeoConfig(URI url, boolean switchedOff) {
        this.url = url;
        this.switchedOff = switchedOff;
    }

    /**
     * @return the configuration from the {@value #PROPERTY} system property
     */
    public static GeoConfig fromSystemProperties() {
        return of(System.getProperty(PROPERTY));
    }

    /**
     * @param value the property value, or null if it is not set
     * @return the configuration for that value
     */
    public static GeoConfig of(String value) {
        if (value == null || value.strip().equalsIgnoreCase(DEFAULT_KEYWORD)
                || value.strip().equals("${" + PROPERTY + "}")) {
            return new GeoConfig(URI.create(DEFAULT_URL), false);
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("off")) {
            return new GeoConfig(null, true);
        }
        Optional<URI> checked = check(trimmed);
        if (checked.isEmpty()) {
            LOGGER.log(Level.WARNING, "Ignoring -D{0}: not an https URL (or an http URL on this computer);"
                    + " IP location lookup is disabled", PROPERTY);
            return new GeoConfig(null, false);
        }
        return new GeoConfig(checked.get(), false);
    }

    /**
     * @return a configuration with IP lookup switched off
     */
    public static GeoConfig off() {
        return new GeoConfig(null, true);
    }

    private static Optional<URI> check(String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || uri.isOpaque() || host.isEmpty() || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            return Optional.empty();
        }
        if (scheme.equals("https") || (scheme.equals("http") && LOOPBACK_HOSTS.contains(host))) {
            return Optional.of(uri);
        }
        return Optional.empty();
    }

    /**
     * @return true if IP lookups may be made
     */
    public boolean ipLookupAvailable() {
        return url != null;
    }

    /**
     * @return the lookup URL, or empty if IP lookup is disabled
     */
    public Optional<URI> url() {
        return Optional.ofNullable(url);
    }

    /**
     * @return true if IP lookup was switched off on purpose ({@code off} or empty)
     */
    public boolean switchedOff() {
        return switchedOff;
    }

    /**
     * @return the host the lookup goes to (with the port if one is given), or empty if
     *         IP lookup is disabled
     */
    public String providerHost() {
        if (url == null) {
            return "";
        }
        return url.getPort() == -1 ? url.getHost() : url.getHost() + ":" + url.getPort();
    }
}
