package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.OfferStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortalOfferDto(
        boolean prepared,
        OfferStatus status,
        String filename,
        Instant sentAt,
        Instant viewedAt,
        Instant acceptedAt,
        String downloadUrl,
        boolean canAccept,
        String message,
        String candidateName,
        String role,
        String department
) {
}
