package model;

import java.util.Objects;

/**
 * One row of the administrator's account list: an account's id, type, name, e-mail
 * address and whether it is banned.
 *
 * @param id        the account id as entered at registration
 * @param type      the account type
 * @param firstName the first name
 * @param lastName  the last name
 * @param email     the e-mail address
 * @param banned    true if the account may not log in
 */
public record AccountSummary(String id, AccountType type, String firstName, String lastName,
                             String email, boolean banned) {

    public AccountSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    /**
     * @return "Banned" or "Active"
     */
    public String status() {
        return banned ? "Banned" : "Active";
    }
}
