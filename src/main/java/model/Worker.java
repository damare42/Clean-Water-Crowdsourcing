package model;

/**
 * Created by Sheng on 9/27/16.
 * A worker account.
 */
public class Worker extends User {

    public Worker(String id, String fname, String lname, String email) {
        this(id, fname, lname, email, AccountType.Worker);
    }

    protected Worker(String id, String fname, String lname, String email, AccountType type) {
        super(id, fname, lname, email, type);
    }
}
