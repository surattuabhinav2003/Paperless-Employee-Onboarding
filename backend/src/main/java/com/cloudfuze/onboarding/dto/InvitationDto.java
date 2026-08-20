package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Returned to HR when a portal link is created or regenerated. The raw token is
 * surfaced exactly once, at generation time, so HR can copy the link; it is never
 * persisted and never returned again.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationDto(
        String portalUrl,
        Instant expiresAt,
        Instant sentAt,
        int invitationCount,
        String emailedTo,
        String emailProvider
) {
}
