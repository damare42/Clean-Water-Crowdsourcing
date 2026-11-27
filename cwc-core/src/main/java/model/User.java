package model;

/**
 * Created by Sheng on 9/13/16.
 * A regular account that can file and view water source reports.
 */
public class User extends Account {

    User(String id, String fname, String lname, String email, String phone, String address) {
        super(id, fname, lname, email, phone, address);
    }

    @Override
    public AccountType getType() {
        return AccountType.User;
    }
}
