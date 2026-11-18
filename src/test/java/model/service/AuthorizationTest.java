package model.service;

import model.Account;
import model.AccountType;
import model.Permission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizationTest {

    @Test
    void requireFollowsPermissionMatrixForEveryTypeAndPermission() {
        for (AccountType type : AccountType.values()) {
            Account account = Account.create(type, "id", "F", "L", "f@example.com");
            for (Permission permission : Permission.values()) {
                if (type.hasPermission(permission)) {
                    assertDoesNotThrow(() -> Authorization.require(account, permission),
                            type + " / " + permission);
                } else {
                    PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                            () -> Authorization.require(account, permission), type + " / " + permission);
                    assertEquals(permission, e.getPermission());
                    assertEquals(type, e.getAccountType());
                }
            }
        }
    }

    @Test
    void requireDeniesEverythingWhenNobodyIsLoggedIn() {
        for (Permission permission : Permission.values()) {
            PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                    () -> Authorization.require(null, permission));
            assertEquals(permission, e.getPermission());
        }
    }

    @Test
    void messageNamesTheAction() {
        Account worker = Account.create(AccountType.Worker, "w1", "W", "K", "w@example.com");

        PermissionDeniedException e = assertThrows(PermissionDeniedException.class,
                () -> Authorization.require(worker, Permission.MANAGE_USERS));

        assertEquals("Worker accounts are not allowed to manage users", e.getMessage());
    }
}
