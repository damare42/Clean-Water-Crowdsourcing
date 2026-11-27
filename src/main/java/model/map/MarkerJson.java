package model.map;

import model.ReportField;

import java.util.List;

/**
 * Turns map markers into JSON for the map page. The output is plain ASCII: every
 * character outside printable ASCII, and {@code < > & '}, is written as a
 * {@code \}{@code uXXXX} escape, so the text is safe to hand to JavaScript's
 * {@code JSON.parse} and stays harmless even if it ends up inside HTML or a script.
 * <p>
 * Format: an array of {@code {"kind", "id", "lat", "lon", "title", "status",
 * "fields": [{"label", "value"}, ...]}} objects; {@code status} is one of the
 * {@link MapMarker.Status#word() status words}.
 */
public final class MarkerJson {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private MarkerJson() {
    }

    /**
     * @param markers the markers to write
     * @return a JSON array with one object per marker, in the same order
     */
    public static String toJson(List<MapMarker> markers) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < markers.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            appendMarker(out, markers.get(i));
        }
        return out.append(']').toString();
    }

    private static void appendMarker(StringBuilder out, MapMarker marker) {
        out.append("{\"kind\":");
        appendString(out, marker.kind());
        out.append(",\"id\":").append(marker.id());
        out.append(",\"lat\":").append(marker.position().latitude());
        out.append(",\"lon\":").append(marker.position().longitude());
        out.append(",\"title\":");
        appendString(out, marker.title());
        out.append(",\"status\":");
        appendString(out, marker.status().word());
        out.append(",\"fields\":[");
        List<ReportField> fields = marker.fields();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"label\":");
            appendString(out, fields.get(i).label());
            out.append(",\"value\":");
            appendString(out, fields.get(i).value());
            out.append('}');
        }
        out.append("]}");
    }

    /**
     * @param value any string
     * @return the string as a quoted JSON string literal, in plain ASCII
     */
    public static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        appendString(out, value);
        return out.toString();
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e || c == '<' || c == '>' || c == '&' || c == '\'') {
                        out.append("\\u").append(HEX[(c >> 12) & 0xf]).append(HEX[(c >> 8) & 0xf])
                                .append(HEX[(c >> 4) & 0xf]).append(HEX[c & 0xf]);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
