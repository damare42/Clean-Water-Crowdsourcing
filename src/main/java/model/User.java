package model;

/**
 * Created by Sheng on 9/13/16.
 * A regular account that can file water source reports.
 */
public class User extends Account {

    public User(String id, String fname, String lname, String email) {
        this(id, fname, lname, email, AccountType.User);
    }

    protected User(String id, String fname, String lname, String email, AccountType type) {
        super(id, fname, lname, email, type);
    }
}
