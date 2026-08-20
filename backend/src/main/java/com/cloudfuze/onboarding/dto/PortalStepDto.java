package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One node of the candidate progress tracker. {@code state} is computed on the
 * server so the portal UI cannot invent an unlocked step.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortalStepDto(
        String key,
        String title,
        String state,
        String statusText,
        String lockReason
) {
    public static final String COMPLETED = "completed";
    public static final String CURRENT = "current";
    public static final String LOCKED = "locked";
}
