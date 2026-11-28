package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    private static Account alice() {
        return Account.create(AccountType.User, "alice", "Alice", "Smith", "alice@example.com",
                "404-555-0000", "North Ave");
    }

    @Test
    void fiveArgFactorySetsFieldsWithEmptyPhoneAndAddress() {
        Account account = Account.create(AccountType.Worker, "alice", "Alice", "Smith",
                "alice@example.com");

        assertEquals("alice", account.getId());
        assertEquals("Alice", account.getFname());
        assertEquals("Smith", account.getLname());
        assertEquals("alice@example.com", account.getEmail());
        assertEquals(AccountType.Worker, account.getType());
        assertEquals("", account.getPhone());
        assertEquals("", account.getAddress());
    }

    @Test
    void sevenArgFactoryKeepsPhoneAndAddress() {
        Account account = Account.create(AccountType.Manager, "bob", "Bob", "Jones",
                "bob@example.com", "555-1234", "1 Main St");

        assertEquals("bob", account.getId());
        assertEquals(AccountType.Manager, account.getType());
        assertEquals("555-1234", account.getPhone());
        assertEquals("1 Main St", account.getAddress());
    }

    @Test
    void nullPhoneAndAddressBecomeEmpty() {
        Account account = Account.create(AccountType.User, "bob", "Bob", "Jones",
                "bob@example.com", null, null);

        assertEquals("", account.getPhone());
        assertEquals("", account.getAddress());
    }

    @Test
    void idIsStrippedAndKeyIsLowerCase() {
        Account account = Account.create(AccountType.User, "  Alice ", "Alice", "Smith",
                "alice@example.com");

        assertEquals("Alice", account.getId());
        assertEquals("alice", account.getIdKey());
        assertEquals("Alice", account.toString());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "a", " a ", "al ice", "a\tb"})
    void factoryRejectsInvalidIds(String id) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Account.create(AccountType.User, id, "A", "B", "a@example.com"));
        assertEquals(Validation.MSG_INVALID_ID, e.getMessage());
    }

    @ParameterizedTest
    @EnumSource(AccountType.class)
    void createReturnsSubclassMatchingType(AccountType type) {
        Account account = Account.create(type, "id", "First", "Last", "f@example.com");

        Class<?> expected = switch (type) {
            case User -> User.class;
            case Worker -> Worker.class;
            case Manager -> Manager.class;
            case Admin -> Admin.class;
        };
        assertEquals(expected, account.getClass());
        assertEquals(type, account.getType());
        assertEquals(type.permissions(), account.getPermissions());
    }

    @Test
    void createRejectsMissingType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Account.create(null, "id", "First", "Last", "f@example.com"));
        assertEquals(Validation.MSG_ACCOUNT_TYPE_REQUIRED, e.getMessage());
    }

    @Test
    void subclassHierarchyIsKept() {
        assertTrue(Account.create(AccountType.Manager, "m1", "M", "G", "m@example.com") instanceof Worker);
        assertTrue(Account.create(AccountType.Admin, "a1", "A", "D", "a@example.com") instanceof User);
    }

    @Test
    void accountsCanOnlyBeCreatedThroughTheFactory() {
        for (Class<?> type : new Class<?>[]{Account.class, User.class, Worker.class,
                Manager.class, Admin.class}) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                int modifiers = constructor.getModifiers();
                assertFalse(Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers),
                        "constructor must be package-private: " + constructor);
            }
        }
        assertTrue(Modifier.isAbstract(Account.class.getModifiers()));
    }

    @Test
    void idAndTypeCannotBeChanged() {
        assertTrue(Arrays.stream(Account.class.getMethods()).map(Method::getName)
                .noneMatch(name -> name.equals("setId") || name.equals("setType")));
    }

    @Test
    void hasPermissionFollowsType() {
        Account user = alice();
        Account admin = Account.create(AccountType.Admin, "root", "Ada", "Admin", "a@example.com");

        assertTrue(user.hasPermission(Permission.SUBMIT_SOURCE_REPORT));
        assertFalse(user.hasPermission(Permission.MANAGE_USERS));
        assertFalse(user.hasPermission(null));
        assertTrue(admin.hasPermission(Permission.MANAGE_USERS));
        assertFalse(admin.hasPermission(Permission.SUBMIT_SOURCE_REPORT));
    }

    @Test
    void updateProfileAppliesAllFieldsWhenValid() {
        Account account = alice();

        account.updateProfile("Alicia", "Smythe", "alicia@example.com", "555-0101", "Tech Square");

        assertEquals("Alicia", account.getFname());
        assertEquals("Smythe", account.getLname());
        assertEquals("alicia@example.com", account.getEmail());
        assertEquals("555-0101", account.getPhone());
        assertEquals("Tech Square", account.getAddress());
    }

    @Test
    void updateProfileWithInvalidEmailChangesNothing() {
        Account account = alice();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> account.updateProfile("Alicia", "Smythe", "not-an-email", "555-0101", "Tech Square"));

        assertEquals(Validation.MSG_INVALID_EMAIL, e.getMessage());
        assertUnchanged(account);
    }

    @Test
    void updateProfileWithInvalidNameChangesNothing() {
        Account account = alice();

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> account.updateProfile("Al1cia", "Smythe", "alicia@example.com", "555-0101", "Tech Square"));

        assertEquals(Validation.MSG_INVALID_NAME, e.getMessage());
        assertUnchanged(account);
    }

    private static void assertUnchanged(Account account) {
        assertEquals("Alice", account.getFname());
        assertEquals("Smith", account.getLname());
        assertEquals("alice@example.com", account.getEmail());
        assertEquals("404-555-0000", account.getPhone());
        assertEquals("North Ave", account.getAddress());
    }
}
