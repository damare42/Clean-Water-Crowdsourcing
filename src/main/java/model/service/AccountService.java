package model.service;

import model.Account;
import model.Permission;
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
     * Accounts may edit their own profile; editing another account's profile needs
     * {@link Permission#MANAGE_USERS}.
     *
     * @param actor the logged-in account making the change
     * @param account the account being edited
     * @throws PermissionDeniedException if the actor may not edit this account
     * @throws IllegalArgumentException with a user-facing message if validation fails
     * @throws DataAccessException if the account is not stored or cannot be saved
     */
    public void updateProfile(Account actor, Account account, String fname, String lname,
                              String email, String phone, String address) {
        Objects.requireNonNull(account, "account");
        Authorization.requireSelfOr(actor, account, Permission.MANAGE_USERS);
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
