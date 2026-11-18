package model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation tests for the current behaviour of {@link Account}.
 */
class AccountTest {

    @BeforeEach
    void clearStaticState() {
        Authenticator.ACCOUNTS.clear();
        Account.accountList.clear();
    }

    @Test
    void fiveArgConstructorSetsFields() {
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
    void sevenArgConstructorSetsCoreFields() {
        Account account = new Account("bob", "Bob", "Jones", "bob@example.com", AccountType.Manager,
                "555-1234", "1 Main St");

        assertEquals("bob", account.getId());
        assertEquals("Bob", account.getFname());
        assertEquals("Jones", account.getLname());
        assertEquals("bob@example.com", account.getEmail());
        assertEquals(AccountType.Manager, account.getType());
    }

    @Test
    void sevenArgConstructorPhoneAndAddressAreNotVisibleThroughGetters() {
        // Current behaviour: the constructor stores phone/address in JavaFX properties,
        // but getPhone()/getAddress() read separate plain fields that default to "".
        Account account = new Account("bob", "Bob", "Jones", "bob@example.com", AccountType.Manager,
                "555-1234", "1 Main St");

        assertEquals("", account.getPhone());
        assertEquals("", account.getAddress());
    }

    @Test
    void constructorDoesNotRegisterAccount() {
        new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User);

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
        Account account = new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User);

        assertEquals(AccountType.User, account.getAccountTypeProperty().get());
        account.setType(AccountType.Worker);
        assertEquals(AccountType.Worker, account.getAccountTypeProperty().get());
    }

    @Test
    void toStringReturnsId() {
        Account account = new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User);

        assertEquals("alice", account.toString());
        account.setId("other");
        assertEquals("other", account.toString());
    }

    @Test
    void getAccountListReturnsSharedStaticMap() {
        Account account = new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User);

        assertSame(Account.accountList, account.getAccountList());
    }

    @Test
    void ofReturnsRegisteredAccount() {
        Account alice = new Account("alice", "Alice", "Smith", "alice@example.com", AccountType.User);
        Authenticator.addAccount(alice, "secret");

        assertSame(alice, Account.of("alice"));
        assertEquals(1, Account.accountList.size());
    }

    @Test
    void ofCreatesAndRegistersPlaceholderForUnknownId() {
        // Current behaviour: an unknown id yields a new, mostly empty account that is
        // added to the account list (a "phantom" account).
        Account phantom = Account.of("ghost");

        assertEquals("ghost", phantom.getId());
        assertNull(phantom.getFname());
        assertNull(phantom.getLname());
        assertNull(phantom.getEmail());
        assertNull(phantom.getType());
        assertEquals("", phantom.getPhone());
        assertEquals("", phantom.getAddress());
        assertSame(phantom, Account.accountList.get("ghost"));
        assertTrue(Authenticator.ACCOUNTS.isEmpty());
    }

    @Test
    void ofReturnsSameInstanceOnRepeatedCalls() {
        Account first = Account.of("ghost");
        Account second = Account.of("ghost");

        assertSame(first, second);
        assertNotSame(first, Account.of("other"));
        assertEquals(2, Account.accountList.size());
    }

    @Test
    void subclassesAreAccountsWithGivenType() {
        Account worker = new Worker("w", "W", "K", "w@example.com", AccountType.Worker);
        Account manager = new Manager("m", "M", "G", "m@example.com", AccountType.Manager);
        Account admin = new Admin("a", "A", "D", "a@example.com", AccountType.Admin);

        assertTrue(worker instanceof User);
        assertTrue(manager instanceof Worker);
        assertTrue(admin instanceof User);
        assertEquals(AccountType.Worker, worker.getType());
        assertEquals(AccountType.Manager, manager.getType());
        assertEquals(AccountType.Admin, admin.getType());
    }
}
