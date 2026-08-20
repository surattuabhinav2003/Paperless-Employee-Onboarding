package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Everything HR needs on one candidate: pipeline row, documents, offer, bond, audit. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateDetailDto(
        CandidateSummaryDto candidate,
        CandidateProfileDto profile,
        List<RequiredDocumentDto> requiredDocuments,
        List<DocumentDto> documents,
        OfferDto offer,
        BondDto bond,
        List<AuditLogDto> auditTrail
) {
}
