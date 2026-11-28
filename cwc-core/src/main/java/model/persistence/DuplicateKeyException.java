package model.persistence;

/**
 * Thrown when storing something whose key is already in use, e.g. registering an
 * account id that is taken (ignoring case).
 */
public class DuplicateKeyException extends DataAccessException {

    public DuplicateKeyException(String message) {
        super(message);
    }
}
