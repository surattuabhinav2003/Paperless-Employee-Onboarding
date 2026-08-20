package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

/** Raised for unknown, malformed or revoked candidate portal tokens. */
public class InvalidPortalTokenException extends ApiException {

    public InvalidPortalTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_TOKEN",
                "This onboarding link is not valid. Please use the link from your invitation email or contact HR.");
    }
}
