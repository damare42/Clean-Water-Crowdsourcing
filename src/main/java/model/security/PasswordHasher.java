package model.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.OptionalInt;

/**
 * Hashes and verifies passwords with PBKDF2-HMAC-SHA256 (built into the JDK).
 * <p>
 * Every hash gets its own random {@value #SALT_BYTES}-byte salt. The result is one
 * self-describing string, {@code pbkdf2-sha256$<iterations>$<salt>$<hash>} (salt
 * and hash in Base64), so the iteration count can be raised later without
 * invalidating existing hashes: {@link #verify} always uses the stored count and
 * {@link #needsRehash} says when a stored hash is weaker than the current setting.
 * Hashes are compared in constant time. Passwords are taken as {@code char[]} so
 * callers can clear them after use; this class never logs or returns them.
 */
public final class PasswordHasher {

    /** Identifies the algorithm in the encoded hash. */
    public static final String ALGORITHM_ID = "pbkdf2-sha256";
    /** Iteration count used for new hashes by default (OWASP 2023 recommendation). */
    public static final int DEFAULT_ITERATIONS = 210_000;
    /** Lowest iteration count this class accepts, for new and for stored hashes. */
    public static final int MIN_ITERATIONS = 1_000;
    /**
     * Highest iteration count accepted, for new and for stored hashes, so a corrupt or
     * tampered hash cannot stall a login for long (about ten times the default).
     */
    public static final int MAX_ITERATIONS = 2_000_000;
    /** Length of the random salt in bytes. */
    public static final int SALT_BYTES = 16;
    /** Length of the derived key in bytes. */
    public static final int HASH_BYTES = 32;

    private static final String JCA_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String SEPARATOR = "$";
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    /**
     * Creates a hasher using {@link #DEFAULT_ITERATIONS}.
     */
    public PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    /**
     * @param iterations the iteration count for new hashes, between {@link #MIN_ITERATIONS}
     *                   and {@link #MAX_ITERATIONS}
     */
    public PasswordHasher(int iterations) {
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Iterations must be between " + MIN_ITERATIONS
                    + " and " + MAX_ITERATIONS + ": " + iterations);
        }
        this.iterations = iterations;
    }

    /**
     * @return the iteration count used for new hashes
     */
    public int getIterations() {
        return iterations;
    }

    /**
     * Hashes a password with a new random salt.
     *
     * @param password the password; not modified, the caller should clear it after use
     * @return the encoded hash
     * @throws IllegalArgumentException if the password is null or empty
     */
    public String hash(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, iterations, HASH_BYTES);
        return ALGORITHM_ID + SEPARATOR + iterations + SEPARATOR + ENCODER.encodeToString(salt)
                + SEPARATOR + ENCODER.encodeToString(hash);
    }

    /**
     * Checks a password against an encoded hash, using the salt and iteration count
     * stored in the hash.
     *
     * @param password the password to check; not modified
     * @param encoded a hash produced by {@link #hash}
     * @return true if the password matches; false if it does not, if the password is
     *         null or empty, or if the encoded hash is malformed
     */
    public boolean verify(char[] password, String encoded) {
        if (password == null || password.length == 0) {
            return false;
        }
        Parsed parsed = parse(encoded);
        if (parsed == null) {
            return false;
        }
        byte[] actual = pbkdf2(password, parsed.salt, parsed.iterations, parsed.hash.length);
        return MessageDigest.isEqual(actual, parsed.hash);
    }

    /**
     * @param encoded a stored hash
     * @return true if the hash is malformed or uses fewer iterations than this
     *         hasher, so it should be replaced after the next successful login
     */
    public boolean needsRehash(String encoded) {
        Parsed parsed = parse(encoded);
        return parsed == null || parsed.iterations < iterations;
    }

    /**
     * @param encoded a stored hash
     * @return the iteration count stored in the hash, or empty if it is malformed
     */
    public static OptionalInt iterationsOf(String encoded) {
        Parsed parsed = parse(encoded);
        return parsed == null ? OptionalInt.empty() : OptionalInt.of(parsed.iterations);
    }

    private record Parsed(int iterations, byte[] salt, byte[] hash) {
    }

    private static Parsed parse(String encoded) {
        if (encoded == null) {
            return null;
        }
        String[] parts = encoded.split("\\$", -1);
        if (parts.length != 4 || !ALGORITHM_ID.equals(parts[0])) {
            return null;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = DECODER.decode(parts[2]);
            byte[] hash = DECODER.decode(parts[3]);
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS
                    || salt.length < SALT_BYTES || hash.length < 16 || hash.length > 64) {
                return null;
            }
            return new Parsed(iterations, salt, hash);
        } catch (IllegalArgumentException e) {
            // NumberFormatException or invalid Base64
            return null;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int lengthBytes) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, lengthBytes * 8);
        try {
            return SecretKeyFactory.getInstance(JCA_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(JCA_ALGORITHM + " is not available", e);
        } finally {
            spec.clearPassword();
        }
    }
}
