package model.map;

import model.geo.ApproximateLocation;
import model.geo.LocationSource;

/**
 * Turns an approximate location into JSON for the map page:
 * {@code {"lat":n,"lon":n,"accuracyM":n,"kind":"ip"|"timezone"}}. Only numbers and a
 * fixed word; no place names or other text from outside go to the page.
 */
public final class LocationJson {

    private LocationJson() {
    }

    /**
     * @param location the location
     * @return its JSON object
     */
    public static String toJson(ApproximateLocation location) {
        long accuracyM = Math.round(location.accuracyKm() * 1000);
        String kind = location.source() == LocationSource.IP ? "ip" : "timezone";
        return "{\"lat\":" + location.point().formattedLatitude()
                + ",\"lon\":" + location.point().formattedLongitude()
                + ",\"accuracyM\":" + accuracyM
                + ",\"kind\":\"" + kind + "\"}";
    }
}
