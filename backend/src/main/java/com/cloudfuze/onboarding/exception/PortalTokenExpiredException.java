package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public class PortalTokenExpiredException extends ApiException {

    public PortalTokenExpiredException(Instant expiredAt) {
        super(HttpStatus.UNAUTHORIZED, "PORTAL_TOKEN_EXPIRED",
                "This onboarding link has expired. Please ask HR to send you a new invitation link.",
                Map.of("expiredAt", expiredAt.toString()));
    }
}
