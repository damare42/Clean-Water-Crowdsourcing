package model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by Sheng on 9/20/16.
 * Registers accounts and verifies ids and passwords.
 */
public class Authenticator {

    /**
     * Passwords keyed by account id.
     */
    public static final Map<String, String> ACCOUNTS = new HashMap<>();

    private Authenticator() {
    }

    /**
     * Stores an account and its password.
     *
     * @param account the account to store
     * @param password the password of the account
     * @throws IllegalArgumentException if the account's id is already taken
     */
    public static void addAccount(Account account, String password) {
        if (!isIdAvailable(account.getId())) {
            throw new IllegalArgumentException(Validation.MSG_ID_TAKEN);
        }
        ACCOUNTS.put(account.getId(), password);
        Account.accountList.put(account.getId(), account);
    }

    /**
     * Validates a registration form and, if it is valid and the id is free, creates
     * and stores an account of the subclass matching the chosen account type.
     *
     * @return the newly registered account
     * @throws IllegalArgumentException with a user-facing message if the form is
     *         invalid or the id is taken; nothing is stored in that case
     */
    public static Account register(String id, AccountType type, String fname, String lname,
                                   String email, String emailConfirm,
                                   String password, String passwordConfirm) {
        Optional<String> error = Validation.validateRegistration(id, type, fname, lname,
                email, emailConfirm, password, passwordConfirm);
        if (error.isPresent()) {
            throw new IllegalArgumentException(error.get());
        }
        Account account = Account.create(type, id, fname, lname, email);
        addAccount(account, password);
        return account;
    }

    /**
     * Checks a login attempt.
     *
     * @param id the id of the account
     * @param password the password to compare
     * @return true if an account with this id exists and the password matches
     */
    public static boolean validatePassword(String id, String password) {
        String validAccountPassword = ACCOUNTS.get(id);
        return validAccountPassword != null && validAccountPassword.equals(password);
    }

    /**
     * @param id the account id to check
     * @return true if no account and no password is stored under this id
     */
    public static boolean isIdAvailable(String id) {
        return id != null && !ACCOUNTS.containsKey(id) && !Account.accountList.containsKey(id);
    }
}
