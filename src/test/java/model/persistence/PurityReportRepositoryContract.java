package model.persistence;

import model.GeoPoint;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterPurityReportDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour every {@link PurityReportRepository} must have.
 */
abstract class PurityReportRepositoryContract {

    protected static final Instant WHEN = Instant.parse("2016-10-11T12:30:45.123Z");

    protected PurityReportRepository repository;

    protected abstract PurityReportRepository createRepository();

    @BeforeEach
    void setUpRepository() {
        repository = createRepository();
    }

    protected static WaterPurityReportDraft draft(String reporter, Instant when) {
        return new WaterPurityReportDraft(reporter, when, new GeoPoint(33.77, -84.39),
                WaterPurityCondition.Unsafe, 12.5, 0.75);
    }

    @Test
    void numberingStartsAtOne() {
        assertEquals(1, repository.nextReportNumber());
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findByNumber(1).isEmpty());
    }

    @Test
    void addAssignsSequentialNumbersAndKeepsAllFields() {
        WaterPurityReportDraft first = new WaterPurityReportDraft("alice",
                Instant.parse("2024-02-29T23:59:59.999Z"), new GeoPoint(-89.999999, 179.123456789),
                WaterPurityCondition.Safe, 0, 1_000_000);
        WaterPurityReport a = repository.add(first);
        WaterPurityReport b = repository.add(draft("bob", WHEN));

        assertEquals(first.withNumber(1), a);
        assertEquals(2, b.getReportNumber());
        assertEquals(3, repository.nextReportNumber());
        assertEquals(List.of(a, b), repository.findAll());
        assertEquals(Optional.of(a), repository.findByNumber(1));
        assertEquals(1_000_000, repository.findByNumber(1).orElseThrow().getContaminantPpm());
        assertTrue(repository.findByNumber(3).isEmpty());
    }

    @Test
    void everyConditionRoundTrips() {
        for (WaterPurityCondition condition : WaterPurityCondition.values()) {
            WaterPurityReport added = repository.add(new WaterPurityReportDraft("alice", WHEN,
                    new GeoPoint(0, 0), condition, 0.000001, 999_999.999999));
            assertEquals(Optional.of(added), repository.findByNumber(added.getReportNumber()));
        }
    }

    @Test
    void findReportedBetweenIsHalfOpenAndOrdered() {
        Instant start = Instant.parse("2016-01-01T00:00:00Z");
        Instant end = Instant.parse("2017-01-01T00:00:00Z");
        WaterPurityReport before = repository.add(draft("a", start.minusMillis(1)));
        WaterPurityReport atStart = repository.add(draft("b", start));
        WaterPurityReport inside = repository.add(draft("c", WHEN));
        WaterPurityReport atEnd = repository.add(draft("d", end));
        WaterPurityReport lastMilli = repository.add(draft("e", end.minusMillis(1)));

        assertEquals(List.of(atStart, inside, lastMilli), repository.findReportedBetween(start, end));
        assertEquals(List.of(before), repository.findReportedBetween(Instant.EPOCH, start));
        assertEquals(List.of(atEnd), repository.findReportedBetween(end, end.plusSeconds(1)));
        assertTrue(repository.findReportedBetween(end, end).isEmpty());
    }

    @Test
    void findAllIsUnmodifiableSnapshot() {
        repository.add(draft("alice", WHEN));
        List<WaterPurityReport> snapshot = repository.findAll();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        repository.add(draft("bob", WHEN));
        assertEquals(1, snapshot.size());
    }

    @Test
    void concurrentAddsGetDistinctConsecutiveNumbers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 10; i++) {
                        repository.add(draft("user", WHEN));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        List<Integer> numbers = repository.findAll().stream().map(WaterPurityReport::getReportNumber).toList();
        for (int i = 0; i < 40; i++) {
            assertEquals(i + 1, numbers.get(i));
        }
        assertEquals(41, repository.nextReportNumber());
    }
}
