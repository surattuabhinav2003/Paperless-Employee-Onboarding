package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.HrRole;
import jakarta.validation.constraints.NotNull;

/** Grants or revokes admin for one HR user. */
public record UpdateHrRoleRequest(
        @NotNull(message = "Role is required")
        HrRole role
) {
}
