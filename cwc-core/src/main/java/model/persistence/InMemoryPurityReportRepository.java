package model.persistence;

import model.WaterPurityReport;
import model.WaterPurityReportDraft;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A {@link PurityReportRepository} that keeps reports in memory, for tests. Thread-safe.
 */
public final class InMemoryPurityReportRepository implements PurityReportRepository {

    private final TreeMap<Integer, WaterPurityReport> reports = new TreeMap<>();

    @Override
    public synchronized WaterPurityReport add(WaterPurityReportDraft draft) {
        Objects.requireNonNull(draft, "draft");
        WaterPurityReport report = draft.withNumber(nextReportNumber());
        reports.put(report.getReportNumber(), report);
        return report;
    }

    @Override
    public synchronized List<WaterPurityReport> findAll() {
        return List.copyOf(reports.values());
    }

    @Override
    public synchronized List<WaterPurityReport> findReportedBetween(Instant from, Instant until) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(until, "until");
        return reports.values().stream()
                .filter(r -> !r.getReportedAt().isBefore(from) && r.getReportedAt().isBefore(until))
                .toList();
    }

    @Override
    public synchronized Optional<WaterPurityReport> findByNumber(int reportNumber) {
        return Optional.ofNullable(reports.get(reportNumber));
    }

    @Override
    public synchronized int nextReportNumber() {
        return reports.isEmpty() ? 1 : reports.lastKey() + 1;
    }
}
