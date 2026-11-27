package model.persistence;

/**
 * Thrown when data cannot be read from or written to storage. Messages never
 * contain passwords or password hashes.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
