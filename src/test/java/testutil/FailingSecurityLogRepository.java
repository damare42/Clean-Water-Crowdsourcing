package testutil;

import model.persistence.DataAccessException;
import model.persistence.SecurityLogRepository;
import model.security.SecurityEvent;
import model.security.SecurityEventType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A security log repository that cannot store events: every append throws
 * {@link DataAccessException}. Reads find nothing. Counts the attempted appends.
 */
public final class FailingSecurityLogRepository implements SecurityLogRepository {

    /** The message of the exceptions thrown by {@link #append}. */
    public static final String MESSAGE = "security log is read-only";

    private int appendAttempts;

    @Override
    public SecurityEvent append(Instant occurredAt, SecurityEventType type, String actor,
                                String subject, String detail) {
        appendAttempts++;
        throw new DataAccessException(MESSAGE);
    }

    @Override
    public List<SecurityEvent> findNewestFirst(int limit) {
        return List.of();
    }

    @Override
    public Optional<Instant> findLatest(SecurityEventType type, String subject) {
        return Optional.empty();
    }

    @Override
    public int countAfter(SecurityEventType type, String subject, Instant after) {
        return 0;
    }

    /**
     * @return how many times {@link #append} was called
     */
    public int appendAttempts() {
        return appendAttempts;
    }
}
