package model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"a@b.co", "first.last@example.com", "x_y-z@sub.example.org"})
    void acceptsValidEmails(String email) {
        assertTrue(Validation.isValidEmail(email));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"plain", "a@b", "a@b.c", "@example.com", "a b@example.com", "a@@example.com"})
    void rejectsInvalidEmails(String email) {
        assertFalse(Validation.isValidEmail(email));
    }

    @Test
    void namesMustBeLettersOnly() {
        assertTrue(Validation.isValidName("Alice"));
        assertFalse(Validation.isValidName(""));
        assertFalse(Validation.isValidName(null));
        assertFalse(Validation.isValidName("Al1ce"));
        assertFalse(Validation.isValidName("Mary Ann"));
    }

    @Test
    void passwordMustBeAtLeastSixAlphanumericCharacters() {
        assertFalse(Validation.isValidPassword("abc12"));
        assertTrue(Validation.isValidPassword("abc123"));
        assertTrue(Validation.isValidPassword("abcdefghij1234"));
        assertFalse(Validation.isValidPassword("abc 123"));
        assertFalse(Validation.isValidPassword("abc!123"));
        assertFalse(Validation.isValidPassword((String) null));
        assertTrue(Validation.MSG_INVALID_PASSWORD.contains("at least 6"));
    }

    @Test
    void passwordCharArrayOverloadMatchesStringRules() {
        assertTrue(Validation.isValidPassword("abc123".toCharArray()));
        assertFalse(Validation.isValidPassword("abc12".toCharArray()));
        assertFalse(Validation.isValidPassword("abc 123".toCharArray()));
        assertFalse(Validation.isValidPassword((char[]) null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "alice", " alice ", "\talice\n", "Al1ce_x", "ali.ce@home"})
    void acceptsIdsWithTwoOrMoreNonWhitespaceCharacters(String id) {
        assertTrue(Validation.isValidId(id));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a", " a ", "  ", "\t\t", "al ice", "al\tice", "a\u00A0b", "\u2003\u2003"})
    void rejectsShortOrWhitespaceIds(String id) {
        assertFalse(Validation.isValidId(id));
    }

    @Test
    void idsAreNormalizedForDisplayAndLookup() {
        assertEquals("Alice", Validation.normalizeId("  Alice\t"));
        assertEquals("alice", Validation.idKey("  ALIce "));
        assertEquals(Validation.idKey("alice"), Validation.idKey("ALICE"));
        assertEquals(null, Validation.normalizeId(null));
        assertEquals(null, Validation.idKey(null));
    }

    @Test
    void coordinateRanges() {
        assertTrue(Validation.isValidLatitude(-90));
        assertTrue(Validation.isValidLatitude(90));
        assertFalse(Validation.isValidLatitude(90.5));
        assertFalse(Validation.isValidLatitude(Double.NaN));
        assertTrue(Validation.isValidLongitude(-180));
        assertTrue(Validation.isValidLongitude(180));
        assertFalse(Validation.isValidLongitude(-180.5));
        assertFalse(Validation.isValidLongitude(Double.NaN));
    }

    @Test
    void parseLatitudeAcceptsNumbersInRange() {
        assertEquals(33.77, Validation.parseLatitude(" 33.77 "));
        assertEquals(-90.0, Validation.parseLatitude("-90"));
        assertEquals(45.0, Validation.parseLatitude("+45"));
        assertEquals(45.0, Validation.parseLatitude("45."));
        assertEquals(0.5, Validation.parseLatitude(".5"));
        assertEquals(-0.5, Validation.parseLatitude("-.5"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "abc", "1,5", "91", "-90.1", "NaN", "Infinity", "-Infinity",
            "45d", "45f", "45D", "0x1p4", "0x10", "1e1", "1E1", "4.5e1", "+", "-", ".", "1.2.3",
            "--1", "4 5", "\u0664\u0665"})
    void parseLatitudeRejectsBadInput(String text) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Validation.parseLatitude(text));
        assertEquals(Validation.MSG_INVALID_LATITUDE, e.getMessage());
    }

    @Test
    void parseLongitudeAcceptsNumbersInRange() {
        assertEquals(-84.39, Validation.parseLongitude("-84.39"));
        assertEquals(180.0, Validation.parseLongitude("180"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"east", "181", "-180.01", "NaN", "90d", "0x1p4", "1e2"})
    void parseLongitudeRejectsBadInput(String text) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Validation.parseLongitude(text));
        assertEquals(Validation.MSG_INVALID_LONGITUDE, e.getMessage());
    }

    @Test
    void validateProfile() {
        assertEquals(Optional.empty(), Validation.validateProfile("Alice", "Smith", "a@example.com"));
        assertEquals(Optional.of(Validation.MSG_INVALID_NAME),
                Validation.validateProfile("", "Smith", "a@example.com"));
        assertEquals(Optional.of(Validation.MSG_INVALID_NAME),
                Validation.validateProfile("Alice", "Sm1th", "a@example.com"));
        assertEquals(Optional.of(Validation.MSG_INVALID_EMAIL),
                Validation.validateProfile("Alice", "Smith", "bad"));
    }

    private static Optional<String> register(String id, AccountType type, String email,
                                             String emailConfirm, String password,
                                             String passwordConfirm) {
        return Validation.validateRegistration(id, type, "Alice", "Smith", email, emailConfirm,
                password == null ? null : password.toCharArray(),
                passwordConfirm == null ? null : passwordConfirm.toCharArray());
    }

    @Test
    void validateRegistrationAcceptsValidForm() {
        assertEquals(Optional.empty(), register("alice", AccountType.User,
                "a@example.com", "a@example.com", "secret1", "secret1"));
    }

    @Test
    void validateRegistrationReportsFirstError() {
        assertEquals(Optional.of(Validation.MSG_INVALID_ID), register("a", AccountType.User,
                "a@example.com", "a@example.com", "secret1", "secret1"));
        assertEquals(Optional.of(Validation.MSG_INVALID_ID), register("al ice", AccountType.User,
                "a@example.com", "a@example.com", "secret1", "secret1"));
        assertEquals(Optional.of(Validation.MSG_ACCOUNT_TYPE_REQUIRED), register("alice", null,
                "a@example.com", "a@example.com", "secret1", "secret1"));
        assertEquals(Optional.of(Validation.MSG_INVALID_NAME),
                Validation.validateRegistration("alice", AccountType.User, "Alice", "",
                        "a@example.com", "a@example.com", "secret1".toCharArray(),
                        "secret1".toCharArray()));
        assertEquals(Optional.of(Validation.MSG_INVALID_EMAIL), register("alice", AccountType.User,
                "bad", "bad", "secret1", "secret1"));
        assertEquals(Optional.of(Validation.MSG_EMAIL_MISMATCH), register("alice", AccountType.User,
                "a@example.com", "b@example.com", "secret1", "secret1"));
        assertEquals(Optional.of(Validation.MSG_INVALID_PASSWORD), register("alice", AccountType.User,
                "a@example.com", "a@example.com", "short", "short"));
        assertEquals(Optional.of(Validation.MSG_PASSWORD_MISMATCH), register("alice", AccountType.User,
                "a@example.com", "a@example.com", "secret1", "secret2"));
        assertEquals(Optional.of(Validation.MSG_PASSWORD_MISMATCH), register("alice", AccountType.User,
                "a@example.com", "a@example.com", "secret1", null));
        assertEquals(Optional.of(Validation.MSG_INVALID_PASSWORD), register("alice", AccountType.User,
                "a@example.com", "a@example.com", null, null));
    }
}
