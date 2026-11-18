package model;

/**
 * Created by Sheng on 9/27/16.
 * An administrator account; manages users.
 */
public final class Admin extends User {

    Admin(String id, String fname, String lname, String email, String phone, String address) {
        super(id, fname, lname, email, phone, address);
    }

    @Override
    public AccountType getType() {
        return AccountType.Admin;
    }
}
