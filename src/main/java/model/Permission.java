package model;

/**
 * Something an account may be allowed to do. Which account type has which
 * permission is defined in one place: {@link AccountType#permissions()}.
 */
public enum Permission {
    /** File a water source report. */
    SUBMIT_SOURCE_REPORT,
    /** See the list of water source reports. */
    VIEW_SOURCE_REPORTS,
    /** File a water purity report. */
    SUBMIT_PURITY_REPORT,
    /** See water purity reports. */
    VIEW_PURITY_REPORTS,
    /** See the historical purity chart for a location. */
    VIEW_PURITY_HISTORY,
    /** Ban, unban and delete accounts and read the security log. */
    MANAGE_USERS
}
