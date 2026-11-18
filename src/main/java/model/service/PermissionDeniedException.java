package model.service;

import model.AccountType;
import model.Permission;

import java.util.Locale;

/**
 * Thrown when an account (or nobody, if no one is logged in) tries to do something
 * its account type does not allow. The message is suitable for showing to the user.
 */
public class PermissionDeniedException extends RuntimeException {

    private final Permission permission;
    private final AccountType accountType;

    /**
     * @param permission the permission that was missing
     * @param accountType the type of the account that tried, or null if nobody was logged in
     */
    public PermissionDeniedException(Permission permission, AccountType accountType) {
        super(message(permission, accountType));
        this.permission = permission;
        this.accountType = accountType;
    }

    private static String message(Permission permission, AccountType accountType) {
        String action = permission.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return accountType == null
                ? "You must be logged in to " + action
                : accountType + " accounts are not allowed to " + action;
    }

    /**
     * @return the permission that was missing
     */
    public Permission getPermission() {
        return permission;
    }

    /**
     * @return the type of the account that tried, or null if nobody was logged in
     */
    public AccountType getAccountType() {
        return accountType;
    }
}
