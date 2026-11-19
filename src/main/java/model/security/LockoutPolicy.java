package model.security;

import java.time.Duration;
import java.util.Objects;

/**
 * When repeated failed logins lock an id: after {@code maxFailures} failed attempts
 * for the same (normalized) id within {@code window}, further attempts for that id are
 * refused for {@code lockDuration}, whether or not the id exists. Only failures since
 * the last successful login and since the end of the last lock count.
 *
 * @param maxFailures  failed attempts that trigger a lock, at least 1
 * @param window       how far back failed attempts count
 * @param lockDuration how long a lock lasts
 */
public record LockoutPolicy(int maxFailures, Duration window, Duration lockDuration) {

    /** Failed attempts that lock an id by default. */
    public static final int DEFAULT_MAX_FAILURES = 5;
    /** How far back failed attempts count by default. */
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);
    /** How long a lock lasts by default. */
    public static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(15);

    /** 5 failures within 15 minutes lock the id for 15 minutes. */
    public static final LockoutPolicy DEFAULT =
            new LockoutPolicy(DEFAULT_MAX_FAILURES, DEFAULT_WINDOW, DEFAULT_LOCK_DURATION);

    public LockoutPolicy {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (maxFailures < 1 || window.isNegative() || window.isZero()
                || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Invalid lockout policy: " + maxFailures + ", "
                    + window + ", " + lockDuration);
        }
    }
}
