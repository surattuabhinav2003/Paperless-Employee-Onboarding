package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.HrUser;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/** One HR user, as the admin screen lists them. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HrUserDto(
        UUID id,
        String email,
        String fullName,
        String jobTitle,
        HrRole role,
        String roleLabel,
        boolean active,
        Instant createdAt,
        /** Null until they first sign in - the admin screen shows that as pending. */
        Instant lastLoginAt
) {
    public static HrUserDto from(HrUser user) {
        HrRole role = user.getRole() == null ? HrRole.HR : user.getRole();
        return new HrUserDto(user.getId(), user.getEmail(), user.getFullName(), user.getJobTitle(),
                role, role.getLabel(), user.isActive(), user.getCreatedAt(), user.getLastLoginAt());
    }
}
