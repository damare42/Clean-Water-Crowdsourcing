package model.geo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoConfigTest {

    @Test
    void unsetMeansTheDefaultProvider() {
        for (String value : new String[]{null, "default", " DEFAULT ", "${cwc.geo.url}"}) {
            GeoConfig config = GeoConfig.of(value);
            assertTrue(config.ipLookupAvailable(), String.valueOf(value));
            assertEquals(Optional.of(URI.create(GeoConfig.DEFAULT_URL)), config.url());
            assertEquals("ipapi.co", config.providerHost());
            assertFalse(config.switchedOff());
        }
    }

    @Test
    void offOrEmptySwitchesIpLookupOff() {
        for (String value : new String[]{"off", "OFF", "", "  "}) {
            GeoConfig config = GeoConfig.of(value);
            assertFalse(config.ipLookupAvailable(), "'" + value + "'");
            assertTrue(config.switchedOff());
            assertEquals("", config.providerHost());
            assertEquals(Optional.empty(), config.url());
        }
        assertFalse(GeoConfig.off().ipLookupAvailable());
    }

    @Test
    void httpsUrlsAreUsed() {
        GeoConfig config = GeoConfig.of("https://ipwho.is/");
        assertEquals(Optional.of(URI.create("https://ipwho.is/")), config.url());
        assertEquals("ipwho.is", config.providerHost());
        assertEquals("example.com:8443", GeoConfig.of("https://example.com:8443/json").providerHost());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8080/json/", "http://localhost/json", "http://[::1]:9/", "HTTP://LOCALHOST/x"})
    void loopbackHttpIsAllowedForTests(String value) {
        assertTrue(GeoConfig.of(value).ipLookupAvailable(), value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://ipapi.co/json/", "http://10.0.0.1/", "http://127.0.0.1.example.com/",
            "https://user:secret@ipapi.co/json/", "https://user@ipapi.co/", "ftp://ipapi.co/",
            "file:///etc/passwd", "ipapi.co/json", "https://", "https://ipapi.co/json#x", "not a url",
            "javascript:alert(1)", "https:ipapi.co"})
    void anythingElseDisablesIpLookup(String value) {
        GeoConfig config = GeoConfig.of(value);
        assertFalse(config.ipLookupAvailable(), value);
        assertFalse(config.switchedOff(), value);
        assertEquals("", config.providerHost());
    }
}
