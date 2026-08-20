package com.cloudfuze.onboarding.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** The single error shape every API returns, success or failure. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors,
        Map<String, Object> details
) {
    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, Map.of(), Map.of());
    }

    public static ErrorResponse of(int status, String code, String message, String path,
                                   Map<String, Object> details) {
        return new ErrorResponse(Instant.now(), status, code, message, path, Map.of(), details);
    }

    public static ErrorResponse validation(int status, String message, String path,
                                           Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, "VALIDATION_FAILED", message, path, fieldErrors, Map.of());
    }
}
