package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.OfferStatus;
import com.cloudfuze.onboarding.model.Stage;

import java.time.Instant;
import java.util.UUID;

/** One row of the HR pipeline table. */
public record CandidateSummaryDto(
        UUID id,
        String name,
        String email,
        String role,
        String department,
        Stage stage,
        String stageLabel,
        boolean readyForApproval,
        int documentsRequired,
        int documentsVerified,
        int documentsSubmitted,
        int documentsRejected,
        int documentsMissing,
        OfferStatus offerStatus,
        boolean offerPrepared,
        Instant submittedForReviewAt,
        Instant invitationSentAt,
        Instant tokenExpiresAt,
        boolean portalLinkActive,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
}
