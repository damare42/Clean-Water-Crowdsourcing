package model.service;

import model.Account;
import model.AccountSummary;
import model.Permission;
import model.Validation;
import model.persistence.AccountRepository;
import model.persistence.DataAccessException;
import model.security.SecurityEventType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Changes stored account details, and lets administrators list, ban, unban and delete
 * accounts. Administrator actions are written to the {@link SecurityLog}.
 * <p>
 * Deleting an account removes the account and its password hash only: the reports it
 * filed stay, still showing its id as the reporter, and so do its security log
 * entries. The id becomes free again, so a new account could later register the same
 * id; the reports do not record which account (old or new) filed them.
 */
public final class AccountService {

    private static final Logger LOGGER = Logger.getLogger(AccountService.class.getName());

    /** Shown when an administrator tries to ban their own account. */
    public static final String MSG_CANNOT_BAN_SELF = "You cannot ban your own account";
    /** Shown when an administrator tries to delete their own account. */
    public static final String MSG_CANNOT_DELETE_SELF = "You cannot delete your own account";
    /** Shown when the account to ban or delete does not exist (any more). */
    public static final String MSG_NO_SUCH_ACCOUNT = "There is no such account";

    private final AccountRepository accounts;
    private final AccessControl access;
    private final SecurityLog log;

    /**
     * @param accounts where accounts are stored
     * @param access checks permissions and records denials
     * @param log where administrator actions are recorded
     */
    public AccountService(AccountRepository accounts, AccessControl access, SecurityLog log) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.access = Objects.requireNonNull(access, "access");
        this.log = Objects.requireNonNull(log, "log");
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
        access.requireSelfOr(actor, account, Permission.MANAGE_USERS);
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

    /**
     * @param actor the logged-in account; needs {@link Permission#MANAGE_USERS}
     * @return every account, ordered by id ignoring case
     * @throws PermissionDeniedException if the actor may not manage users
     */
    public List<AccountSummary> listAccounts(Account actor) {
        access.require(actor, Permission.MANAGE_USERS);
        return accounts.findAllSummaries();
    }

    /**
     * Bans or unbans an account. A banned account cannot log in; its reports stay
     * visible. Administrators cannot ban themselves, so at least one administrator
     * can always log in.
     *
     * @param actor the logged-in account; needs {@link Permission#MANAGE_USERS}
     * @param targetId the id of the account to change, in any case
     * @param banned true to ban, false to unban
     * @throws PermissionDeniedException if the actor may not manage users
     * @throws IllegalArgumentException with a user-facing message if the target is the
     *         actor or does not exist
     */
    public void setBanned(Account actor, String targetId, boolean banned) {
        access.require(actor, Permission.MANAGE_USERS);
        if (isSelf(actor, targetId)) {
            throw new IllegalArgumentException(MSG_CANNOT_BAN_SELF);
        }
        String id = storedId(targetId);
        if (!accounts.setBanned(id, banned)) {
            throw new IllegalArgumentException(MSG_NO_SUCH_ACCOUNT);
        }
        log.record(banned ? SecurityEventType.ACCOUNT_BANNED : SecurityEventType.ACCOUNT_UNBANNED,
                actor.getId(), id, "");
        LOGGER.log(Level.INFO, "{0} {1} account {2}",
                new Object[]{actor.getId(), banned ? "banned" : "unbanned", id});
    }

    /**
     * Deletes an account; see the class comment for what is kept. Administrators
     * cannot delete themselves.
     *
     * @param actor the logged-in account; needs {@link Permission#MANAGE_USERS}
     * @param targetId the id of the account to delete, in any case
     * @throws PermissionDeniedException if the actor may not manage users
     * @throws IllegalArgumentException with a user-facing message if the target is the
     *         actor or does not exist
     */
    public void deleteAccount(Account actor, String targetId) {
        access.require(actor, Permission.MANAGE_USERS);
        if (isSelf(actor, targetId)) {
            throw new IllegalArgumentException(MSG_CANNOT_DELETE_SELF);
        }
        Optional<Account> target = targetId == null ? Optional.empty() : accounts.findById(targetId);
        if (target.isEmpty() || !accounts.delete(targetId)) {
            throw new IllegalArgumentException(MSG_NO_SUCH_ACCOUNT);
        }
        log.record(SecurityEventType.ACCOUNT_DELETED, actor.getId(), target.get().getId(),
                target.get().getType().name());
        LOGGER.log(Level.INFO, "{0} deleted account {1}", new Object[]{actor.getId(), target.get().getId()});
    }

    private static boolean isSelf(Account actor, String targetId) {
        return targetId != null && actor.getIdKey().equals(Validation.idKey(targetId));
    }

    /**
     * @return the id as stored (for the log), or the given id if there is no such account
     */
    private String storedId(String targetId) {
        return targetId == null ? null
                : accounts.findById(targetId).map(Account::getId).orElse(targetId);
    }
}
