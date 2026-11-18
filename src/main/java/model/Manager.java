package model;

/**
 * Created by Sheng on 9/27/16.
 * A manager account; can also view purity history.
 */
public final class Manager extends Worker {

    Manager(String id, String fname, String lname, String email, String phone, String address) {
        super(id, fname, lname, email, phone, address);
    }

    @Override
    public AccountType getType() {
        return AccountType.Manager;
    }
}
