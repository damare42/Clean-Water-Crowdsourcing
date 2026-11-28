package model;

import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Input validation rules shared by registration, the profile page and water
 * source reports. Every check is a pure function so it can be tested without a UI.
 */
public final class Validation {

    /** Minimum length of an account id (username). */
    public static final int MIN_ID_LENGTH = 2;
    /** Minimum length of a password. */
    public static final int MIN_PASSWORD_LENGTH = 6;

    public static final double MIN_LATITUDE = -90.0;
    public static final double MAX_LATITUDE = 90.0;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;
    /** Highest accepted concentration in parts per million: one million PPM is 100%. */
    public static final double MAX_PPM = 1_000_000.0;
    /**
     * Largest search radius in kilometres for the purity history; about half the
     * Earth's circumference, so it covers almost the whole planet.
     */
    public static final double MAX_RADIUS_KM = 20_000.0;
    /** Earliest year the purity history accepts. */
    public static final int MIN_YEAR = 1900;
    /** Latest year the purity history accepts. */
    public static final int MAX_YEAR = 9999;

    public static final String MSG_INVALID_ID =
            "Username must be at least " + MIN_ID_LENGTH + " characters and contain no spaces";
    public static final String MSG_ID_TAKEN = "Username is already taken";
    public static final String MSG_ACCOUNT_TYPE_REQUIRED = "Please select an account type";
    public static final String MSG_INVALID_NAME =
            "Name fields are required and are restricted to letters only";
    public static final String MSG_INVALID_EMAIL = "Invalid email address";
    public static final String MSG_EMAIL_MISMATCH =
            "Email confirmation does not match your email address";
    public static final String MSG_INVALID_PASSWORD =
            "Password must be at least " + MIN_PASSWORD_LENGTH + " alphanumeric characters";
    public static final String MSG_PASSWORD_MISMATCH =
            "Password confirmation does not match your password";
    public static final String MSG_INVALID_LATITUDE =
            "Latitude must be a number between -90 and 90";
    public static final String MSG_INVALID_LONGITUDE =
            "Longitude must be a number between -180 and 180";
    public static final String MSG_TYPE_REQUIRED = "Please select the type of water";
    public static final String MSG_CONDITION_REQUIRED = "Please select the condition of the water";
    public static final String MSG_PURITY_CONDITION_REQUIRED =
            "Please select the overall condition of the water";
    public static final String MSG_INVALID_VIRUS_PPM =
            "Virus PPM must be a number from 0 to 1000000";
    public static final String MSG_INVALID_CONTAMINANT_PPM =
            "Contaminant PPM must be a number from 0 to 1000000";
    public static final String MSG_INVALID_RADIUS =
            "Radius must be a number of kilometres greater than 0 and at most 20000";
    public static final String MSG_INVALID_YEAR =
            "Year must be a whole number from " + MIN_YEAR + " to " + MAX_YEAR;

    private static final Pattern EMAIL = Pattern.compile(
            "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$");
    private static final Pattern NAME = Pattern.compile("[A-Za-z]+");
    private static final Pattern PASSWORD = Pattern.compile("[A-Za-z0-9]{" + MIN_PASSWORD_LENGTH + ",}");
    private static final Pattern ID = Pattern.compile("\\S{" + MIN_ID_LENGTH + ",}",
            Pattern.UNICODE_CHARACTER_CLASS);
    /** Plain decimal numbers only: no exponents, hex, type suffixes, NaN or Infinity. */
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");
    /** A year: one to four ASCII digits. */
    private static final Pattern YEAR = Pattern.compile("[0-9]{1,4}");

    private Validation() {
    }

    /**
     * @param email the address to check
     * @return true if the address is a syntactically valid e-mail address
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL.matcher(email).matches();
    }

    /**
     * @param name a first or last name
     * @return true if the name is non-empty and consists of letters only
     */
    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /**
     * @param password the password to check
     * @return true if the password has at least {@value #MIN_PASSWORD_LENGTH}
     *         characters, all alphanumeric
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD.matcher(password).matches();
    }

    /**
     * Same as {@link #isValidPassword(String)} without copying the password into a String.
     *
     * @param password the password to check
     * @return true if the password is valid
     */
    public static boolean isValidPassword(char[] password) {
        return password != null && PASSWORD.matcher(CharBuffer.wrap(password)).matches();
    }

