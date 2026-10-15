package com.musiclibrary.repository;

/**
 * Unchecked exception thrown by repository operations when a data access error occurs.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
