package model.security;

/**
 * What a {@link SecurityEvent} records. The names are stored in the database
 * ({@code security_log.event_type}, checked by the schema), so constants must not be
 * renamed or removed; adding one needs a schema migration.
 */
public enum SecurityEventType {
    /** Someone logged in. */
    LOGIN_SUCCESS("Login succeeded"),
    /** A login was refused: unknown id, wrong password, banned account or locked id. */
    LOGIN_FAILURE("Login failed"),
    /** Too many failed logins: the id is locked for a while. */
    LOGIN_LOCKED("Login locked"),
    /** A new account was registered. */
    REGISTRATION("Registration"),
    /** The first administrator account was created at start-up. */
    ADMIN_CREATED("Administrator created"),
    /** An administrator banned an account. */
    ACCOUNT_BANNED("Account banned"),
    /** An administrator lifted a ban. */
    ACCOUNT_UNBANNED("Account unbanned"),
    /** An administrator deleted an account. */
    ACCOUNT_DELETED("Account deleted"),
    /** An account (or nobody) tried something its type does not allow. */
    PERMISSION_DENIED("Permission denied");

    private final String label;

    SecurityEventType(String label) {
        this.label = label;
    }

    /**
     * @return a short description for display, e.g. "Login failed"
     */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
