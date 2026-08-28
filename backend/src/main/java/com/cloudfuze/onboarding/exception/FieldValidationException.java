package com.cloudfuze.onboarding.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Validation that Bean Validation annotations cannot express, because the rule
 * is decided at runtime - currently which candidate detail fields are required.
 *
 * <p>Carries per-field messages so the response looks identical to an
 * annotation failure and the form can highlight the same inputs.
 */
@Getter
public class FieldValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public FieldValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null ? Map.of() : fieldErrors;
    }
}
