package model.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One entry of the security log. Entries are never changed or deleted once written.
 * Text fields never hold passwords or password hashes; null text is stored as "",
 * control characters are replaced by spaces and long text is cut to
 * {@value #MAX_TEXT_LENGTH} characters, so an attacker cannot flood or garble the log
 * through the login form.
 *
 * @param sequence   the position in the log, starting at 1; higher is newer
 * @param occurredAt when it happened (millisecond precision)
 * @param type       what happened
 * @param actor      the id of the logged-in account that acted, or "" (e.g. for login attempts)
 * @param subject    the id acted on or attempted, e.g. the normalized id of a login attempt
 * @param detail     more information, e.g. why a login failed
 */
public record SecurityEvent(long sequence, Instant occurredAt, SecurityEventType type,
                            String actor, String subject, String detail) {

    /** Longest stored actor, subject or detail. */
    public static final int MAX_TEXT_LENGTH = 200;

    public SecurityEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("Sequence must be positive: " + sequence);
        }
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt").truncatedTo(ChronoUnit.MILLIS);
        Objects.requireNonNull(type, "type");
        actor = clean(actor);
        subject = clean(subject);
        detail = clean(detail);
    }

    /**
     * @param text any text, or null
     * @return the text as it is stored: "" for null, control characters replaced by
     *         spaces, at most {@value #MAX_TEXT_LENGTH} characters
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String cut = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        StringBuilder out = new StringBuilder(cut.length());
        for (int i = 0; i < cut.length(); i++) {
            char c = cut.charAt(i);
            out.append(Character.isISOControl(c) ? ' ' : c);
        }
        return out.toString();
    }
}
