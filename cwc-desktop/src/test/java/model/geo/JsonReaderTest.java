package model.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReaderTest {

    @Test
    void parsesAllValueTypes() {
        Map<String, Object> json = JsonReader.parseObject(
                " {\"s\":\"x\",\"n\":-12.5e1,\"i\":0,\"t\":true,\"f\":false,\"z\":null,"
                        + "\"a\":[1,\"b\",[],{}],\"o\":{\"k\":[null]}}\n");
        assertEquals("x", json.get("s"));
        assertEquals(-125.0, json.get("n"));
        assertEquals(0.0, json.get("i"));
        assertEquals(Boolean.TRUE, json.get("t"));
        assertEquals(Boolean.FALSE, json.get("f"));
        assertTrue(json.containsKey("z"));
        assertNull(json.get("z"));
        assertEquals(Arrays.asList(1.0, "b", List.of(), Map.of()), json.get("a"));
        assertEquals(List.of("s", "n", "i", "t", "f", "z", "a", "o"), List.copyOf(json.keySet()), "document order");
    }

    @Test
    void decodesEscapes() {
        Map<String, Object> json = JsonReader.parseObject(
                "{\"e\":\"q\\\" b\\\\ s\\/ \\b\\f\\n\\r\\t \\u00e9\\u0041 \\ud83c\\uddfa\"}");
        assertEquals("q\" b\\ s/ \b\f\n\r\t \u00e9A \ud83c\uddfa", json.get("e"));
    }

    @Test
    void acceptsNumbersInJsonSyntax() {
        assertEquals(List.of(0.0, -0.0, 1.5, 100.0, 0.25, 37.42301), JsonReader.parse("[0,-0,1.5,1E2,25e-2,37.42301]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "{",
            "{\"a\":1",
            "{\"a\":\"unterminated}",
            "{\"a\":1}}",
            "{\"a\":1} x",
            "{\"a\":1,}",
            "[1,]",
            "{\"a\":1,\"a\":2}",
            "{\"a\":NaN}",
            "{\"a\":Infinity}",
            "{\"a\":-Infinity}",
            "{\"a\":01}",
            "{\"a\":-01}",
            "{\"a\":1.}",
            "{\"a\":.5}",
            "{\"a\":1e}",
            "{\"a\":+1}",
            "{\"a\":1e400}",
            "{\"a\":-1e400}",
            "{\"a\":tru}",
            "{\"a\":nul}",
            "{'a':1}",
            "{a:1}",
            "{\"a\":\"\\x\"}",
            "{\"a\":\"\\u12g4\"}",
            "{\"a\":\"\\u12\"}",
            "{\"a\":\"tab\there\"}",
            "{\"a\":\"nl\nhere\"}",
            "<html><body>Not JSON</body></html>",
            "\u00a0{\"a\":1}",
    })
    void rejectsInvalidDocuments(String text) {
        assertThrows(JsonReader.JsonException.class, () -> JsonReader.parse(text), text);
    }

    @Test
    void rejectsNonObjectRoots() {
        for (String text : List.of("[]", "\"x\"", "1", "true", "null")) {
            assertThrows(JsonReader.JsonException.class, () -> JsonReader.parseObject(text), text);
        }
        assertThrows(JsonReader.JsonException.class, () -> JsonReader.parse(null));
    }

    @Test
    void depthIsLimited() {
        String ok = "[".repeat(JsonReader.MAX_DEPTH) + "]".repeat(JsonReader.MAX_DEPTH);
        JsonReader.parse(ok);
        String bomb = "[".repeat(JsonReader.MAX_DEPTH + 1) + "]".repeat(JsonReader.MAX_DEPTH + 1);
        assertThrows(JsonReader.JsonException.class, () -> JsonReader.parse(bomb));
        String objects = "{\"a\":".repeat(100_000) + "1" + "}".repeat(100_000);
        assertThrows(JsonReader.JsonException.class, () -> JsonReader.parse(objects));
    }

    @Test
    void stringLengthIsLimited() {
        String ok = "x".repeat(JsonReader.MAX_STRING_LENGTH);
        assertEquals(ok, JsonReader.parseObject("{\"s\":\"" + ok + "\"}").get("s"));
        assertThrows(JsonReader.JsonException.class,
                () -> JsonReader.parseObject("{\"s\":\"" + ok + "x\"}"));
        assertThrows(JsonReader.JsonException.class,
                () -> JsonReader.parseObject("{\"" + ok + "x\":1}"), "keys are strings too");
    }

    @Test
    void parsedValuesCannotBeModified() {
        Map<String, Object> json = JsonReader.parseObject("{\"a\":[1]}");
        assertThrows(UnsupportedOperationException.class, () -> json.put("b", 2));
    }
}
