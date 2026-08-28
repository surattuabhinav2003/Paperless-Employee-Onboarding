package com.cloudfuze.onboarding.dto;

/** One candidate detail field and whether it is asked for. */
public record CandidateFieldDto(
        String code,
        String label,
        String group,
        String groupLabel,
        boolean enabled,
        boolean required
) {
}
