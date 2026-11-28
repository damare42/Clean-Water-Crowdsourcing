package model.persistence;

import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceReportDraft;
import model.WaterSourceType;
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
 * Behaviour every {@link ReportRepository} must have. Subclasses supply the
 * implementation under test.
 */
abstract class ReportRepositoryContract {

    protected static final Instant WHEN = Instant.parse("2016-10-11T12:30:45.123Z");

    protected ReportRepository repository;

    /**
     * @return a new, empty repository
     */
    protected abstract ReportRepository createRepository();

    @BeforeEach
    void setUpRepository() {
        repository = createRepository();
    }

    protected static WaterSourceReportDraft draft(String reporter) {
        return new WaterSourceReportDraft(reporter, WHEN, 33.77, -84.39,
                WaterSourceType.Pond, WaterSourceCondition.Waste);
    }

    private List<Integer> numbers() {
        return repository.findAll().stream().map(WaterSourceReport::getReportNumber).toList();
    }

    @Test
    void numberingStartsAtOne() {
        assertEquals(1, repository.nextReportNumber());
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findByNumber(1).isEmpty());
    }

    @Test
    void addAssignsSequentialNumbers() {
        WaterSourceReport first = repository.add(draft("alice"));
        WaterSourceReport second = repository.add(draft("bob"));
        WaterSourceReport third = repository.add(draft("carol"));

        assertEquals(1, first.getReportNumber());
        assertEquals(2, second.getReportNumber());
        assertEquals(3, third.getReportNumber());
        assertEquals(4, repository.nextReportNumber());
        assertEquals(List.of(first, second, third), repository.findAll());
    }

    @Test
    void storedReportKeepsAllFields() {
        WaterSourceReportDraft draft = new WaterSourceReportDraft("alice",
                Instant.parse("2024-02-29T23:59:59.999Z"), -89.999999, 179.123456789,
                WaterSourceType.Spout, WaterSourceCondition.Potable);

        WaterSourceReport added = repository.add(draft);

        assertEquals(draft.withNumber(1), added);
        WaterSourceReport found = repository.findByNumber(1).orElseThrow();
        assertEquals(added, found);
        assertEquals(Instant.parse("2024-02-29T23:59:59.999Z"), found.getReportedAt());
        assertEquals(-89.999999, found.getLatitude());
        assertEquals(179.123456789, found.getLongitude());
        assertEquals(WaterSourceType.Spout, found.getType());
        assertEquals(WaterSourceCondition.Potable, found.getCondition());
        assertEquals("alice", found.getReporter());
    }

    @Test
    void everyTypeAndConditionRoundTrips() {
        for (WaterSourceType type : WaterSourceType.values()) {
            for (WaterSourceCondition condition : WaterSourceCondition.values()) {
                WaterSourceReport added = repository.add(
                        new WaterSourceReportDraft("alice", WHEN, 0, 0, type, condition));
                assertEquals(Optional.of(added), repository.findByNumber(added.getReportNumber()));
            }
        }
        assertEquals(WaterSourceType.values().length * WaterSourceCondition.values().length,
                repository.findAll().size());
    }

    @Test
    void findByNumberOfMissingReportIsEmpty() {
        repository.add(draft("alice"));

        assertTrue(repository.findByNumber(0).isEmpty());
        assertTrue(repository.findByNumber(2).isEmpty());
        assertTrue(repository.findByNumber(-1).isEmpty());
    }

    @Test
    void findAllIsOrderedByNumber() {
        for (int i = 0; i < 12; i++) {
            repository.add(draft("user" + i));
        }

        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            expected.add(i);
        }
        assertEquals(expected, numbers());
    }

    @Test
    void findAllIsUnmodifiableSnapshot() {
        repository.add(draft("alice"));
        List<WaterSourceReport> snapshot = repository.findAll();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(draft("x").withNumber(9)));
        repository.add(draft("bob"));
        assertEquals(1, snapshot.size());
    }

    @Test
    void concurrentAddsGetDistinctConsecutiveNumbers() throws Exception {
        int threads = 4;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String reporter = "user" + t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        repository.add(draft(reporter));
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= threads * perThread; i++) {
            expected.add(i);
        }
        assertEquals(expected, numbers());
        assertEquals(threads * perThread + 1, repository.nextReportNumber());
    }
}
