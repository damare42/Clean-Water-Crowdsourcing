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

    /**
     * Allows an account to act on itself, and accounts with the given permission to
     * act on any account.
     *
     * @param actor the acting account, or null if nobody is logged in
     * @param target the account acted on
     * @param permission the permission needed to act on another account
     * @throws PermissionDeniedException if the actor is null, or is not the target
     *         (same id, ignoring case) and lacks the permission
     */
    public static void requireSelfOr(Account actor, Account target, Permission permission) {
        if (actor != null && target != null && actor.getIdKey().equals(target.getIdKey())) {
            return;
        }
        require(actor, permission);
    }
}
