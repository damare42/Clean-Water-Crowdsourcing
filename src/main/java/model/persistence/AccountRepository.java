package model.persistence;

import model.Account;

import java.util.Optional;

/**
 * Stores accounts together with their password hashes. Ids are unique ignoring
 * case and surrounding whitespace: every method that takes an id finds the account
 * whatever the case of the given id. Returned accounts are copies; changing one
 * does not change what is stored.
 */
public interface AccountRepository {

    /**
     * Stores a new account.
     *
     * @param account the account to store
     * @param passwordHash the encoded password hash (never a plain password)
     * @throws DuplicateKeyException if an account with the same id (ignoring case) exists
     */
    void add(Account account, String passwordHash);

    /**
     * @param id the account id, in any case
     * @return the stored account, or empty if there is none
     */
    Optional<Account> findById(String id);

    /**
     * @param id the account id, in any case
     * @return true if an account with this id exists
     */
    boolean existsById(String id);

    /**
     * @param id the account id, in any case
     * @return the stored password hash, or empty if there is no such account
     */
    Optional<String> findPasswordHash(String id);

    /**
     * Overwrites the profile fields (first and last name, e-mail, phone, address) of
     * the stored account that has the same id as the given account. The id and type
     * are not changed.
     *
     * @return true if an account was updated, false if there is no such account
     */
    boolean updateProfile(Account account);

    /**
     * Replaces the password hash of an account, e.g. to upgrade its iteration count.
     *
     * @return true if an account was updated, false if there is no such account
     */
    boolean updatePasswordHash(String id, String passwordHash);
}
