package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

/** SignatureOne could not complete the requested operation. */
public class SignatureProviderException extends ApiException {

    public SignatureProviderException(String message) {
        super(HttpStatus.BAD_GATEWAY, "SIGNATURE_PROVIDER_FAILURE", message);
    }

    public SignatureProviderException(String message, Throwable cause) {
        this(message);
        if (cause != null) {
            initCause(cause);
        }
    }
}
