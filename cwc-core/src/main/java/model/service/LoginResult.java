package model.service;

import model.Account;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of {@link AuthService#authenticate}: the account on success, otherwise
 * why the login was refused, with a message for the user.
 * <p>
 * The reasons are chosen so that the message does not tell an attacker whether an id
 * exists: an unknown id and a wrong password both give
 * {@link Status#INVALID_CREDENTIALS}; a ban is only revealed to someone who gave the
 * correct password; and locks apply to unknown ids just like to real ones.
 */
public final class LoginResult {

    /** Why a login succeeded or failed. */
    public enum Status {
        /** The id exists, the password matches and the account is not banned. */
        SUCCESS,
        /** Unknown id or wrong password (deliberately not told apart). */
        INVALID_CREDENTIALS,
        /** The password was correct, but an administrator banned the account. */
        BANNED,
        /** Too many failed attempts for this id; try again later. */
        LOCKED
    }

    /** Shown for {@link Status#INVALID_CREDENTIALS}. */
    public static final String MSG_INVALID = "Invalid log in. Please try again";
    /** Shown for {@link Status#BANNED}. */
    public static final String MSG_BANNED = "This account is banned. Please contact an administrator.";

    private final Status status;
    private final Account account;
    private final Instant lockedUntil;

    private LoginResult(Status status, Account account, Instant lockedUntil) {
        this.status = status;
        this.account = account;
        this.lockedUntil = lockedUntil;
    }

    static LoginResult success(Account account) {
        return new LoginResult(Status.SUCCESS, Objects.requireNonNull(account, "account"), null);
    }

    static LoginResult invalid() {
        return new LoginResult(Status.INVALID_CREDENTIALS, null, null);
    }

    static LoginResult banned() {
        return new LoginResult(Status.BANNED, null, null);
    }

    static LoginResult locked(Instant until) {
        return new LoginResult(Status.LOCKED, null, Objects.requireNonNull(until, "until"));
    }

    public Status getStatus() {
        return status;
    }

    /**
     * @return true if the login succeeded
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /**
     * @return the logged-in account, or empty if the login failed
     */
    public Optional<Account> getAccount() {
        return Optional.ofNullable(account);
    }

    /**
     * @return when the lock ends, for {@link Status#LOCKED}; otherwise empty
     */
    public Optional<Instant> getLockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }

    /**
     * @param now the current time, to say how long a lock still lasts
     * @return a message for the user, or "" on success
     */
    public String message(Instant now) {
        return switch (status) {
            case SUCCESS -> "";
            case INVALID_CREDENTIALS -> MSG_INVALID;
            case BANNED -> MSG_BANNED;
            case LOCKED -> lockedMessage(Duration.between(now, lockedUntil));
        };
    }

    /**
     * @param remaining how long the lock still lasts
     * @return e.g. "Too many failed log-ins. Please try again in 15 minutes."
     */
    static String lockedMessage(Duration remaining) {
        long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
        return "Too many failed log-ins. Please try again in " + minutes
                + (minutes == 1 ? " minute." : " minutes.");
    }
}
