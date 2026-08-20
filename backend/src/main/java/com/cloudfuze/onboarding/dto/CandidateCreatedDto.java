package com.cloudfuze.onboarding.dto;

/** Response of candidate creation: the pipeline row plus the freshly issued link. */
public record CandidateCreatedDto(
        CandidateSummaryDto candidate,
        InvitationDto invitation
) {
}
