package com.cloudfuze.onboarding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The candidate facts HR owns. Email is deliberately absent: it is the address
 * the invitation went to and the identity the portal link is bound to, so
 * changing it would silently orphan a live link.
 */
public record UpdateCandidateRequest(
        @NotBlank(message = "Candidate name is required")
        @Size(max = 160, message = "Name must be 160 characters or fewer")
        String name,

        @NotBlank(message = "Role is required")
        @Size(max = 120, message = "Role must be 120 characters or fewer")
        String role,

        @NotBlank(message = "Department is required")
        @Size(max = 120, message = "Department must be 120 characters or fewer")
        String department
) {
}
