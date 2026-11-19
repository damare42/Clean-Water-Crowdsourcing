package model.persistence;

import model.security.SecurityEvent;
import model.security.SecurityEventType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The append-only security log: events can be added and read, never changed or
 * removed. Text is stored as {@link SecurityEvent} cleans it.
 */
public interface SecurityLogRepository {

    /**
     * Adds an event at the end of the log.
     *
     * @param occurredAt when it happened
     * @param type what happened
     * @param actor the id of the account that acted, or null
     * @param subject the id acted on or attempted, or null
     * @param detail more information, or null; never a password
     * @return the stored event with its sequence number
     */
    SecurityEvent append(Instant occurredAt, SecurityEventType type, String actor, String subject,
                         String detail);

    /**
     * @param limit the most events to return (at least 1)
     * @return the newest events, newest first (unmodifiable)
     */
    List<SecurityEvent> findNewestFirst(int limit);

    /**
     * @param type an event type
     * @param subject a subject, compared exactly (after cleaning)
     * @return the time of the latest such event, or empty if there is none
     */
    Optional<Instant> findLatest(SecurityEventType type, String subject);

    /**
     * @param type an event type
     * @param subject a subject, compared exactly (after cleaning)
     * @param after only events strictly after this time count
     * @return how many such events there are
     */
    int countAfter(SecurityEventType type, String subject, Instant after);
}
