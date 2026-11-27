package model.persistence;

import model.security.SecurityEvent;
import model.security.SecurityEventType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link SecurityLogRepository} backed by the {@code security_log} table of a
 * {@link SqliteDatabase} (schema version 2). Triggers in the schema reject every
 * UPDATE and DELETE on the table, so the log stays append-only even for code that
 * bypasses this class.
 */
public final class SqliteSecurityLogRepository implements SecurityLogRepository {

    private static final String COLUMNS = "event_id, occurred_at_ms, event_type, actor, subject, detail";

    private final SqliteDatabase database;

    public SqliteSecurityLogRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public SecurityEvent append(Instant occurredAt, SecurityEventType type, String actor, String subject,
                                String detail) {
        // Validates and cleans the values; the sequence is replaced by the real one below.
        SecurityEvent cleaned = new SecurityEvent(1, occurredAt, type, actor, subject, detail);
        long id = database.inTransaction(c -> {
            try (PreparedStatement insert = c.prepareStatement(
                    "INSERT INTO security_log (occurred_at_ms, event_type, actor, subject, detail)"
                            + " VALUES (?, ?, ?, ?, ?)")) {
                insert.setLong(1, cleaned.occurredAt().toEpochMilli());
                insert.setString(2, cleaned.type().name());
                insert.setString(3, cleaned.actor());
                insert.setString(4, cleaned.subject());
                insert.setString(5, cleaned.detail());
                insert.executeUpdate();
            }
            try (PreparedStatement query = c.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = query.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No event id was assigned");
                }
                return rs.getLong(1);
            }
        });
        return new SecurityEvent(id, cleaned.occurredAt(), cleaned.type(), cleaned.actor(),
                cleaned.subject(), cleaned.detail());
    }

    @Override
    public List<SecurityEvent> findNewestFirst(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit must be positive: " + limit);
        }
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM security_log ORDER BY event_id DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    List<SecurityEvent> events = new ArrayList<>();
                    while (rs.next()) {
                        events.add(toEvent(rs));
                    }
                    return List.copyOf(events);
                }
            }
        });
    }

    @Override
    public Optional<Instant> findLatest(SecurityEventType type, String subject) {
        Objects.requireNonNull(type, "type");
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT MAX(occurred_at_ms) FROM security_log WHERE subject = ? AND event_type = ?")) {
                statement.setString(1, SecurityEvent.clean(subject));
                statement.setString(2, type.name());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    long millis = rs.getLong(1);
                    return rs.wasNull() ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
                }
            }
        });
    }

    @Override
    public int countAfter(SecurityEventType type, String subject, Instant after) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(after, "after");
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT COUNT(*) FROM security_log"
                            + " WHERE subject = ? AND event_type = ? AND occurred_at_ms > ?")) {
                statement.setString(1, SecurityEvent.clean(subject));
                statement.setString(2, type.name());
                statement.setLong(3, after.toEpochMilli());
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    private static SecurityEvent toEvent(ResultSet rs) throws SQLException {
        long id = rs.getLong("event_id");
        try {
            return new SecurityEvent(id, Instant.ofEpochMilli(rs.getLong("occurred_at_ms")),
                    SecurityEventType.valueOf(rs.getString("event_type")), rs.getString("actor"),
                    rs.getString("subject"), rs.getString("detail"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid security log row " + id, e);
        }
    }
}
