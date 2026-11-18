package model;

/**
 * Created by Sheng on 9/27/16.
 * A worker account; can also file water purity reports.
 */
public class Worker extends User {

    Worker(String id, String fname, String lname, String email, String phone, String address) {
        super(id, fname, lname, email, phone, address);
    }

    @Override
    public AccountType getType() {
        return AccountType.Worker;
    }
}
