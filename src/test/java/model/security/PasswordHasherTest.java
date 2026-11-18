package model.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    /** Fast hasher for most tests; the default count is tested separately. */
    private final PasswordHasher hasher = new PasswordHasher(PasswordHasher.MIN_ITERATIONS);

    private static char[] pw(String s) {
        return s.toCharArray();
    }

    @Test
    void defaultIterationsMeetOwaspRecommendation() {
        assertTrue(PasswordHasher.DEFAULT_ITERATIONS >= 210_000);
        assertEquals(PasswordHasher.DEFAULT_ITERATIONS, new PasswordHasher().getIterations());
    }

    @Test
    void defaultHasherProducesVerifiableHashWithDefaultIterations() {
        PasswordHasher strong = new PasswordHasher();
        String hash = strong.hash(pw("secret1"));

        assertEquals(OptionalInt.of(PasswordHasher.DEFAULT_ITERATIONS), PasswordHasher.iterationsOf(hash));
        assertTrue(strong.verify(pw("secret1"), hash));
        assertFalse(strong.verify(pw("secret2"), hash));
    }

    @Test
    void verifyAcceptsCorrectPassword() {
        String hash = hasher.hash(pw("secret1"));

        assertTrue(hasher.verify(pw("secret1"), hash));
    }

    @ParameterizedTest
    @ValueSource(strings = {"secret2", "SECRET1", "secret", "secret11", " secret1", "x"})
    void verifyRejectsWrongPassword(String wrong) {
        String hash = hasher.hash(pw("secret1"));

        assertFalse(hasher.verify(pw(wrong), hash));
    }

    @Test
    void verifyRejectsNullAndEmptyPassword() {
        String hash = hasher.hash(pw("secret1"));

        assertFalse(hasher.verify(null, hash));
        assertFalse(hasher.verify(new char[0], hash));
    }

    @Test
    void hashRejectsNullAndEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(null));
        assertThrows(IllegalArgumentException.class, () -> hasher.hash(new char[0]));
    }

    @Test
    void hashHasDocumentedFormat() {
        String hash = hasher.hash(pw("secret1"));
        String[] parts = hash.split("\\$");

        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertEquals(String.valueOf(PasswordHasher.MIN_ITERATIONS), parts[1]);
        assertEquals(PasswordHasher.SALT_BYTES, Base64.getDecoder().decode(parts[2]).length);
        assertEquals(PasswordHasher.HASH_BYTES, Base64.getDecoder().decode(parts[3]).length);
        assertFalse(hash.contains("secret1"));
    }

    @Test
    void samePasswordGetsDifferentSaltsAndHashes() {
        String first = hasher.hash(pw("secret1"));
        String second = hasher.hash(pw("secret1"));

        assertNotEquals(first, second);
        assertNotEquals(first.split("\\$")[2], second.split("\\$")[2]);
        assertTrue(hasher.verify(pw("secret1"), first));
        assertTrue(hasher.verify(pw("secret1"), second));
    }

    @Test
    void hashingDoesNotModifyTheCallersArray() {
        char[] password = pw("secret1");

        String hash = hasher.hash(password);
        hasher.verify(password, hash);

        assertArrayEquals(pw("secret1"), password);
    }

    @Test
    void verifyUsesStoredIterationCount() {
        PasswordHasher old = new PasswordHasher(2_000);
        String oldHash = old.hash(pw("secret1"));
        PasswordHasher current = new PasswordHasher(5_000);

        assertEquals(OptionalInt.of(2_000), PasswordHasher.iterationsOf(oldHash));
        assertTrue(current.verify(pw("secret1"), oldHash));
        assertFalse(current.verify(pw("secret2"), oldHash));
    }

    @Test
    void verifyFailsIfStoredIterationCountIsTamperedWith() {
        String hash = hasher.hash(pw("secret1"));
        String tampered = hash.replaceFirst("\\$" + PasswordHasher.MIN_ITERATIONS + "\\$", "\\$1001\\$");

        assertEquals(OptionalInt.of(1001), PasswordHasher.iterationsOf(tampered));
        assertFalse(hasher.verify(pw("secret1"), tampered));
    }

    @Test
    void needsRehashWhenStoredIterationsAreLower() {
        String weak = new PasswordHasher(2_000).hash(pw("secret1"));
        String strong = new PasswordHasher(5_000).hash(pw("secret1"));
        PasswordHasher current = new PasswordHasher(5_000);

        assertTrue(current.needsRehash(weak));
        assertFalse(current.needsRehash(strong));
        assertTrue(current.needsRehash("garbage"));
        assertTrue(current.needsRehash(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "garbage",
            "secret1",
            "pbkdf2-sha256$1000$AAAAAAAAAAAAAAAAAAAAAA==",
            "pbkdf2-sha256$1000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=$x",
            "pbkdf2-sha1$1000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$abc$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$-5$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$10$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$2147483647$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$99999999999$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$1000$!!notbase64!!$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$1000$AAAAAAAAAAAAAAAAAAAAAA==$!!notbase64!!",
            "pbkdf2-sha256$1000$AAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "pbkdf2-sha256$1000$AAAAAAAAAAAAAAAAAAAAAA==$AAAA",
            "pbkdf2-sha256$1000$AAAAAAAAAAAAAAAAAAAAAA==$",
    })
    void malformedHashesNeverVerifyAndHaveNoIterations(String encoded) {
        assertFalse(hasher.verify(pw("secret1"), encoded));
        assertEquals(OptionalInt.empty(), PasswordHasher.iterationsOf(encoded));
    }

    @Test
    void rejectsIterationCountsOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordHasher(PasswordHasher.MIN_ITERATIONS - 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PasswordHasher(PasswordHasher.MAX_ITERATIONS + 1));
    }

    @Test
    void handlesNonAsciiPasswords() {
        String hash = hasher.hash(pw("pässwörd€"));

        assertTrue(hasher.verify(pw("pässwörd€"), hash));
        assertFalse(hasher.verify(pw("passwordE"), hash));
    }
}
