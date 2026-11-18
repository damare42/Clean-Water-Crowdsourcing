package model.persistence;

import model.Account;
import model.AccountType;
import model.Validation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An {@link AccountRepository} that keeps accounts in memory, for tests. It stores
 * copies of the field values, so it behaves like a database: changing a returned
 * account does not change what is stored. Thread-safe.
 */
public final class InMemoryAccountRepository implements AccountRepository {

    private record Row(AccountType type, String id, String fname, String lname, String email,
                       String phone, String address, String passwordHash) {

        static Row of(Account account, String passwordHash) {
            return new Row(account.getType(), account.getId(), account.getFname(),
                    account.getLname(), account.getEmail(), account.getPhone(),
                    account.getAddress(), passwordHash);
        }

        Account toAccount() {
            return Account.create(type, id, fname, lname, email, phone, address);
        }
    }

    private final Map<String, Row> rows = new HashMap<>();

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
                account.getAddress(), old.passwordHash()));
        return true;
    }

    @Override
    public synchronized boolean updatePasswordHash(String id, String passwordHash) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        Optional<Row> old = row(id);
        old.ifPresent(r -> rows.put(Validation.idKey(id), new Row(r.type(), r.id(), r.fname(),
                r.lname(), r.email(), r.phone(), r.address(), passwordHash)));
        return old.isPresent();
    }

    private Optional<Row> row(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(rows.get(Validation.idKey(id)));
    }
}
