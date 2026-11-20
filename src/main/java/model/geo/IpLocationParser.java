package model.geo;

import model.GeoPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads the JSON answer of an IP geolocation service. Three response shapes are
 * understood, so the service can be switched by configuration alone:
 * <ul>
 *     <li>ipapi.co: numeric {@code latitude} and {@code longitude}, {@code city},
 *     {@code region}, {@code country_name}; errors have {@code "error": true};</li>
 *     <li>ipwho.is: the same coordinates, {@code city}, {@code region}, {@code country};
 *     errors have {@code "success": false};</li>
 *     <li>ipinfo.io: {@code "loc": "lat,lon"}, {@code city}, {@code region},
 *     {@code country}.</li>
 * </ul>
 * Coordinates must be JSON numbers (or the two decimal halves of {@code loc}) in range;
 * 0,0 means "unknown" and is refused. The place name is built only from the city,
 * region and country, cleaned of control, format and bidirectional characters, with
 * whitespace collapsed, and capped at {@value #MAX_LABEL_LENGTH} characters. It is
 * plain text for labels.
 */
public final class IpLocationParser {

    /** Longest place name kept, in characters. */
    public static final int MAX_LABEL_LENGTH = 80;
    /**
     * Most combining marks kept on one base character. Real place names need at most two
     * (Vietnamese, decomposed: a vowel with a tone mark and a circumflex or breve); more
     * only stack marks on top of each other ("Zalgo" text) and spill over nearby labels.
     */
    public static final int MAX_COMBINING_MARKS = 2;

    private IpLocationParser() {
    }

    /**
     * @param body the response body
     * @param now when the response arrived
     * @return the location, or empty if the body is not an acceptable answer
     */
    public static Optional<ApproximateLocation> parse(String body, Instant now) {
        Objects.requireNonNull(now, "now");
        Map<String, Object> json;
        try {
            json = JsonReader.parseObject(body);
        } catch (JsonReader.JsonException e) {
            return Optional.empty();
        }
        if (Boolean.TRUE.equals(json.get("error")) || Boolean.FALSE.equals(json.get("success"))) {
            return Optional.empty();
        }
        Optional<GeoPoint> point = coordinates(json);
        if (point.isEmpty() || (point.get().latitude() == 0 && point.get().longitude() == 0)) {
            return Optional.empty();
        }
        Object country = json.get("country_name") instanceof String ? json.get("country_name") : json.get("country");
        String label = joinLabel(List.of(text(json.get("city")), text(json.get("region")), text(country)));
        return Optional.of(new ApproximateLocation(point.get(), LocationSource.IP,
                ApproximateLocation.IP_ACCURACY_KM, label, now, ""));
    }

    private static Optional<GeoPoint> coordinates(Map<String, Object> json) {
        try {
            if (json.containsKey("latitude") || json.containsKey("longitude")) {
                if (json.get("latitude") instanceof Double lat && json.get("longitude") instanceof Double lon) {
                    return Optional.of(new GeoPoint(lat, lon));
                }
                return Optional.empty();
            }
            if (json.get("loc") instanceof String loc) {
                String[] parts = loc.split(",", -1);
                if (parts.length == 2) {
                    return Optional.of(GeoPoint.parse(parts[0].strip(), parts[1].strip()));
                }
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static String text(Object value) {
        return value instanceof String s ? clean(s) : "";
    }

    private static String joinLabel(List<String> parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) {
                present.add(part);
            }
        }
        return cap(String.join(", ", present));
    }

    /**
     * Cleans a text from outside for display: removes control and format characters
     * (Unicode categories Cc and Cf, which include the bidirectional overrides), keeps at
     * most {@value #MAX_COMBINING_MARKS} combining marks (Unicode categories Mn, Me and
     * Mc) on each character, turns every run of whitespace into one space and trims, and
     * caps the length at {@value #MAX_LABEL_LENGTH}.
     *
     * @param text any text
     * @return the cleaned text
     */
    public static String sanitizeLabel(String text) {
        return cap(clean(text == null ? "" : text));
    }

    private static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean space = false;
        // Combining marks kept since the last base character.
        int marks = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            int type = Character.getType(cp);
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                space = out.length() > 0;
                marks = 0;
                continue;
            }
            if (type == Character.CONTROL || type == Character.FORMAT || type == Character.UNASSIGNED
                    || type == Character.SURROGATE || type == Character.PRIVATE_USE) {
                continue;
            }
            if (isCombiningMark(type)) {
                if (marks >= MAX_COMBINING_MARKS) {
                    continue;
                }
                marks++;
            } else {
                marks = 0;
            }
            if (space) {
                out.append(' ');
                space = false;
            }
            out.appendCodePoint(cp);
        }
        return out.toString();
    }

    private static boolean isCombiningMark(int type) {
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK
                || type == Character.COMBINING_SPACING_MARK;
    }

    private static String cap(String text) {
        if (text.codePointCount(0, text.length()) <= MAX_LABEL_LENGTH) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, MAX_LABEL_LENGTH)).strip();
    }
}
