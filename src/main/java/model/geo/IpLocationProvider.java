package model.geo;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Looks up the approximate location of this computer with one HTTPS GET to an IP
 * geolocation service (see {@link GeoConfig}).
 * <p>
 * The request carries only {@code User-Agent} and {@code Accept: application/json}:
 * no account data, no cookies, no referrer, no added query parameters. Redirects are
 * not followed. Only an HTTP 200 answer with a JSON content type is read, and at most
 * {@value #MAX_BODY_BYTES} bytes of it; the whole exchange must finish within the
 * timeout. Every failure is logged as one warning (cause and status, never the body)
 * and gives an empty result.
 */
public final class IpLocationProvider implements LocationProvider {
    private static final Logger LOGGER = Logger.getLogger(IpLocationProvider.class.getName());

    /** Largest response body accepted. */
    public static final int MAX_BODY_BYTES = 16 * 1024;
    /** Time allowed to connect. */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    /** Time allowed for the whole lookup. */
    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient client;
    private final URI url;
    private final Duration timeout;
    private final String userAgent;
    private final Clock clock;

    /**
     * @param client the HTTP client, e.g. {@link #newClient()}
     * @param url the lookup URL
     * @param timeout the time allowed for the whole lookup
     * @param userAgent the User-Agent header
     * @param clock gives the time of the result
     */
    public IpLocationProvider(HttpClient client, URI url, Duration timeout, String userAgent, Clock clock) {
        this.client = Objects.requireNonNull(client, "client");
        this.url = Objects.requireNonNull(url, "url");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @return an HTTP client for lookups: the system proxy settings, no redirects, no
     *         cookies, a {@link #CONNECT_TIMEOUT} connect timeout, and HTTP/1.1 (so no
     *         protocol-upgrade headers are added to the request either)
     */
    public static HttpClient newClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    @Override
    public Optional<ApproximateLocation> lookup() {
        HttpRequest request = HttpRequest.newBuilder(url)
                .GET()
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build();
        AtomicInteger status = new AtomicInteger(-1);
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, info -> {
            status.set(info.statusCode());
            String type = info.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (info.statusCode() != 200 || !type.contains("json")) {
                return HttpResponse.BodySubscribers.replacing(null);
            }
            return new CappedBody(MAX_BODY_BYTES);
        });
        try {
            HttpResponse<byte[]> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200 || response.body() == null) {
                return fail("unexpected answer", status.get());
            }
            String body = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(response.body())).toString();
            Optional<ApproximateLocation> location = IpLocationParser.parse(body, clock.instant());
            if (location.isEmpty()) {
                return fail("unusable response", status.get());
            }
            return location;
        } catch (TimeoutException e) {
            future.cancel(true);
            return fail(e.getClass().getSimpleName(), status.get());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return fail(e.getClass().getSimpleName(), status.get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return fail(cause.getClass().getSimpleName(), status.get());
        } catch (CharacterCodingException e) {
            return fail(e.getClass().getSimpleName(), status.get());
        }
    }

    private Optional<ApproximateLocation> fail(String cause, int status) {
        LOGGER.log(Level.WARNING, "IP location lookup at {0} failed: {1} (HTTP status {2})",
                new Object[]{url.getHost(), cause, status < 0 ? "none" : String.valueOf(status)});
        return Optional.empty();
    }

    /** Collects a response body, failing as soon as it gets larger than the cap. */
    private static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int cap;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private Flow.Subscription subscription;

        CappedBody(int cap) {
            this.cap = cap;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                if (bytes.size() + item.remaining() > cap) {
                    result.completeExceptionally(new BodyTooLargeException());
                    subscription.cancel();
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }

    /** The response body was larger than {@link #MAX_BODY_BYTES}. */
    static final class BodyTooLargeException extends java.io.IOException {
        BodyTooLargeException() {
            super("Response body too large");
        }
    }
}
