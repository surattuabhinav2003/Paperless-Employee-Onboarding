package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * The candidate's answers, keyed by the field's index in the offer's field
 * list. A signature field's value is a PNG/JPG data URI; every other field's
 * value is plain text.
 */
public record SignOfferRequest(
        @NotEmpty(message = "Complete every field before submitting")
        Map<Integer, String> fieldValues
) {
}
