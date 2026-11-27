package model.geo;

import model.Account;
import model.AccountType;
import model.GeoPoint;
import model.persistence.InMemorySettingsRepository;
import model.persistence.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import testutil.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationServiceTest {

    private static final TimeZoneLocations ZONES = TimeZoneLocations.load();
    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");
    private static final GeoPoint ATLANTA = new GeoPoint(33.749, -84.388);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T10:00:00Z"));
    private InMemorySettingsRepository settings;
    private FakeProvider provider;
    private final Account admin = Account.create(AccountType.Admin, "root", "Root", "Admin", "r@example.com", "", "");

    /** Counts calls; answers Atlanta, or nothing when {@code fail} is set. */
    private final class FakeProvider implements LocationProvider {
        int calls;
        boolean fail;

        @Override
        public Optional<ApproximateLocation> lookup() {
            calls++;
            if (fail) {
                return Optional.empty();
            }
            return Optional.of(new ApproximateLocation(ATLANTA, LocationSource.IP, ApproximateLocation.IP_ACCURACY_KM,
                    "Atlanta, Georgia, United States", clock.instant(), ""));
        }
    }

    @BeforeEach
    void setUp() {
        settings = new InMemorySettingsRepository();
        provider = new FakeProvider();
    }

    private LocationService service(ZoneId zone, GeoConfig config) {
        return new LocationService(settings, provider, ZONES, zone, clock, config);
    }

    private LocationService service() {
        return service(NEW_YORK_ZONE, GeoConfig.of(null));
    }

    @Test
    void defaultModeLooksUpTheIpAddress() {
        LocationService service = service();
        assertEquals(LocationMode.IP_LOOKUP, service.mode());
        ApproximateLocation location = service.locate(false).orElseThrow();
        assertEquals(LocationSource.IP, location.source());
        assertEquals(ATLANTA, location.point());
        assertEquals(1, provider.calls);
        assertEquals("ipapi.co", service.providerHost());
        assertTrue(service.ipLookupAvailable());
    }

    @Test
    void freshCacheMakesNoCall() {
        LocationService service = service();
        service.locate(false);
        clock.advance(Duration.ofHours(23));
        ApproximateLocation again = service.locate(false).orElseThrow();
        assertEquals(1, provider.calls);
        assertEquals("Atlanta, Georgia, United States", again.placeLabel());
        assertEquals(Optional.of(again), service.cached());
    }

    @Test
    void cacheSurvivesANewServiceOnTheSameSettings() {
        service().locate(false);
        assertEquals(ATLANTA, service().cached().orElseThrow().point());
        assertEquals(ATLANTA, service().locate(false).orElseThrow().point());
        assertEquals(1, provider.calls);
    }

    @Test
    void staleCacheCallsTheProvider() {
        LocationService service = service();
        service.locate(false);
        clock.advance(Duration.ofHours(24).plusSeconds(1));
        assertEquals(Optional.empty(), service.cached());
        service.locate(false);
        assertEquals(2, provider.calls);
    }

    @Test
    void forceSkipsTheCacheButIsThrottled() {
        LocationService service = service();
        service.locate(false);
        clock.advance(Duration.ofSeconds(30));
        service.locate(true);
        assertEquals(1, provider.calls, "a result under 60 s old is reused");
        clock.advance(Duration.ofSeconds(31));
        service.locate(true);
        assertEquals(2, provider.calls);
        clock.advance(Duration.ofMinutes(5));
        service.locate(false);
        assertEquals(2, provider.calls, "the forced result refreshed the cache");
    }

    @Test
    void providerFailureFallsBackToTheTimeZone() {
        provider.fail = true;
        ApproximateLocation location = service().locate(false).orElseThrow();
        assertEquals(LocationSource.TIME_ZONE, location.source());
        assertEquals(40.714, location.point().latitude(), 0.01);
        assertEquals(ApproximateLocation.TIME_ZONE_ACCURACY_KM, location.accuracyKm());
        assertEquals("America/New_York", location.zoneId());
        assertEquals(Optional.empty(), settings.get(LocationService.CACHE_KEY), "failures are not cached");
    }

    @Test
    void noTimeZonePositionGivesNothing() {
        provider.fail = true;
        assertEquals(Optional.empty(), service(ZoneId.of("Etc/UTC"), GeoConfig.of(null)).locate(false));
    }

    @Test
    void timeZoneModeNeverCallsTheProvider() {
        LocationService service = service();
        service.locate(false);
        service.setMode(admin, LocationMode.TIME_ZONE);
        assertEquals(Optional.empty(), settings.get(LocationService.CACHE_KEY), "leaving IP lookup clears the cache");
        assertEquals(LocationSource.TIME_ZONE, service.locate(false).orElseThrow().source());
        assertEquals(LocationSource.TIME_ZONE, service.locate(true).orElseThrow().source());
        assertEquals(Optional.empty(), service.cached());
        assertEquals(1, provider.calls);
    }

    @Test
    void offModeDoesNothingAndClearsTheCache() {
        LocationService service = service();
        service.locate(false);
        service.setMode(admin, LocationMode.OFF);
        assertEquals(LocationMode.OFF, service.mode());
        assertEquals(Optional.empty(), service.locate(false));
        assertEquals(Optional.empty(), service.locate(true));
        assertEquals(Optional.empty(), service.cached());
        assertEquals(Optional.empty(), settings.get(LocationService.CACHE_KEY));
        assertEquals(1, provider.calls);
    }

    @Test
    void modeIsStored() {
        service().setMode(admin, LocationMode.OFF);
        assertEquals(LocationMode.OFF, service().mode());
        assertEquals(Optional.of("OFF"), settings.get(LocationService.MODE_KEY));
        service().setMode(admin, LocationMode.IP_LOOKUP);
        assertEquals(LocationMode.IP_LOOKUP, service().mode());
    }

    @Test
    void unknownStoredModeMeansTheDefault() {
        settings.put(LocationService.MODE_KEY, "SOMETHING");
        assertEquals(LocationMode.IP_LOOKUP, service().mode());
    }

    @Test
    void killSwitchActsAsTimeZoneMode() {
        LocationService service = service(NEW_YORK_ZONE, GeoConfig.of("off"));
        assertEquals(LocationMode.IP_LOOKUP, service.mode());
        assertEquals(LocationMode.TIME_ZONE, service.effectiveMode());
        assertEquals(LocationSource.TIME_ZONE, service.locate(true).orElseThrow().source());
        assertEquals(0, provider.calls);
        assertTrue(!service.ipLookupAvailable() && service.providerHost().isEmpty());
        settings.put(LocationService.CACHE_KEY, LocationService.encode(new ApproximateLocation(ATLANTA,
                LocationSource.IP, 25, "Atlanta", clock.instant(), "")));
        assertEquals(Optional.empty(), service.cached(), "no IP result is used while IP lookup is off");
    }

    @Test
    void corruptCacheIsRemoved() {
        for (String bad : new String[]{"garbage", "1|2|3", "999|0|25|1|x", "40|-74|NaN|1|x", "40|-74|25|x|y",
                "40|-74|25|1|bad‮label", "0|0|25|1|x", "40|-74|0|1|x"}) {
            settings.put(LocationService.CACHE_KEY, bad);
            LocationService service = service();
            assertEquals(Optional.empty(), service.cached(), bad);
            assertEquals(Optional.empty(), settings.get(LocationService.CACHE_KEY), bad);
        }
    }

    @Test
    void cacheRoundTripsAndKeepsPipesInTheLabel() {
        ApproximateLocation location = new ApproximateLocation(ATLANTA, LocationSource.IP, 25, "A|B, C",
                clock.instant(), "");
        assertEquals(Optional.of(location), LocationService.decode(LocationService.encode(location)));
    }

    @Test
    void futureStampedCacheIsNotUsed() {
        settings.put(LocationService.CACHE_KEY, LocationService.encode(new ApproximateLocation(ATLANTA,
                LocationSource.IP, 25, "Atlanta", clock.instant().plus(Duration.ofDays(2)), "")));
        assertEquals(Optional.empty(), service().cached());
        service().locate(false);
        assertEquals(1, provider.calls);
    }

    @Test
    void changingTheModeNeedsAnAccount() {
        assertThrows(NullPointerException.class, () -> service().setMode(null, LocationMode.OFF));
        assertThrows(NullPointerException.class, () -> service().setMode(admin, null));
        assertEquals(LocationMode.IP_LOOKUP, service().mode());
    }

    @Test
    void resultArrivingAfterSwitchingOffIsDropped() {
        LocationService[] holder = new LocationService[1];
        LocationProvider switchingProvider = () -> {
            holder[0].setMode(admin, LocationMode.OFF);
            return Optional.of(new ApproximateLocation(ATLANTA, LocationSource.IP, 25, "", clock.instant(), ""));
        };
        holder[0] = new LocationService(settings, switchingProvider, ZONES, NEW_YORK_ZONE, clock, GeoConfig.of(null));
        assertEquals(Optional.empty(), holder[0].locate(false));
        assertEquals(Optional.empty(), settings.get(LocationService.CACHE_KEY));
    }

    @Test
    void switchingOffJustBeforeTheCacheWriteLeavesNoCachedLocation() throws InterruptedException {
        LocationService[] holder = new LocationService[1];
        Thread[] switcher = new Thread[1];
        // Starts setMode(OFF) on another thread at the moment locate() writes the cache,
        // and gives it time to run first if nothing stops it.
        SettingsRepository hooked = new SettingsRepository() {
            @Override
            public Optional<String> get(String key) {
                return settings.get(key);
            }

            @Override
            public void remove(String key) {
                settings.remove(key);
            }

            @Override
            public void put(String key, String value) {
                if (key.equals(LocationService.CACHE_KEY) && switcher[0] == null) {
                    switcher[0] = new Thread(() -> holder[0].setMode(admin, LocationMode.OFF));
                    switcher[0].start();
                    try {
                        switcher[0].join(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                settings.put(key, value);
            }
        };
        holder[0] = new LocationService(hooked, provider, ZONES, NEW_YORK_ZONE, clock, GeoConfig.of(null));

        holder[0].locate(false);
        assertTrue(switcher[0] != null, "the cache write should have been attempted");
        switcher[0].join(5000);

        assertEquals(LocationMode.OFF, holder[0].mode());
        assertEquals(Optional.empty(), settings.get(LocationService.CACHE_KEY),
                "Off must never leave a cached location behind");
    }
}
