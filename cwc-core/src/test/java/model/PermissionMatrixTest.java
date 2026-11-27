package model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static model.Permission.MANAGE_USERS;
import static model.Permission.SUBMIT_PURITY_REPORT;
import static model.Permission.SUBMIT_SOURCE_REPORT;
import static model.Permission.VIEW_PURITY_HISTORY;
import static model.Permission.VIEW_PURITY_REPORTS;
import static model.Permission.VIEW_SOURCE_REPORTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionMatrixTest {

    private static final Map<AccountType, Set<Permission>> EXPECTED = Map.of(
            AccountType.User, EnumSet.of(SUBMIT_SOURCE_REPORT, VIEW_SOURCE_REPORTS),
            AccountType.Worker, EnumSet.of(SUBMIT_SOURCE_REPORT, VIEW_SOURCE_REPORTS,
                    SUBMIT_PURITY_REPORT, VIEW_PURITY_REPORTS),
            AccountType.Manager, EnumSet.of(SUBMIT_SOURCE_REPORT, VIEW_SOURCE_REPORTS,
                    SUBMIT_PURITY_REPORT, VIEW_PURITY_REPORTS, VIEW_PURITY_HISTORY),
            AccountType.Admin, EnumSet.of(VIEW_SOURCE_REPORTS, MANAGE_USERS));

    @Test
    void everyTypeHasExactlyTheExpectedPermissions() {
        assertEquals(EnumSet.allOf(AccountType.class), EnumSet.copyOf(EXPECTED.keySet()));
        for (AccountType type : AccountType.values()) {
            assertEquals(EXPECTED.get(type), type.permissions(), type.name());
            for (Permission permission : Permission.values()) {
                assertEquals(EXPECTED.get(type).contains(permission), type.hasPermission(permission),
                        type + " / " + permission);
            }
        }
    }

    @Test
    void rolesBuildOnEachOther() {
        assertTrue(AccountType.Worker.permissions().containsAll(AccountType.User.permissions()));
        assertTrue(AccountType.Manager.permissions().containsAll(AccountType.Worker.permissions()));
        assertFalse(AccountType.Manager.hasPermission(MANAGE_USERS));
    }

    @Test
    void everyPermissionIsGrantedToSomeone() {
        for (Permission permission : Permission.values()) {
            assertTrue(EnumSet.allOf(AccountType.class).stream().anyMatch(t -> t.hasPermission(permission)),
                    permission.name());
        }
    }

    @Test
    void permissionSetsAreUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
                () -> AccountType.User.permissions().add(MANAGE_USERS));
    }

    @Test
    void nullPermissionIsNeverGranted() {
        for (AccountType type : AccountType.values()) {
            assertFalse(type.hasPermission(null));
        }
    }

    @Test
    void adminCannotBeSelfRegistered() {
        assertEquals(List.of(AccountType.User, AccountType.Worker, AccountType.Manager),
                AccountType.selfRegistrable());
    }
}
