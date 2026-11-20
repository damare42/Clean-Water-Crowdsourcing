package model.geo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON parser (RFC 8259) for short responses from outside the app.
 * Objects become {@code Map<String, Object>} (in document order), arrays
 * {@code List<Object>}, strings {@code String}, numbers {@code Double}, true/false
 * {@code Boolean} and null {@code null}.
 * <p>
 * It refuses anything unusual instead of guessing: nesting deeper than
 * {@value #MAX_DEPTH}, strings longer than {@value #MAX_STRING_LENGTH} characters,
 * duplicate keys, numbers that do not fit a finite double, {@code NaN}/{@code Infinity},
 * leading zeros, unescaped control characters, and anything after the value.
 */
public final class JsonReader {

    /** Deepest allowed nesting of objects and arrays. */
    public static final int MAX_DEPTH = 16;
    /** Longest allowed string (after unescaping), in UTF-16 characters. */
    public static final int MAX_STRING_LENGTH = 256;

    /** Thrown for any input that is not acceptable JSON. */
    public static final class JsonException extends IllegalArgumentException {
        JsonException(String message, int position) {
            super(message + " at position " + position);
        }
    }

    private final String text;
    private int pos;

    private JsonReader(String text) {
        this.text = text;
    }

    /**
     * Parses a document whose top-level value must be an object.
     *
     * @param text the JSON text
     * @return the object, keys in document order (unmodifiable)
     * @throws JsonException if the text is not acceptable JSON or not an object
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("The top-level value is not an object", 0);
        }
        return (Map<String, Object>) value;
    }

    /**
     * Parses a document.
     *
     * @param text the JSON text
     * @return the value (see the class description for the types)
     * @throws JsonException if the text is not acceptable JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("No text", 0);
        }
        JsonReader reader = new JsonReader(text);
        reader.skipWhitespace();
        Object value = reader.readValue(0);
        reader.skipWhitespace();
        if (reader.pos != text.length()) {
            throw new JsonException("Unexpected text after the value", reader.pos);
        }
        return value;
    }

    private Object readValue(int depth) {
        if (pos >= text.length()) {
            throw new JsonException("Unexpected end of input", pos);
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject(depth + 1);
            case '[' -> readArray(depth + 1);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield readNumber();
                }
                throw new JsonException("Unexpected character", pos);
            }
        };
    }

    private Map<String, Object> readObject(int depth) {
        checkDepth(depth);
        pos++; // '{'
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return Collections.unmodifiableMap(object);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonException("Expected a key", pos);
            }
            int keyPos = pos;
            String key = readString();
            if (object.containsKey(key)) {
                throw new JsonException("Duplicate key", keyPos);
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, readValue(depth));
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return Collections.unmodifiableMap(object);
            }
            if (c != ',') {
                throw new JsonException("Expected ',' or '}'", pos - 1);
            }
        }
    }

    private List<Object> readArray(int depth) {
        checkDepth(depth);
        pos++; // '['
        List<Object> array = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return Collections.unmodifiableList(array);
        }
        while (true) {
            skipWhitespace();
            array.add(readValue(depth));
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return Collections.unmodifiableList(array);
            }
            if (c != ',') {
                throw new JsonException("Expected ',' or ']'", pos - 1);
            }
        }
    }

    private void checkDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonException("Nested too deeply", pos);
        }
    }

    private String readString() {
        int start = pos;
        pos++; // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new JsonException("Unterminated string", start);
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw new JsonException("Control character in a string", pos - 1);
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(readHex4());
                    default -> throw new JsonException("Bad escape", pos - 1);
                }
            } else {
                out.append(c);
            }
            if (out.length() > MAX_STRING_LENGTH) {
                throw new JsonException("String too long", start);
            }
        }
    }

    private char readHex4() {
        if (pos + 4 > text.length()) {
            throw new JsonException("Unterminated \\u escape", pos);
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(text.charAt(pos + i), 16);
            if (digit < 0) {
                throw new JsonException("Bad \\u escape", pos);
            }
            value = value * 16 + digit;
        }
        pos += 4;
        return (char) value;
    }

    private Double readNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
            if (isDigit(peek())) {
                throw new JsonException("Leading zero", start);
            }
        } else if (isDigit(peek())) {
            digits();
        } else {
            throw new JsonException("Bad number", start);
        }
        if (peek() == '.') {
            pos++;
            if (!isDigit(peek())) {
                throw new JsonException("Bad fraction", start);
            }
            digits();
        }
        if (peek() == 'e' || peek() == 'E') {
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw new JsonException("Bad exponent", start);
            }
            digits();
        }
        if (pos - start > 64) {
            throw new JsonException("Number too long", start);
        }
        double value = Double.parseDouble(text.substring(start, pos));
        if (!Double.isFinite(value)) {
            throw new JsonException("Number out of range", start);
        }
        return value;
    }

    private void digits() {
        while (isDigit(peek())) {
            pos++;
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) {
            throw new JsonException("Unknown literal", pos);
        }
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private char next() {
        if (pos >= text.length()) {
            throw new JsonException("Unexpected end of input", pos);
        }
        return text.charAt(pos++);
    }

    private void expect(char c) {
        if (next() != c) {
            throw new JsonException("Expected '" + c + "'", pos - 1);
        }
    }
}
