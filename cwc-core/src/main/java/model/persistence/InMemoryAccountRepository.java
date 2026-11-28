package model.persistence;

import model.Account;
import model.AccountSummary;
import model.AccountType;
import model.Validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * An {@link AccountRepository} that keeps accounts in memory, for tests. It stores
 * copies of the field values, so it behaves like a database: changing a returned
 * account does not change what is stored. Thread-safe.
 */
public final class InMemoryAccountRepository implements AccountRepository {

    private record Row(AccountType type, String id, String fname, String lname, String email,
                       String phone, String address, String passwordHash, boolean banned) {

        static Row of(Account account, String passwordHash) {
            return new Row(account.getType(), account.getId(), account.getFname(),
                    account.getLname(), account.getEmail(), account.getPhone(),
                    account.getAddress(), passwordHash, false);
        }

        Account toAccount() {
            return Account.create(type, id, fname, lname, email, phone, address);
        }

        AccountSummary toSummary() {
            return new AccountSummary(id, type, fname, lname, email, banned);
        }
    }

    /** Keyed by lower-case id, so iteration is ordered like the SQLite repository. */
    private final Map<String, Row> rows = new TreeMap<>();

    @Override
    public synchronized void add(Account account, String passwordHash) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(passwordHash, "passwordHash");
        if (rows.putIfAbsent(account.getIdKey(), Row.of(account, passwordHash)) != null) {
            throw new DuplicateKeyException("Account id already exists: " + account.getId());
        }
    }

    @Override
    public synchronized Optional<Account> findById(String id) {
        return row(id).map(Row::toAccount);
    }

    @Override
    public synchronized boolean existsById(String id) {
        return row(id).isPresent();
    }

    @Override
    public synchronized Optional<String> findPasswordHash(String id) {
        return row(id).map(Row::passwordHash);
    }

    @Override
    public synchronized boolean updateProfile(Account account) {
        Row old = rows.get(account.getIdKey());
        if (old == null) {
            return false;
        }
        rows.put(account.getIdKey(), new Row(old.type(), old.id(), account.getFname(),
                account.getLname(), account.getEmail(), account.getPhone(),
                account.getAddress(), old.passwordHash(), old.banned()));
        return true;
    }

    @Override
    public synchronized boolean updatePasswordHash(String id, String passwordHash) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        Optional<Row> old = row(id);
        old.ifPresent(r -> rows.put(Validation.idKey(id), new Row(r.type(), r.id(), r.fname(),
                r.lname(), r.email(), r.phone(), r.address(), passwordHash, r.banned())));
        return old.isPresent();
    }

    @Override
    public synchronized boolean addFirstOfType(Account account, String passwordHash) {
        Objects.requireNonNull(account, "account");
        if (existsByType(account.getType())) {
            return false;
        }
        add(account, passwordHash);
        return true;
    }

    @Override
    public synchronized boolean existsByType(AccountType type) {
        return rows.values().stream().anyMatch(r -> r.type() == type);
    }

    @Override
    public synchronized boolean isBanned(String id) {
        return row(id).map(Row::banned).orElse(false);
    }

    @Override
    public synchronized boolean setBanned(String id, boolean banned) {
        Optional<Row> old = row(id);
        old.ifPresent(r -> rows.put(Validation.idKey(id), new Row(r.type(), r.id(), r.fname(),
                r.lname(), r.email(), r.phone(), r.address(), r.passwordHash(), banned)));
        return old.isPresent();
    }

    @Override
    public synchronized boolean delete(String id) {
        return id != null && rows.remove(Validation.idKey(id)) != null;
    }

    @Override
    public synchronized List<AccountSummary> findAllSummaries() {
        return rows.values().stream().map(Row::toSummary).toList();
    }

    private Optional<Row> row(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(rows.get(Validation.idKey(id)));
    }
}
