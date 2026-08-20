package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.OfferStatus;

import java.time.Instant;
import java.util.UUID;

public record OfferDto(
        UUID id,
        OfferStatus status,
        String filename,
        long sizeBytes,
        Instant sentAt,
        Instant viewedAt,
        Instant acceptedAt,
        String acceptedByName,
        String uploadedBy,
        String notes,
        String downloadUrl
) {
}
