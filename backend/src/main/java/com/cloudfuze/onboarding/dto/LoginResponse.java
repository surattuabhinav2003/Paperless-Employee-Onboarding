package com.cloudfuze.onboarding.dto;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        HrUserDto user
) {
}
