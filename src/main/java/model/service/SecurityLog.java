package model.service;

import model.Account;
import model.Permission;
import model.persistence.DataAccessException;
import model.persistence.SecurityLogRepository;
import model.security.SecurityEvent;
import model.security.SecurityEventType;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes and reads the security log: logins, registrations, administrator actions and
 * permission denials. Events are stamped with the injected clock. Writing an event
 * that cannot be saved throws {@link DataAccessException}, so security-relevant
 * actions fail rather than go unrecorded (except permission denials, which are
 * refused anyway; see {@link AccessControl}).
 */
public final class SecurityLog {

    private static final Logger LOGGER = Logger.getLogger(SecurityLog.class.getName());

    /** The most events {@link #recentEvents} returns. */
    public static final int MAX_EVENTS = 1000;

    private final SecurityLogRepository repository;
    private final Clock clock;

    /**
     * @param repository where events are stored
     * @param clock supplies the time of events
     */
    public SecurityLog(SecurityLogRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @return the current time of this log's clock
     */
    public Instant now() {
        return clock.instant();
    }

    /**
     * Appends an event stamped with the current time.
     *
     * @param type what happened
     * @param actor the id of the account that acted, or null
     * @param subject the id acted on or attempted, or null
     * @param detail more information, or null; never a password
     * @return the stored event
     * @throws DataAccessException if the event cannot be saved
     */
    public SecurityEvent record(SecurityEventType type, String actor, String subject, String detail) {
        SecurityEvent event = repository.append(now(), type, actor, subject, detail);
        LOGGER.log(Level.FINE, "Security event {0}: actor={1} subject={2} {3}",
                new Object[]{type, event.actor(), event.subject(), event.detail()});
        return event;
    }

    /**
     * Records that an account (or nobody) was refused a permission.
     *
     * @param actor the account that tried, or null if nobody was logged in
     * @param permission the permission it lacked
     */
    public void permissionDenied(Account actor, Permission permission) {
        record(SecurityEventType.PERMISSION_DENIED, actor == null ? "" : actor.getId(), "",
                (actor == null ? "not logged in" : actor.getType().name()) + " lacks " + permission);
    }

    /**
     * @param viewer the logged-in account; needs {@link Permission#MANAGE_USERS}
     * @return the newest events, newest first, at most {@value #MAX_EVENTS}
     * @throws PermissionDeniedException if the viewer may not read the log (the
     *         denial itself is logged)
     */
    public List<SecurityEvent> recentEvents(Account viewer) {
        new AccessControl(this).require(viewer, Permission.MANAGE_USERS);
        return repository.findNewestFirst(MAX_EVENTS);
    }

    /**
     * @return the time of the latest event of this type for this subject, if any
     */
    Optional<Instant> latest(SecurityEventType type, String subject) {
        return repository.findLatest(type, subject);
    }

    /**
     * @return how many events of this type for this subject happened strictly after the given time
     */
    int countAfter(SecurityEventType type, String subject, Instant after) {
        return repository.countAfter(type, subject, after);
    }
}
