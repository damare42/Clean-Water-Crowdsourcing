package model.geo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the provider against a local HTTP server on 127.0.0.1; never the internet. */
class IpLocationProviderTest {

    private static final String USER_AGENT = "CleanWaterCrowdsourcing/test (JavaFX desktop app)";
    private static final String NEW_YORK = "{\"latitude\":40.7128,\"longitude\":-74.006,\"city\":\"New York\","
            + "\"region\":\"New York\",\"country_name\":\"United States\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);

    private HttpServer server;
    private volatile Responder responder;
    private final List<Map<String, List<String>>> requestHeaders = new CopyOnWriteArrayList<>();
    private final List<String> requestLines = new CopyOnWriteArrayList<>();
    private final List<LogRecord> warnings = new CopyOnWriteArrayList<>();
    private final Handler logHandler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            warnings.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requestLines.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            requestHeaders.add(Map.copyOf(exchange.getRequestHeaders()));
            try {
                responder.respond(exchange);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "test-geo-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        Logger.getLogger(IpLocationProvider.class.getName()).addHandler(logHandler);
    }

    @AfterEach
    void stopServer() {
        Logger.getLogger(IpLocationProvider.class.getName()).removeHandler(logHandler);
        server.stop(0);
    }

    private URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    private Optional<ApproximateLocation> lookup(String path, Duration timeout) {
        return new IpLocationProvider(IpLocationProvider.newClient(), url(path), timeout, USER_AGENT, CLOCK).lookup();
    }

    private Optional<ApproximateLocation> lookup() {
        return lookup("/json/", IpLocationProvider.TIMEOUT);
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        if (type != null) {
            exchange.getResponseHeaders().add("Content-Type", type);
        }
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Responder json(int status, String body) {
        return exchange -> send(exchange, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void okAnswerIsParsed() {
        responder = json(200, NEW_YORK);
        ApproximateLocation location = lookup().orElseThrow();
        assertEquals(40.7128, location.point().latitude());
        assertEquals("New York, New York, United States", location.placeLabel());
        assertEquals(CLOCK.instant(), location.obtainedAt());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void requestCarriesOnlyTheExpectedHeaders() {
        responder = json(200, NEW_YORK);
        lookup().orElseThrow();
        assertEquals(List.of("GET /json/"), requestLines);
        Map<String, List<String>> headers = requestHeaders.get(0);
        assertEquals(List.of(USER_AGENT), headers.get("User-agent"));
        assertEquals(List.of("application/json"), headers.get("Accept"));
        assertNull(headers.get("Cookie"));
        assertNull(headers.get("Referer"));
        assertNull(headers.get("Authorization"));
        for (String name : headers.keySet()) {
            assertTrue(List.of("User-agent", "Accept", "Host", "Connection", "Content-length").contains(name),
                    "unexpected header " + name + ": " + headers);
        }
    }

    @Test
    void errorStatusesGiveNothing() {
        for (int status : new int[]{429, 500, 404}) {
            responder = json(status, "{\"error\":true,\"reason\":\"RateLimited\"}");
            assertTrue(lookup().isEmpty(), "status " + status);
        }
        assertEquals(3, warnings.size());
        assertFalse(warnings.stream().anyMatch(w -> String.valueOf(w.getParameters() == null ? ""
                : List.of(w.getParameters())).contains("RateLimited")), "the body is never logged");
    }

    @Test
    void redirectsAreNotFollowed() {
        responder = exchange -> {
            if (exchange.getRequestURI().getPath().equals("/moved")) {
                exchange.getResponseHeaders().add("Location", url("/json/").toString());
                send(exchange, 302, null, new byte[0]);
            } else {
                json(200, NEW_YORK).respond(exchange);
            }
        };
        assertTrue(lookup("/moved", IpLocationProvider.TIMEOUT).isEmpty());
        assertEquals(List.of("GET /moved"), requestLines, "exactly one request");
    }

    @Test
    void wrongContentTypeGivesNothing() {
        responder = exchange -> send(exchange, 200, "text/html", NEW_YORK.getBytes(StandardCharsets.UTF_8));
        assertTrue(lookup().isEmpty());
        responder = exchange -> send(exchange, 200, null, NEW_YORK.getBytes(StandardCharsets.UTF_8));
        assertTrue(lookup().isEmpty());
    }

    @Test
    void oversizedBodyGivesNothing() {
        String big = "{\"latitude\":40.7128,\"longitude\":-74.006,\"pad\":\"" + " ".repeat(200) + "\","
                + "\"x\":[" + "1,".repeat(IpLocationProvider.MAX_BODY_BYTES) + "1]}";
        responder = json(200, big);
        assertTrue(lookup().isEmpty());
        responder = json(200, "{\"latitude\":40.7128,\"longitude\":-74.006}" + " ".repeat(2 * 1024 * 1024));
        assertTrue(lookup().isEmpty());
    }

    @Test
    void invalidBodiesGiveNothing() {
        for (String body : List.of("<html>busy</html>", "{\"latitude\":\"abc\",\"longitude\":-74}",
                "{\"latitude\":999,\"longitude\":-74}", "")) {
            responder = json(200, body);
            assertTrue(lookup().isEmpty(), body);
        }
        responder = exchange -> send(exchange, 200, "application/json", new byte[]{'{', '"', (byte) 0xff, '"', ':', '1', '}'});
        assertTrue(lookup().isEmpty(), "malformed UTF-8");
    }

    @Test
    void stallingServerTimesOut() {
        responder = exchange -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            json(200, NEW_YORK).respond(exchange);
        };
        long start = System.nanoTime();
        assertTrue(lookup().isEmpty());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(millis <= 3_500, "took " + millis + " ms");
    }

    @Test
    void closedPortGivesNothing() throws IOException {
        int port;
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = socket.getLocalPort();
        }
        URI closed = URI.create("http://127.0.0.1:" + port + "/json/");
        assertTrue(new IpLocationProvider(IpLocationProvider.newClient(), closed, IpLocationProvider.TIMEOUT,
                USER_AGENT, CLOCK).lookup().isEmpty());
    }
}
