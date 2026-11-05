package com.musiclibrary.repository;

import java.io.Serial;

/**
 * Unchecked exception thrown by repository operations when a data access error occurs.
 */
public class RepositoryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
