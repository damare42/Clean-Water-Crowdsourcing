package model.persistence;

import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceReportDraft;
import model.WaterSourceType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link ReportRepository} backed by the {@code water_source_reports} table of a
 * {@link SqliteDatabase}. The report time is stored as milliseconds since the epoch
 * (UTC). New report numbers are computed as the highest number plus one inside the
 * same INSERT statement, so numbering is atomic and matches the in-memory repository.
 */
public final class SqliteReportRepository implements ReportRepository {

    private static final String COLUMNS = "report_number, reporter, reported_at_ms, latitude,"
            + " longitude, water_type, water_condition";

    private final SqliteDatabase database;

    public SqliteReportRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public WaterSourceReport add(WaterSourceReportDraft draft) {
        Objects.requireNonNull(draft, "draft");
        int number = database.inTransaction(c -> {
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO water_source_reports (" + COLUMNS + ") VALUES ("
                            + "(SELECT COALESCE(MAX(report_number), 0) + 1 FROM water_source_reports),"
                            + " ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, draft.reporter());
                insert.setLong(2, draft.reportedAt().toEpochMilli());
                insert.setDouble(3, draft.latitude());
                insert.setDouble(4, draft.longitude());
                insert.setString(5, draft.type().name());
                insert.setString(6, draft.condition().name());
                insert.executeUpdate();
            }
            // report_number is the rowid, so this is the number just assigned.
            try (PreparedStatement query = c.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = query.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No report number was assigned");
                }
                return Math.toIntExact(rs.getLong(1));
            }
        });
        return draft.withNumber(number);
    }

    @Override
    public List<WaterSourceReport> findAll() {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM water_source_reports ORDER BY report_number");
                 ResultSet rs = statement.executeQuery()) {
                List<WaterSourceReport> reports = new ArrayList<>();
                while (rs.next()) {
                    reports.add(toReport(rs));
                }
                return List.copyOf(reports);
            }
        });
    }

    @Override
    public Optional<WaterSourceReport> findByNumber(int reportNumber) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM water_source_reports WHERE report_number = ?")) {
                statement.setInt(1, reportNumber);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(toReport(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public int nextReportNumber() {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT COALESCE(MAX(report_number), 0) + 1 FROM water_source_reports");
                 ResultSet rs = statement.executeQuery()) {
                rs.next();
                return Math.toIntExact(rs.getLong(1));
            }
        });
    }

    private static WaterSourceReport toReport(ResultSet rs) throws SQLException {
        int number = rs.getInt("report_number");
        try {
            return new WaterSourceReport(number, rs.getString("reporter"),
                    Instant.ofEpochMilli(rs.getLong("reported_at_ms")),
                    rs.getDouble("latitude"), rs.getDouble("longitude"),
                    WaterSourceType.valueOf(rs.getString("water_type")),
                    WaterSourceCondition.valueOf(rs.getString("water_condition")));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid report row " + number, e);
        }
    }
}
