package model.service;

import model.Account;
import model.Permission;
import model.persistence.DataAccessException;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The permission checks of {@link Authorization}, plus a security log entry for every
 * denial. Services use this so that denials are recorded whatever the UI shows.
 */
public final class AccessControl {

    private static final Logger LOGGER = Logger.getLogger(AccessControl.class.getName());

    private final SecurityLog log;

    /**
     * @param log where denials are recorded
     */
    public AccessControl(SecurityLog log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * @see Authorization#require(Account, Permission)
     * @throws PermissionDeniedException if the account is null or lacks the permission
     */
    public void require(Account actor, Permission permission) {
        try {
            Authorization.require(actor, permission);
        } catch (PermissionDeniedException e) {
            throw recorded(e, actor);
        }
    }

    /**
     * @see Authorization#requireSelfOr(Account, Account, Permission)
     * @throws PermissionDeniedException if the actor may not act on the target
     */
    public void requireSelfOr(Account actor, Account target, Permission permission) {
        try {
            Authorization.requireSelfOr(actor, target, permission);
        } catch (PermissionDeniedException e) {
            throw recorded(e, actor);
        }
    }

    /**
     * Logs the denial. If that fails the action is still refused: the logging error is
     * attached to the denial instead of replacing it.
     */
    private PermissionDeniedException recorded(PermissionDeniedException denial, Account actor) {
        try {
            log.permissionDenied(actor, denial.getPermission());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not record a permission denial in the security log", e);
            denial.addSuppressed(e);
        }
        return denial;
    }
}
