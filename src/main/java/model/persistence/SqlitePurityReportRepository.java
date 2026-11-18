package model.persistence;

import model.GeoPoint;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterPurityReportDraft;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link PurityReportRepository} backed by the {@code water_purity_reports} table of
 * a {@link SqliteDatabase} (schema version 2). Times are stored as milliseconds since
 * the epoch (UTC); numbers are assigned like {@link SqliteReportRepository} does.
 */
public final class SqlitePurityReportRepository implements PurityReportRepository {

    private static final String COLUMNS = "report_number, reporter, reported_at_ms, latitude,"
            + " longitude, overall_condition, virus_ppm, contaminant_ppm";

    private final SqliteDatabase database;

    public SqlitePurityReportRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public WaterPurityReport add(WaterPurityReportDraft draft) {
        Objects.requireNonNull(draft, "draft");
        int number = database.inTransaction(c -> {
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO water_purity_reports (" + COLUMNS + ") VALUES ("
                            + "(SELECT COALESCE(MAX(report_number), 0) + 1 FROM water_purity_reports),"
                            + " ?, ?, ?, ?, ?, ?, ?)")) {
                insert.setString(1, draft.reporter());
                insert.setLong(2, draft.reportedAt().toEpochMilli());
                insert.setDouble(3, draft.location().latitude());
                insert.setDouble(4, draft.location().longitude());
                insert.setString(5, draft.condition().name());
                insert.setDouble(6, draft.virusPpm());
                insert.setDouble(7, draft.contaminantPpm());
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
    public List<WaterPurityReport> findAll() {
        return query("SELECT " + COLUMNS + " FROM water_purity_reports ORDER BY report_number");
    }

    @Override
    public List<WaterPurityReport> findReportedBetween(Instant from, Instant until) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(until, "until");
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement("SELECT " + COLUMNS
                    + " FROM water_purity_reports WHERE reported_at_ms >= ? AND reported_at_ms < ?"
                    + " ORDER BY report_number")) {
                statement.setLong(1, from.toEpochMilli());
                statement.setLong(2, until.toEpochMilli());
                return readAll(statement);
            }
        });
    }

    @Override
    public Optional<WaterPurityReport> findByNumber(int reportNumber) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM water_purity_reports WHERE report_number = ?")) {
                statement.setInt(1, reportNumber);
                return readAll(statement).stream().findFirst();
            }
        });
    }

    @Override
    public int nextReportNumber() {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT COALESCE(MAX(report_number), 0) + 1 FROM water_purity_reports");
                 ResultSet rs = statement.executeQuery()) {
                rs.next();
                return Math.toIntExact(rs.getLong(1));
            }
        });
    }

    private List<WaterPurityReport> query(String sql) {
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(sql)) {
                return readAll(statement);
            }
        });
    }

    private static List<WaterPurityReport> readAll(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            List<WaterPurityReport> reports = new ArrayList<>();
            while (rs.next()) {
                reports.add(toReport(rs));
            }
            return List.copyOf(reports);
        }
    }

    private static WaterPurityReport toReport(ResultSet rs) throws SQLException {
        int number = rs.getInt("report_number");
        try {
            return new WaterPurityReport(number, rs.getString("reporter"),
                    Instant.ofEpochMilli(rs.getLong("reported_at_ms")),
                    new GeoPoint(rs.getDouble("latitude"), rs.getDouble("longitude")),
                    WaterPurityCondition.valueOf(rs.getString("overall_condition")),
                    rs.getDouble("virus_ppm"), rs.getDouble("contaminant_ppm"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid purity report row " + number, e);
        }
    }
}
