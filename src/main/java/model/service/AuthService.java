package model.service;

import model.Account;
import model.AccountType;
import model.Validation;
import model.persistence.AccountRepository;
import model.persistence.DataAccessException;
import model.persistence.DuplicateKeyException;
import model.security.PasswordHasher;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers accounts and checks logins. Passwords are only ever stored as PBKDF2
 * hashes (see {@link PasswordHasher}); they are taken as {@code char[]} and never
 * logged or returned. Ids are matched ignoring case and surrounding whitespace.
 */
public final class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    /** Shown when someone tries to register an account type that needs an administrator. */
    public static final String MSG_ACCOUNT_TYPE_NOT_ALLOWED =
            "This account type cannot be chosen at registration";

    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    /** Checked against for unknown ids, so they take as long as known ones. */
    private final String dummyHash;

    /**
     * Creates the service. This computes one password hash, so it takes about as
     * long as a login.
     *
     * @param accounts where accounts are stored
     * @param hasher hashes and verifies passwords
     */
    public AuthService(AccountRepository accounts, PasswordHasher hasher) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
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
        LOGGER.log(Level.INFO, "Registered account {0} ({1})",
                new Object[]{account.getId(), account.getType()});
        return account;
    }

    /**
     * Checks a login attempt. If the stored hash uses fewer iterations than the
     * current setting, it is replaced with a stronger one; if that cannot be saved,
     * the login still succeeds and the upgrade is tried again next time.
     * <p>
     * This takes as long as a password hash (a noticeable fraction of a second), so
     * call it off the UI thread.
     *
     * @param id the account id, in any case
     * @param password the password; not modified, the caller should clear it
     * @return the account if the id exists and the password matches, otherwise empty
     */
    public Optional<Account> login(String id, char[] password) {
        Optional<String> storedHash = Validation.isValidId(id)
                ? accounts.findPasswordHash(id) : Optional.empty();
        if (storedHash.isEmpty()) {
            // Spend about as long as a real check so unknown ids are not revealed by timing.
            hasher.verify(password, dummyHash);
            LOGGER.log(Level.INFO, "Failed login: unknown id");
            return Optional.empty();
        }
        if (!hasher.verify(password, storedHash.get())) {
            LOGGER.log(Level.INFO, "Failed login for {0}: wrong password", Validation.normalizeId(id));
            return Optional.empty();
        }
        if (hasher.needsRehash(storedHash.get())) {
            upgradeHash(id, password);
        }
        Optional<Account> account = accounts.findById(id);
        account.ifPresent(a -> LOGGER.log(Level.INFO, "Logged in {0}", a.getId()));
        return account;
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
