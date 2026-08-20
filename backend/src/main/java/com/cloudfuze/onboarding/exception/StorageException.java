package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILURE", message);
        if (cause != null) {
            initCause(cause);
        }
    }

    public StorageException(String message) {
        this(message, null);
    }
}
