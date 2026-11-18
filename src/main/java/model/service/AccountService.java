package model.service;

import model.Account;
import model.Validation;
import model.persistence.AccountRepository;
import model.persistence.DataAccessException;

import java.util.Objects;
import java.util.Optional;

/**
 * Changes stored account details.
 */
public final class AccountService {

    private final AccountRepository accounts;

    /**
     * @param accounts where accounts are stored
     */
    public AccountService(AccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    /**
     * Validates and saves the editable profile fields of an account, then applies
     * them to the given object. If any field is invalid, nothing is saved or changed.
     *
     * @param account the account being edited (normally the logged-in account)
     * @throws IllegalArgumentException with a user-facing message if validation fails
     * @throws DataAccessException if the account is not stored or cannot be saved
     */
    public void updateProfile(Account account, String fname, String lname, String email,
                              String phone, String address) {
        Objects.requireNonNull(account, "account");
        Optional<String> error = Validation.validateProfile(fname, lname, email);
        if (error.isPresent()) {
            throw new IllegalArgumentException(error.get());
        }
        Account updated = Account.create(account.getType(), account.getId(), fname, lname, email,
                phone, address);
        if (!accounts.updateProfile(updated)) {
            throw new DataAccessException("Account " + account.getId() + " is not stored");
        }
        account.updateProfile(fname, lname, email, phone, address);
    }
}
