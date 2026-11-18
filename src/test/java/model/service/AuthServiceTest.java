package model.service;

import model.Account;
import model.AccountSummary;
import model.AccountType;
import model.Validation;
import model.Worker;
import model.persistence.AccountRepository;
import model.persistence.DataAccessException;
import model.persistence.InMemoryAccountRepository;
import model.persistence.InMemorySecurityLogRepository;
import model.security.LockoutPolicy;
import model.security.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    private InMemoryAccountRepository accounts;
    private PasswordHasher hasher;
    private AuthService auth;
    private InMemorySecurityLogRepository securityEvents;

    @BeforeEach
    void setUp() {
        accounts = new InMemoryAccountRepository();
        hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);
        securityEvents = new InMemorySecurityLogRepository();
        auth = authService(accounts, hasher);
    }

    private AuthService authService(AccountRepository repository, PasswordHasher passwordHasher) {
        return new AuthService(repository, passwordHasher,
                new SecurityLog(securityEvents, Clock.systemUTC()), LockoutPolicy.DEFAULT);
    }

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    private Account register(String id, AccountType type, String password) {
        return auth.register(id, type, "First", "Last", "f@example.com", "f@example.com",
                pw(password), pw(password));
    }

    @Test
    void registerStoresAccountOfMatchingSubclass() {
        Account account = auth.register("worker1", AccountType.Worker, "Wendy", "Ward",
                "wendy@example.com", "wendy@example.com", pw("secret1"), pw("secret1"));

        assertEquals(Worker.class, account.getClass());
        assertEquals(AccountType.Worker, account.getType());
        Account stored = accounts.findById("worker1").orElseThrow();
        assertEquals(Worker.class, stored.getClass());
        assertEquals("Wendy", stored.getFname());
        assertEquals("wendy@example.com", stored.getEmail());
    }

    @Test
    void registerStoresOnlyAPbkdf2Hash() {
        register("alice", AccountType.User, "secret1");

        String stored = accounts.findPasswordHash("alice").orElseThrow();
        assertFalse(stored.contains("secret1"));
        assertTrue(stored.startsWith(PasswordHasher.ALGORITHM_ID + "$"));
        assertEquals(OptionalInt.of(PasswordHasher.MIN_ITERATIONS), PasswordHasher.iterationsOf(stored));
        assertTrue(hasher.verify(pw("secret1"), stored));
    }

    @Test
    void registerDoesNotModifyPasswordArrays() {
        char[] password = pw("secret1");
        char[] confirm = pw("secret1");

        auth.register("alice", AccountType.User, "A", "B", "a@example.com", "a@example.com",
                password, confirm);

        assertArrayEquals(pw("secret1"), password);
        assertArrayEquals(pw("secret1"), confirm);
    }

    @Test
    void registerStripsId() {
        Account account = register("  Alice ", AccountType.User, "secret1");

        assertEquals("Alice", account.getId());
        assertEquals("Alice", accounts.findById("alice").orElseThrow().getId());
    }

    @Test
    void registerWithInvalidFormStoresNothing() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> register("worker1", AccountType.Worker, "short"));

        assertEquals(Validation.MSG_INVALID_PASSWORD, e.getMessage());
        assertFalse(accounts.existsById("worker1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "  ", "al ice", " a "})
    void registerRejectsInvalidIds(String id) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> register(id, AccountType.User, "secret1"));

        assertEquals(Validation.MSG_INVALID_ID, e.getMessage());
    }

    @Test
    void registerWithTakenIdIgnoringCaseIsRejected() {
        register("alice", AccountType.User, "secret1");

        for (String id : new String[]{"alice", "ALICE", " Alice "}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> register(id, AccountType.Manager, "other12"));
            assertEquals(Validation.MSG_ID_TAKEN, e.getMessage());
        }
        assertEquals(AccountType.User, accounts.findById("alice").orElseThrow().getType());
        assertTrue(auth.login("alice", pw("secret1")).isPresent());
        assertTrue(auth.login("alice", pw("other12")).isEmpty());
    }

    @Test
    void adminCannotSelfRegister() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> register("root", AccountType.Admin, "secret1"));

        assertEquals(AuthService.MSG_ACCOUNT_TYPE_NOT_ALLOWED, e.getMessage());
        assertFalse(accounts.existsById("root"));
    }

    @Test
    void registerWithoutTypeIsRejected() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> register("alice", null, "secret1"));

        assertEquals(Validation.MSG_ACCOUNT_TYPE_REQUIRED, e.getMessage());
    }

    @Test
    void loginAcceptsCorrectPasswordAndReturnsStoredAccount() {
        register("alice", AccountType.Manager, "secret1");

        Account account = auth.login("alice", pw("secret1")).orElseThrow();

        assertEquals("alice", account.getId());
        assertEquals(AccountType.Manager, account.getType());
    }

    @Test
    void loginIgnoresIdCaseAndWhitespace() {
        register("Alice", AccountType.User, "secret1");

        assertEquals("Alice", auth.login("alice", pw("secret1")).orElseThrow().getId());
        assertEquals("Alice", auth.login(" ALICE ", pw("secret1")).orElseThrow().getId());
    }

    @Test
    void loginRejectsWrongPassword() {
        register("alice", AccountType.User, "secret1");

        assertTrue(auth.login("alice", pw("wrong12")).isEmpty());
        assertTrue(auth.login("alice", pw("SECRET1")).isEmpty());
        assertTrue(auth.login("alice", pw("")).isEmpty());
        assertTrue(auth.login("alice", null).isEmpty());
    }

    @Test
    void loginRejectsUnknownOrInvalidId() {
        assertTrue(auth.login("nobody", pw("secret1")).isEmpty());
        assertTrue(auth.login(null, null).isEmpty());
        assertTrue(auth.login("", pw("secret1")).isEmpty());
        assertFalse(accounts.existsById("nobody"));
    }

    @Test
    void loginDoesNotModifyPasswordArray() {
        register("alice", AccountType.User, "secret1");
        char[] password = pw("secret1");

        auth.login("alice", password);

        assertArrayEquals(pw("secret1"), password);
    }

    @Test
    void loginUpgradesWeakHash() {
        AuthService weak = authService(accounts, new PasswordHasher(PasswordHasher.MIN_ITERATIONS));
        weak.register("alice", AccountType.User, "A", "B", "a@example.com", "a@example.com",
                pw("secret1"), pw("secret1"));
        PasswordHasher stronger = new PasswordHasher(2 * PasswordHasher.MIN_ITERATIONS);
        AuthService strong = authService(accounts, stronger);

        assertTrue(strong.login("alice", pw("wrong12")).isEmpty());
        assertEquals(OptionalInt.of(PasswordHasher.MIN_ITERATIONS),
                PasswordHasher.iterationsOf(accounts.findPasswordHash("alice").orElseThrow()));

        assertTrue(strong.login("alice", pw("secret1")).isPresent());

        String upgraded = accounts.findPasswordHash("alice").orElseThrow();
        assertEquals(OptionalInt.of(2 * PasswordHasher.MIN_ITERATIONS), PasswordHasher.iterationsOf(upgraded));
        assertTrue(strong.login("alice", pw("secret1")).isPresent());
        assertTrue(weak.login("alice", pw("secret1")).isPresent());
    }

    @Test
    void failedHashUpgradeDoesNotFailACorrectLogin() {
        AuthService weak = authService(accounts, new PasswordHasher(PasswordHasher.MIN_ITERATIONS));
        weak.register("alice", AccountType.User, "A", "B", "a@example.com", "a@example.com",
                pw("secret1"), pw("secret1"));
        String weakHash = accounts.findPasswordHash("alice").orElseThrow();
        AccountRepository failingWrites = new FailingHashWrites(accounts);
        AuthService strong = authService(failingWrites,
                new PasswordHasher(2 * PasswordHasher.MIN_ITERATIONS));

        assertEquals("alice", strong.login("alice", pw("secret1")).orElseThrow().getId());
        assertEquals(weakHash, accounts.findPasswordHash("alice").orElseThrow());
        assertTrue(strong.login("alice", pw("wrong12")).isEmpty());
    }

    /** Delegates to another repository but fails every password hash update. */
    private static final class FailingHashWrites implements AccountRepository {
        private final AccountRepository delegate;

        FailingHashWrites(AccountRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public void add(Account account, String passwordHash) {
            delegate.add(account, passwordHash);
        }

        @Override
        public Optional<Account> findById(String id) {
            return delegate.findById(id);
        }

        @Override
        public boolean existsById(String id) {
            return delegate.existsById(id);
        }

        @Override
        public Optional<String> findPasswordHash(String id) {
            return delegate.findPasswordHash(id);
        }

        @Override
        public boolean updateProfile(Account account) {
            return delegate.updateProfile(account);
        }

        @Override
        public boolean updatePasswordHash(String id, String passwordHash) {
            throw new DataAccessException("database is read-only");
        }

        @Override
        public boolean addFirstOfType(Account account, String passwordHash) {
            return delegate.addFirstOfType(account, passwordHash);
        }

        @Override
        public boolean existsByType(AccountType type) {
            return delegate.existsByType(type);
        }

        @Override
        public boolean isBanned(String id) {
            return delegate.isBanned(id);
        }

        @Override
        public boolean setBanned(String id, boolean banned) {
            return delegate.setBanned(id, banned);
        }

        @Override
        public boolean delete(String id) {
            return delegate.delete(id);
        }

        @Override
        public List<AccountSummary> findAllSummaries() {
            return delegate.findAllSummaries();
        }
    }

    @Test
    void isIdAvailable() {
        assertTrue(auth.isIdAvailable("alice"));
        register("alice", AccountType.User, "secret1");

        assertFalse(auth.isIdAvailable("alice"));
        assertFalse(auth.isIdAvailable("ALICE"));
        assertFalse(auth.isIdAvailable(" alice "));
        assertTrue(auth.isIdAvailable("bob"));
        assertFalse(auth.isIdAvailable(null));
        assertFalse(auth.isIdAvailable("a"));
        assertFalse(auth.isIdAvailable("b ob"));
    }

    @Test
    void findAccountNeverCreatesAccounts() {
        assertTrue(auth.findAccount("ghost").isEmpty());
        assertTrue(auth.findAccount(null).isEmpty());
        assertFalse(accounts.existsById("ghost"));
        assertTrue(auth.isIdAvailable("ghost"));
    }
}
