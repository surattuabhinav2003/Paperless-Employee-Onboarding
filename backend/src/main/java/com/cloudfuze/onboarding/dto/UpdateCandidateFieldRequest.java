package com.cloudfuze.onboarding.dto;

/** Whether a candidate detail field is asked for, and whether it is required. */
public record UpdateCandidateFieldRequest(
        boolean enabled,
        boolean required
) {
}
