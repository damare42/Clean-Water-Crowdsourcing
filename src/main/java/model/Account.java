package model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by Sheng on 9/27/16.
 * Represents a single account in the system.
 */
public class Account {

    /**
     * All registered accounts, keyed by id.
     */
    public static final Map<String, Account> accountList = new HashMap<>();

    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty fname = new SimpleStringProperty();
    private final StringProperty lname = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty address = new SimpleStringProperty();
    private final ObjectProperty<AccountType> type = new SimpleObjectProperty<>();

    /**
     * Creates an account with an empty phone number and address.
     */
    public Account(String id, String fname, String lname, String email, AccountType type) {
        this(id, fname, lname, email, type, "", "");
    }

    /**
     * Creates an account including phone number and address.
     */
    public Account(String id, String fname, String lname, String email, AccountType type,
                   String phone, String address) {
        this.id.set(id);
        this.fname.set(fname);
        this.lname.set(lname);
        this.email.set(email);
        this.type.set(type);
        this.phone.set(phone);
        this.address.set(address);
    }

    /**
     * Creates an account of the subclass that matches the given account type.
     *
     * @param type the account type; selects User, Worker, Manager or Admin
     * @return a new, unregistered account
     * @throws IllegalArgumentException if the type is null
     */
    public static Account create(AccountType type, String id, String fname, String lname,
                                 String email) {
        if (type == null) {
            throw new IllegalArgumentException(Validation.MSG_ACCOUNT_TYPE_REQUIRED);
        }
        return switch (type) {
            case User -> new User(id, fname, lname, email);
            case Worker -> new Worker(id, fname, lname, email);
            case Manager -> new Manager(id, fname, lname, email);
            case Admin -> new Admin(id, fname, lname, email);
        };
    }

    /**
     * Looks up a registered account. Never creates an account.
     *
     * @param id the id of the account
     * @return the registered account, or empty if no account has that id
     */
    public static Optional<Account> find(String id) {
        return Optional.ofNullable(accountList.get(id));
    }

    /**
     * Updates the editable profile fields. All fields are validated first; if any is
     * invalid nothing is changed.
     *
     * @throws IllegalArgumentException with a user-facing message if validation fails
     */
    public void updateProfile(String fname, String lname, String email, String phone,
                              String address) {
        Optional<String> error = Validation.validateProfile(fname, lname, email);
        if (error.isPresent()) {
            throw new IllegalArgumentException(error.get());
        }
        setFname(fname);
        setLname(lname);
        setEmail(email);
        setPhone(phone);
        setAddress(address);
    }

    public String getId() {
        return id.get();
    }

    public void setId(String id) {
        this.id.set(id);
    }

    public String getFname() {
        return fname.get();
    }

    public void setFname(String fname) {
        this.fname.set(fname);
    }

    public String getLname() {
        return lname.get();
    }

    public void setLname(String lname) {
        this.lname.set(lname);
    }

    public String getEmail() {
        return email.get();
    }

    public void setEmail(String email) {
        this.email.set(email);
    }

    public String getPhone() {
        return phone.get();
    }

    public void setPhone(String phone) {
        this.phone.set(phone);
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String address) {
        this.address.set(address);
    }

    /**
     * @return the account type: User, Worker, Manager or Admin
     */
    public AccountType getType() {
        return type.get();
    }

    public void setType(AccountType type) {
        this.type.set(type);
    }

    public ObjectProperty<AccountType> getAccountTypeProperty() {
        return type;
    }

    /**
     * @return the account id
     */
    @Override
    public String toString() {
        return id.get();
    }
}
