package model;

/**
 * Created by Sheng on 9/27/16.
 * A manager account.
 */
public class Manager extends Worker {

    public Manager(String id, String fname, String lname, String email) {
        super(id, fname, lname, email, AccountType.Manager);
    }
}
