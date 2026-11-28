package model.persistence;

import model.WaterSourceReport;
import model.WaterSourceReportDraft;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A {@link ReportRepository} that keeps reports in memory, for tests. Thread-safe.
 */
public final class InMemoryReportRepository implements ReportRepository {

    private final TreeMap<Integer, WaterSourceReport> reports = new TreeMap<>();

    @Override
    public synchronized WaterSourceReport add(WaterSourceReportDraft draft) {
        Objects.requireNonNull(draft, "draft");
        WaterSourceReport report = draft.withNumber(nextReportNumber());
        reports.put(report.getReportNumber(), report);
        return report;
    }

    @Override
    public synchronized List<WaterSourceReport> findAll() {
        return List.copyOf(reports.values());
    }

    @Override
    public synchronized Optional<WaterSourceReport> findByNumber(int reportNumber) {
        return Optional.ofNullable(reports.get(reportNumber));
    }

    @Override
    public synchronized int nextReportNumber() {
        return reports.isEmpty() ? 1 : reports.lastKey() + 1;
    }
}
