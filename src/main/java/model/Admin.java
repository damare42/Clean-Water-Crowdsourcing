package model;

/**
 * Created by Sheng on 9/27/16.
 * An administrator account.
 */
public class Admin extends User {

    public Admin(String id, String fname, String lname, String email) {
        super(id, fname, lname, email, AccountType.Admin);
    }
}
