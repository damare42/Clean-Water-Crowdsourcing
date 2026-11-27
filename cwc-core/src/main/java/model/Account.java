package model;

import java.util.Optional;
import java.util.Set;

/**
 * Created by Sheng on 9/27/16.
 * Represents a single account in the system.
 * <p>
 * The role of an account is defined only by its subclass ({@link User},
 * {@link Worker}, {@link Manager} or {@link Admin}); {@link #getType()} is derived
 * from it and cannot be changed. Accounts are created with {@link #create}; the id
 * is fixed once created. Accounts are not stored anywhere by themselves; see
 * {@code model.service.AuthService} for registration and persistence.
 */
public abstract class Account {

    private final String id;
    private String fname;
    private String lname;
    private String email;
    private String phone;
    private String address;

    /**
     * Only the subclasses in this package can create accounts; everyone else uses
     * {@link #create}.
     */
    Account(String id, String fname, String lname, String email, String phone, String address) {
        if (!Validation.isValidId(id)) {
            throw new IllegalArgumentException(Validation.MSG_INVALID_ID);
        }
        this.id = Validation.normalizeId(id);
        this.fname = fname;
        this.lname = lname;
        this.email = email;
        this.phone = phone == null ? "" : phone;
        this.address = address == null ? "" : address;
    }

    /**
     * Creates an account with an empty phone number and address.
     *
     * @see #create(AccountType, String, String, String, String, String, String)
     */
    public static Account create(AccountType type, String id, String fname, String lname,
                                 String email) {
        return create(type, id, fname, lname, email, "", "");
    }

    /**
     * Creates an account of the subclass that matches the given account type.
     *
     * @param type the account type; selects User, Worker, Manager or Admin
     * @param id the account id; surrounding whitespace is removed
     * @return a new, unregistered account
     * @throws IllegalArgumentException if the type is null or the id is invalid
     */
    public static Account create(AccountType type, String id, String fname, String lname,
                                 String email, String phone, String address) {
        if (type == null) {
            throw new IllegalArgumentException(Validation.MSG_ACCOUNT_TYPE_REQUIRED);
        }
        return switch (type) {
            case User -> new User(id, fname, lname, email, phone, address);
            case Worker -> new Worker(id, fname, lname, email, phone, address);
            case Manager -> new Manager(id, fname, lname, email, phone, address);
            case Admin -> new Admin(id, fname, lname, email, phone, address);
        };
    }

    /**
     * Updates the editable profile fields of this object. All fields are validated
     * first; if any is invalid nothing is changed. This does not persist anything;
     * use {@code AccountService#updateProfile} for that.
     *
     * @throws IllegalArgumentException with a user-facing message if validation fails
     */
    public void updateProfile(String fname, String lname, String email, String phone,
                              String address) {
        Optional<String> error = Validation.validateProfile(fname, lname, email);
        if (error.isPresent()) {
            throw new IllegalArgumentException(error.get());
        }
        this.fname = fname;
        this.lname = lname;
        this.email = email;
        this.phone = phone == null ? "" : phone;
        this.address = address == null ? "" : address;
    }

    /**
     * @return the account id as entered at registration, without surrounding
     *         whitespace. Ids are unique ignoring case.
     */
    public String getId() {
        return id;
    }

    /**
     * @return the key used to look the account up: the id in lower case
     */
    public String getIdKey() {
        return Validation.idKey(id);
    }

    public String getFname() {
        return fname;
    }

    public String getLname() {
        return lname;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    /**
     * @return the account type, determined by the subclass: User, Worker, Manager or Admin
     */
    public abstract AccountType getType();

    /**
     * @return the permissions of this account's type
     */
    public Set<Permission> getPermissions() {
        return getType().permissions();
    }

    /**
     * @param permission the permission to check
     * @return true if this account's type grants the permission
     */
    public boolean hasPermission(Permission permission) {
        return getType().hasPermission(permission);
    }

    /**
     * @return the account id
     */
    @Override
    public String toString() {
        return id;
    }
}
