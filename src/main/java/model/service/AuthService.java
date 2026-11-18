package model.service;

import model.Account;
import model.AccountType;
import model.Validation;
import model.persistence.AccountRepository;
import model.persistence.DataAccessException;
import model.persistence.DuplicateKeyException;
import model.security.LockoutPolicy;
import model.security.PasswordHasher;
import model.security.SecurityEventType;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers accounts, creates the first administrator and checks logins. Passwords
 * are only ever stored as PBKDF2 hashes (see {@link PasswordHasher}); they are taken as
 * {@code char[]} and never logged or returned. Ids are matched ignoring case and
 * surrounding whitespace.
 * <p>
 * Every login attempt, registration and administrator creation is written to the
 * {@link SecurityLog}. Login attempts are recorded under the attempted id in its
 * normalized form ({@link Validation#idKey}). Repeated failures lock an id for a while,
 * as set by a {@link LockoutPolicy}; locks are worked out from the security log, so
 * they survive a restart of the app.
 */
public final class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    /** Shown when someone tries to register an account type that needs an administrator. */
    public static final String MSG_ACCOUNT_TYPE_NOT_ALLOWED =
            "This account type cannot be chosen at registration";
    /** Shown when the first administrator is to be created but one exists already. */
    public static final String MSG_ADMIN_EXISTS = "An administrator account already exists";

    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final SecurityLog log;
    private final LockoutPolicy lockout;
    /** Checked against for unknown ids, so they take as long as known ones. */
    private final String dummyHash;

    /**
     * Creates the service.
     * <p>
     * This computes one password hash up front (for the timing protection of
     * {@link #authenticate}), so it takes about as long as a login: with the default
     * {@link PasswordHasher#DEFAULT_ITERATIONS} roughly 0.1 to 0.3 seconds on a current
     * desktop. The app builds it once at start-up, before the first window is shown;
     * computing the hash lazily instead would make the first login with an unknown id
     * measurably slower than later ones, which is what the dummy hash is there to avoid.
     *
     * @param accounts where accounts are stored
     * @param hasher hashes and verifies passwords
     * @param log where logins, registrations and lockouts are recorded
     * @param lockout when failed logins lock an id
     */
    public AuthService(AccountRepository accounts, PasswordHasher hasher, SecurityLog log,
                       LockoutPolicy lockout) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.log = Objects.requireNonNull(log, "log");
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        this.dummyHash = hasher.hash("not-a-real-password".toCharArray());
    }

    /**
     * Validates a registration form and, if it is valid and the id is free, stores a
     * new account of the subclass matching the chosen account type. This hashes the
     * password, so call it off the UI thread.
     *
     * @param password the password; not modified, the caller should clear it
     * @param passwordConfirm the repeated password; not modified
     * @return the newly registered account
     * @throws IllegalArgumentException with a user-facing message if the form is
     *         invalid, the type cannot be self-registered, or the id is taken
     *         (ignoring case); nothing is stored in that case
     */
    public Account register(String id, AccountType type, String fname, String lname,
                            String email, String emailConfirm,
                            char[] password, char[] passwordConfirm) {
        Optional<String> error = Validation.validateRegistration(id, type, fname, lname,
                email, emailConfirm, password, passwordConfirm);
        if (error.isPresent()) {
            throw new IllegalArgumentException(error.get());
        }
        if (!AccountType.selfRegistrable().contains(type)) {
            throw new IllegalArgumentException(MSG_ACCOUNT_TYPE_NOT_ALLOWED);
        }
        if (accounts.existsById(id)) {
            throw new IllegalArgumentException(Validation.MSG_ID_TAKEN);
        }
        Account account = Account.create(type, id, fname, lname, email);
        try {
            accounts.add(account, hasher.hash(password));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(Validation.MSG_ID_TAKEN, e);
        }
        log.record(SecurityEventType.REGISTRATION, account.getId(), account.getId(), type.name());
        LOGGER.log(Level.INFO, "Registered account {0} ({1})",
                new Object[]{account.getId(), account.getType()});
        return account;
    }

    /**
     * @return true if at least one administrator account exists
     */
    public boolean hasAdministrator() {
        return accounts.existsByType(AccountType.Admin);
    }

    /**
     * Creates the first administrator account. This is only possible while no
     * administrator exists; checking and storing are atomic. It is meant for the
     * start-up bootstrap (see {@code fxapp.Main}), which only runs when the person
     * starting the app asks for it. This hashes the password, so call it off the UI
     * thread.
     *
     * @return the new administrator account
     * @throws IllegalArgumentException with a user-facing message if the form is
     *         invalid, the id is taken, or an administrator already exists; nothing is
     *         stored in that case
     */
    public Account createFirstAdministrator(String id, String fname, String lname, String email,
                                            String emailConfirm, char[] password,
                                            char[] passwordConfirm) {
        Optional<String> error = Validation.validateRegistration(id, AccountType.Admin, fname, lname,
                email, emailConfirm, password, passwordConfirm);
        if (error.isPresent()) {
            throw new IllegalArgumentException(error.get());
        }
        if (hasAdministrator()) {
            throw new IllegalArgumentException(MSG_ADMIN_EXISTS);
        }
        Account admin = Account.create(AccountType.Admin, id, fname, lname, email);
        boolean stored;
        try {
            stored = accounts.addFirstOfType(admin, hasher.hash(password));
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException(Validation.MSG_ID_TAKEN, e);
        }
        if (!stored) {
            throw new IllegalArgumentException(MSG_ADMIN_EXISTS);
        }
        log.record(SecurityEventType.ADMIN_CREATED, "", admin.getId(), "first administrator (start-up)");
        LOGGER.log(Level.INFO, "Created the first administrator account {0}", admin.getId());
        return admin;
    }

    /**
     * Checks a login attempt; see {@link LoginResult} for the possible outcomes. In
     * order:
     * <ol>
     *     <li>If the attempted id is locked, the attempt is refused without checking
     *         the password.</li>
     *     <li>An unknown id or a wrong password fails, and may lock the id.</li>
     *     <li>A correct password for a banned account is refused as banned.</li>
     *     <li>Otherwise the login succeeds; a weak stored hash is replaced by a stronger
     *         one (if that cannot be saved, the login still succeeds and the upgrade is
     *         tried again next time).</li>
     * </ol>
     * Every outcome is written to the security log. This takes as long as a password
     * hash (a noticeable fraction of a second), so call it off the UI thread.
     *
     * @param id the account id, in any case
     * @param password the password; not modified, the caller should clear it
     * @return the outcome
     * @throws DataAccessException if the accounts or the security log cannot be read or
     *         written; the login is then refused
     */
    public LoginResult authenticate(String id, char[] password) {
        String key = attemptKey(id);
        Optional<Instant> lockedUntil = lockedUntil(key, log.now());
        if (lockedUntil.isPresent()) {
            log.record(SecurityEventType.LOGIN_FAILURE, "", key, "id is locked");
            LOGGER.log(Level.INFO, "Refused login for locked id {0}", key);
            return LoginResult.locked(lockedUntil.get());
        }
        Optional<String> storedHash = Validation.isValidId(id)
                ? accounts.findPasswordHash(id) : Optional.empty();
        if (storedHash.isEmpty()) {
            // Spend about as long as a real check so unknown ids are not revealed by timing.
            hasher.verify(password, dummyHash);
            return failed(key, "unknown id");
        }
        if (!hasher.verify(password, storedHash.get())) {
            return failed(key, "wrong password");
        }
        if (accounts.isBanned(id)) {
            log.record(SecurityEventType.LOGIN_FAILURE, "", key, "account is banned");
            LOGGER.log(Level.INFO, "Refused login for banned account {0}", key);
            return LoginResult.banned();
        }
        if (hasher.needsRehash(storedHash.get())) {
            upgradeHash(id, password);
        }
        Optional<Account> account = accounts.findById(id);
        if (account.isEmpty()) {
            // deleted between the two reads
            return failed(key, "unknown id");
        }
        log.record(SecurityEventType.LOGIN_SUCCESS, account.get().getId(), key, "");
        LOGGER.log(Level.INFO, "Logged in {0}", account.get().getId());
        return LoginResult.success(account.get());
    }

    /**
     * Same as {@link #authenticate}, for callers that only need the account.
     *
     * @return the account if the login succeeded, otherwise empty
     */
    public Optional<Account> login(String id, char[] password) {
        return authenticate(id, password).getAccount();
    }

    /**
     * @param id an attempted id
     * @return how the attempt is recorded and locked: the normalized id, or "" for null
     */
    static String attemptKey(String id) {
        return id == null ? "" : Validation.idKey(id);
    }

    /**
     * @return when the lock on this attempted id ends, if it is locked at {@code now}
     */
    private Optional<Instant> lockedUntil(String key, Instant now) {
        return log.latest(SecurityEventType.LOGIN_LOCKED, key)
                .map(lockedAt -> lockedAt.plus(lockout.lockDuration()))
                .filter(until -> until.isAfter(now));
    }

    /**
     * Records a failed attempt and locks the id if there have now been too many
     * failures: those within the policy's window, since the last successful login and
     * since the end of the last lock.
     */
    private LoginResult failed(String key, String reason) {
        log.record(SecurityEventType.LOGIN_FAILURE, "", key, reason);
        LOGGER.log(Level.INFO, "Failed login for {0}: {1}", new Object[]{key, reason});
        Instant now = log.now();
        Instant since = now.minus(lockout.window());
        Optional<Instant> lastSuccess = log.latest(SecurityEventType.LOGIN_SUCCESS, key);
        if (lastSuccess.isPresent() && lastSuccess.get().isAfter(since)) {
            since = lastSuccess.get();
        }
        // A lock ends at lockedAt + duration and attempts at that very moment are no longer
        // locked, so they must count: event times have millisecond precision, and counting
        // starts strictly after the given time, hence the millisecond subtracted here.
        Optional<Instant> lastLockEnd = log.latest(SecurityEventType.LOGIN_LOCKED, key)
                .map(lockedAt -> lockedAt.plus(lockout.lockDuration()).minusMillis(1));
        if (lastLockEnd.isPresent() && lastLockEnd.get().isAfter(since)) {
            since = lastLockEnd.get();
        }
        int failures = log.countAfter(SecurityEventType.LOGIN_FAILURE, key, since);
        if (failures < lockout.maxFailures()) {
            return LoginResult.invalid();
        }
        log.record(SecurityEventType.LOGIN_LOCKED, "", key, failures + " failed log-ins; locked for "
                + lockout.lockDuration().toMinutes() + " minutes");
        LOGGER.log(Level.WARNING, "Locked id {0} after {1} failed log-ins", new Object[]{key, failures});
        return LoginResult.locked(now.plus(lockout.lockDuration()));
    }

    /**
     * @param id the account id to check
     * @return true if the id is valid and no account has it (ignoring case)
     */
    public boolean isIdAvailable(String id) {
        return Validation.isValidId(id) && !accounts.existsById(id);
    }

    /**
     * Looks up a registered account. Never creates an account.
     *
     * @param id the account id, in any case
     * @return the account, or empty if there is none
     */
    public Optional<Account> findAccount(String id) {
        return accounts.findById(id);
    }

    private void upgradeHash(String id, char[] password) {
        try {
            accounts.updatePasswordHash(id, hasher.hash(password));
            LOGGER.log(Level.INFO, "Upgraded password hash for {0}", Validation.normalizeId(id));
        } catch (DataAccessException e) {
            // The password was correct; a failed upgrade must not block the login.
            LOGGER.log(Level.WARNING, "Could not upgrade the password hash for "
                    + Validation.normalizeId(id), e);
        }
    }
}
