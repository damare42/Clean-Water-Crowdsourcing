package model.persistence;

import model.Account;
import model.AccountType;
import model.Validation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * An {@link AccountRepository} backed by the {@code accounts} table of a
 * {@link SqliteDatabase}. Accounts are keyed by their id in lower case
 * ({@code username_key}); the id as entered is kept for display.
 */
public final class SqliteAccountRepository implements AccountRepository {

    private static final String COLUMNS =
            "username, account_type, first_name, last_name, email, phone, address";

    private final SqliteDatabase database;

    public SqliteAccountRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void add(Account account, String passwordHash) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(passwordHash, "passwordHash");
        int inserted = database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "INSERT INTO accounts (username_key, " + COLUMNS + ", password_hash)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                            + " ON CONFLICT (username_key) DO NOTHING")) {
                statement.setString(1, account.getIdKey());
                statement.setString(2, account.getId());
                statement.setString(3, account.getType().name());
                statement.setString(4, account.getFname());
                statement.setString(5, account.getLname());
                statement.setString(6, account.getEmail());
                statement.setString(7, account.getPhone());
                statement.setString(8, account.getAddress());
                statement.setString(9, passwordHash);
                return statement.executeUpdate();
            }
        });
        if (inserted == 0) {
            throw new DuplicateKeyException("Account id already exists: " + account.getId());
        }
    }

    @Override
    public Optional<Account> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT " + COLUMNS + " FROM accounts WHERE username_key = ?")) {
                statement.setString(1, Validation.idKey(id));
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(toAccount(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public boolean existsById(String id) {
        return findPasswordHash(id).isPresent();
    }

    @Override
    public Optional<String> findPasswordHash(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT password_hash FROM accounts WHERE username_key = ?")) {
                statement.setString(1, Validation.idKey(id));
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public boolean updateProfile(Account account) {
        Objects.requireNonNull(account, "account");
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "UPDATE accounts SET first_name = ?, last_name = ?, email = ?, phone = ?,"
                            + " address = ? WHERE username_key = ?")) {
                statement.setString(1, account.getFname());
                statement.setString(2, account.getLname());
                statement.setString(3, account.getEmail());
                statement.setString(4, account.getPhone());
                statement.setString(5, account.getAddress());
                statement.setString(6, account.getIdKey());
                return statement.executeUpdate() == 1;
            }
        });
    }

    @Override
    public boolean updatePasswordHash(String id, String passwordHash) {
        Objects.requireNonNull(passwordHash, "passwordHash");
        if (id == null) {
            return false;
        }
        return database.execute(c -> {
            try (PreparedStatement statement = c.prepareStatement(
                    "UPDATE accounts SET password_hash = ? WHERE username_key = ?")) {
                statement.setString(1, passwordHash);
                statement.setString(2, Validation.idKey(id));
                return statement.executeUpdate() == 1;
            }
        });
    }

    private static Account toAccount(ResultSet rs) throws SQLException {
        String id = rs.getString("username");
        try {
            return Account.create(AccountType.valueOf(rs.getString("account_type")), id,
                    rs.getString("first_name"), rs.getString("last_name"), rs.getString("email"),
                    rs.getString("phone"), rs.getString("address"));
        } catch (IllegalArgumentException e) {
            throw new DataAccessException("Invalid account row for id " + id, e);
        }
    }
}
