package model.service;

import model.Account;
import model.Permission;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Permission checks used by the services.
 */
public final class Authorization {

    private static final Logger LOGGER = Logger.getLogger(Authorization.class.getName());

    private Authorization() {
    }

    /**
     * @param account the acting account, or null if nobody is logged in
     * @param permission the permission the action needs
     * @throws PermissionDeniedException if the account is null or lacks the permission
     */
    public static void require(Account account, Permission permission) {
        if (account == null || !account.hasPermission(permission)) {
            PermissionDeniedException denied = new PermissionDeniedException(permission,
                    account == null ? null : account.getType());
            LOGGER.log(Level.WARNING, "Permission {0} denied to {1}",
                    new Object[]{permission, account == null ? "anonymous" : account.getId()});
            throw denied;
        }
    }
}
