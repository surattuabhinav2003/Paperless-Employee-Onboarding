package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.Stage;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** What the candidate portal renders on every page: identity, stage, gates. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortalOverviewDto(
        String candidateName,
        String role,
        String department,
        Stage stage,
        String stageLabel,
        String headline,
        String message,
        List<PortalStepDto> steps,
        /** documents | offer | bond - the only page the candidate should be on. */
        String currentStep,
        DocumentProgressDto documents,
        boolean profileSubmitted,
        boolean profileEditable,
        boolean readyToSubmit,
        boolean submittedForReview,
        Instant submittedForReviewAt,
        List<String> outstandingItems,
        boolean documentsUploadAllowed,
        boolean offerAvailable,
        boolean bondAvailable,
        boolean onboardingComplete,
        Instant linkExpiresAt,
        Instant completedAt,
        String supportContact
) {
}
