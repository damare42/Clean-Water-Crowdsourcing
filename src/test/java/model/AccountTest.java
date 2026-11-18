package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTest {

    @BeforeEach
    void clearStaticState() {
        Authenticator.ACCOUNTS.clear();
        Account.accountList.clear();
    }

    private static Account alice() {
        return new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User,
                "404-555-0000", "North Ave");
    }

    @Test
    void fiveArgConstructorSetsFieldsWithEmptyPhoneAndAddress() {
        Account account = new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.Worker);

        assertEquals("alice", account.getId());
        assertEquals("Alice", account.getFname());
        assertEquals("Smith", account.getLname());
        assertEquals("alice@example.com", account.getEmail());
        assertEquals(AccountType.Worker, account.getType());
        assertEquals("", account.getPhone());
        assertEquals("", account.getAddress());
    }

    @Test
    void sevenArgConstructorKeepsPhoneAndAddress() {
        Account account = new Account("bob", "Bob", "Jones", "bob@example.com", AccountType.Manager,
                "555-1234", "1 Main St");

        assertEquals("bob", account.getId());
        assertEquals("Bob", account.getFname());
        assertEquals("Jones", account.getLname());
        assertEquals("bob@example.com", account.getEmail());
        assertEquals(AccountType.Manager, account.getType());
        assertEquals("555-1234", account.getPhone());
        assertEquals("1 Main St", account.getAddress());
    }

    @Test
    void constructorDoesNotRegisterAccount() {
        alice();

        assertTrue(Account.accountList.isEmpty());
    }

    @Test
    void settersUpdateValues() {
        Account account = new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User);

        account.setId("alice2");
        account.setFname("Alicia");
        account.setLname("Smythe");
        account.setEmail("alicia@example.com");
        account.setType(AccountType.Admin);
        account.setPhone("404-555-0000");
        account.setAddress("North Ave");

        assertEquals("alice2", account.getId());
        assertEquals("Alicia", account.getFname());
        assertEquals("Smythe", account.getLname());
        assertEquals("alicia@example.com", account.getEmail());
        assertEquals(AccountType.Admin, account.getType());
        assertEquals("404-555-0000", account.getPhone());
        assertEquals("North Ave", account.getAddress());
    }

    @Test
    void accountTypePropertyReflectsType() {
        Account account = alice();

        assertEquals(AccountType.User, account.getAccountTypeProperty().get());
        account.setType(AccountType.Worker);
        assertEquals(AccountType.Worker, account.getAccountTypeProperty().get());
    }

    @Test
    void toStringReturnsId() {
        Account account = alice();

        assertEquals("alice", account.toString());
        account.setId("other");
        assertEquals("other", account.toString());
    }

    @Test
    void findReturnsRegisteredAccount() {
        Account alice = alice();
        Authenticator.addAccount(alice, "secret");

        assertSame(alice, Account.find("alice").orElseThrow());
        assertEquals(1, Account.accountList.size());
    }

    @Test
    void findDoesNotCreateAccountForUnknownId() {
        assertTrue(Account.find("ghost").isEmpty());
        assertTrue(Account.find(null).isEmpty());

        assertTrue(Account.accountList.isEmpty());
        assertTrue(Authenticator.ACCOUNTS.isEmpty());
        assertTrue(Authenticator.isIdAvailable("ghost"));
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
        assertEquals("id", account.getId());
        assertEquals("First", account.getFname());
        assertEquals("Last", account.getLname());
        assertEquals("f@example.com", account.getEmail());
        assertTrue(Account.accountList.isEmpty());
    }

    @Test
    void createRejectsMissingType() {
        assertThrows(IllegalArgumentException.class,
                () -> Account.create(null, "id", "First", "Last", "f@example.com"));
    }

    @Test
    void subclassConstructorsSetTheirOwnType() {
        assertEquals(AccountType.User, new User("u", "U", "S", "u@example.com").getType());
        assertEquals(AccountType.Worker, new Worker("w", "W", "K", "w@example.com").getType());
        assertEquals(AccountType.Manager, new Manager("m", "M", "G", "m@example.com").getType());
        assertEquals(AccountType.Admin, new Admin("a", "A", "D", "a@example.com").getType());
        assertTrue(new Manager("m", "M", "G", "m@example.com") instanceof Worker);
        assertTrue(new Admin("a", "A", "D", "a@example.com") instanceof User);
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
