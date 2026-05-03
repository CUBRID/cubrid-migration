package com.cmt.e2e.framework.db.init;

/**
 * Unchecked exception thrown when database initialization fails
 * during Flyway migrate or clean operations.
 */
public class DatabaseInitializationException extends RuntimeException {

    public DatabaseInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
