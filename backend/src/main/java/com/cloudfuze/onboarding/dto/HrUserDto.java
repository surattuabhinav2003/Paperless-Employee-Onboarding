package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.HrUser;

import java.util.UUID;

public record HrUserDto(UUID id, String email, String fullName, String jobTitle) {

    public static HrUserDto from(HrUser user) {
        return new HrUserDto(user.getId(), user.getEmail(), user.getFullName(), user.getJobTitle());
    }
}
