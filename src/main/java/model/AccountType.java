package model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Sheng on 9/27/16.
 * The role of an account. Each role maps to a fixed set of {@link Permission}s:
 * <ul>
 *     <li>User: submit and view water source reports</li>
 *     <li>Worker: everything a User can do, plus submit and view purity reports</li>
 *     <li>Manager: everything a Worker can do, plus view purity history</li>
 *     <li>Admin: view water source reports and manage users</li>
 * </ul>
 */
public enum AccountType {
    User(EnumSet.of(Permission.SUBMIT_SOURCE_REPORT, Permission.VIEW_SOURCE_REPORTS)),
    Worker(EnumSet.of(Permission.SUBMIT_SOURCE_REPORT, Permission.VIEW_SOURCE_REPORTS,
            Permission.SUBMIT_PURITY_REPORT, Permission.VIEW_PURITY_REPORTS)),
    Manager(EnumSet.of(Permission.SUBMIT_SOURCE_REPORT, Permission.VIEW_SOURCE_REPORTS,
            Permission.SUBMIT_PURITY_REPORT, Permission.VIEW_PURITY_REPORTS,
            Permission.VIEW_PURITY_HISTORY)),
    Admin(EnumSet.of(Permission.VIEW_SOURCE_REPORTS, Permission.MANAGE_USERS));

    private final Set<Permission> permissions;

    AccountType(EnumSet<Permission> permissions) {
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    /**
     * @return the permissions granted to this account type (unmodifiable)
     */
    public Set<Permission> permissions() {
        return permissions;
    }

    /**
     * @param permission the permission to check
     * @return true if accounts of this type have the permission
     */
    public boolean hasPermission(Permission permission) {
        return permission != null && permissions.contains(permission);
    }

    /**
     * Account types a person may choose when registering. Admin accounts cannot be
     * self-registered.
     *
     * @return the self-registrable types, in display order
     */
    public static List<AccountType> selfRegistrable() {
        return List.of(User, Worker, Manager);
    }
}