    /**
     * @param id the account id (username) to check; surrounding whitespace is ignored
     * @return true if the trimmed id has at least {@value #MIN_ID_LENGTH} characters
     *         and contains no whitespace
     */
    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id.strip()).matches();
    }

    /**
     * @param id an account id as entered
     * @return the id without surrounding whitespace, or null if the id is null
     */
    public static String normalizeId(String id) {
        return id == null ? null : id.strip();
    }

    /**
     * Ids are unique ignoring case. This is the key under which an id is stored
     * and looked up.
     *
     * @param id an account id as entered
     * @return the stripped id in lower case, or null if the id is null
     */
    public static String idKey(String id) {
        return id == null ? null : id.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * @param latitude the latitude in degrees
     * @return true if the latitude is within [-90, 90]
     */
    public static boolean isValidLatitude(double latitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE;
    }

    /**
     * @param longitude the longitude in degrees
     * @return true if the longitude is within [-180, 180]
     */
    public static boolean isValidLongitude(double longitude) {
        return longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    /**
     * Parses user-entered latitude text.
     *
     * @param text the text to parse
     * @return the latitude
     * @throws IllegalArgumentException if the text is not a number in [-90, 90]
     */
    public static double parseLatitude(String text) {
        double value = parseNumber(text, MSG_INVALID_LATITUDE);
        if (!isValidLatitude(value)) {
            throw new IllegalArgumentException(MSG_INVALID_LATITUDE);
        }
        return value;
    }

    /**
     * Parses user-entered longitude text.
     *
     * @param text the text to parse
     * @return the longitude
     * @throws IllegalArgumentException if the text is not a number in [-180, 180]
     */
    public static double parseLongitude(String text) {
        double value = parseNumber(text, MSG_INVALID_LONGITUDE);
        if (!isValidLongitude(value)) {
            throw new IllegalArgumentException(MSG_INVALID_LONGITUDE);
        }
        return value;
    }

    /**
     * @param ppm a concentration in parts per million
     * @return true if the value is a number from 0 to {@value #MAX_PPM}
     */
    public static boolean isValidPpm(double ppm) {
        return ppm >= 0 && ppm <= MAX_PPM;
    }

    /**
     * Parses a user-entered virus concentration. Like coordinates, only plain decimal
     * numbers are accepted (no exponents, NaN or Infinity).
     *
     * @return the value in parts per million; never -0.0
     * @throws IllegalArgumentException if the text is not a number from 0 to {@value #MAX_PPM}
     */
    public static double parseVirusPpm(String text) {
        return parsePpm(text, MSG_INVALID_VIRUS_PPM);
    }

    /**
     * Parses a user-entered contaminant concentration; see {@link #parseVirusPpm}.
     *
     * @return the value in parts per million; never -0.0
     * @throws IllegalArgumentException if the text is not a number from 0 to {@value #MAX_PPM}
     */
    public static double parseContaminantPpm(String text) {
        return parsePpm(text, MSG_INVALID_CONTAMINANT_PPM);
    }

    private static double parsePpm(String text, String message) {
        double value = parseNumber(text, message);
        if (!isValidPpm(value)) {
            throw new IllegalArgumentException(message);
        }
        return value + 0.0;
    }

    /**
     * @param radiusKm a search radius in kilometres
     * @return true if the radius is greater than 0 and at most {@value #MAX_RADIUS_KM}
     */
    public static boolean isValidRadiusKm(double radiusKm) {
        return radiusKm > 0 && radiusKm <= MAX_RADIUS_KM;
    }

    /**
     * Parses a user-entered search radius (plain decimal numbers only).
     *
     * @return the radius in kilometres
     * @throws IllegalArgumentException if the text is not a valid radius
     */
    public static double parseRadiusKm(String text) {
        double value = parseNumber(text, MSG_INVALID_RADIUS);
        if (!isValidRadiusKm(value)) {
            throw new IllegalArgumentException(MSG_INVALID_RADIUS);
        }
        return value;
    }

    /**
     * @param year a calendar year
     * @return true if the year is from {@value #MIN_YEAR} to {@value #MAX_YEAR}
     */
    public static boolean isValidYear(int year) {
        return year >= MIN_YEAR && year <= MAX_YEAR;
    }

    /**
     * Parses a user-entered year: digits only, from {@value #MIN_YEAR} to {@value #MAX_YEAR}.
     *
     * @return the year
     * @throws IllegalArgumentException if the text is not a valid year
     */
    public static int parseYear(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!YEAR.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(MSG_INVALID_YEAR);
        }
        int year = Integer.parseInt(trimmed);
        if (!isValidYear(year)) {
            throw new IllegalArgumentException(MSG_INVALID_YEAR);
        }
        return year;
    }

    private static double parseNumber(String text, String message) {
        if (text == null || !DECIMAL.matcher(text.trim()).matches()) {
            throw new IllegalArgumentException(message);
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message, e);
        }
    }

    /**
     * Checks the editable profile fields of an account.
     *
     * @param fname the first name
     * @param lname the last name
     * @param email the e-mail address
     * @return the first validation error, or empty if all fields are valid
     */
    public static Optional<String> validateProfile(String fname, String lname, String email) {
        if (!isValidName(fname) || !isValidName(lname)) {
            return Optional.of(MSG_INVALID_NAME);
        }
        if (!isValidEmail(email)) {
            return Optional.of(MSG_INVALID_EMAIL);
        }
        return Optional.empty();
    }

    /**
     * Checks a registration form. Does not check whether the id is already taken
     * or whether the account type may be self-registered; see
     * {@code model.service.AuthService#register}.
     *
     * @return the first validation error, or empty if the form is valid
     */
    public static Optional<String> validateRegistration(String id, AccountType type,
                                                        String fname, String lname,
                                                        String email, String emailConfirm,
                                                        char[] password, char[] passwordConfirm) {
        if (!isValidId(id)) {
            return Optional.of(MSG_INVALID_ID);
        }
        if (type == null) {
            return Optional.of(MSG_ACCOUNT_TYPE_REQUIRED);
        }
        Optional<String> profileError = validateProfile(fname, lname, email);
        if (profileError.isPresent()) {
            return profileError;
        }
        if (!email.equals(emailConfirm)) {
            return Optional.of(MSG_EMAIL_MISMATCH);
        }
        if (!isValidPassword(password)) {
            return Optional.of(MSG_INVALID_PASSWORD);
        }
        if (!Arrays.equals(password, passwordConfirm)) {
            return Optional.of(MSG_PASSWORD_MISMATCH);
        }
        return Optional.empty();
    }
}
