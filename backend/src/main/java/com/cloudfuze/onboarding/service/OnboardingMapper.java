package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.CandidateSummaryDto;
import com.cloudfuze.onboarding.dto.DocumentDto;
import com.cloudfuze.onboarding.dto.DocumentProgressDto;
import com.cloudfuze.onboarding.dto.OfferDto;
import com.cloudfuze.onboarding.dto.RequiredDocumentDto;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Entity to DTO mapping. Download links are always built as API paths, so a
 * storage key never reaches a client.
 */
@Component
public class OnboardingMapper {

    /**
     * Builds the document list for a candidate: one entry per requested document,
     * merged with whatever has been uploaded.
     *
     * @param urlBuilder produces the caller-appropriate download path for a document
     */
    public List<DocumentDto> toDocumentDtos(Candidate candidate, List<CandidateDocument> uploaded,
                                            Function<CandidateDocument, String> urlBuilder) {
        Map<DocumentType, CandidateDocument> byType = new LinkedHashMap<>();
        uploaded.forEach(doc -> byType.put(doc.getDocumentType(), doc));

        boolean uploadWindowOpen = candidate.getStage() == Stage.DOCS_PENDING;
        List<DocumentDto> result = new ArrayList<>();

        // Always presented in the canonical sequence - Class 10, secondary,
        // higher education, then identity - regardless of the order HR ticked them.
        for (RequiredDocument required : orderedRequirements(candidate)) {
            CandidateDocument doc = byType.remove(required.getDocumentType());
            result.add(toDocumentDto(required.getDocumentType(), required.displayName(), required.isMandatory(),
                    doc, uploadWindowOpen, urlBuilder));
        }
        // Anything uploaded for a type HR later removed from the requirement list.
        byType.forEach((type, doc) -> result.add(
                toDocumentDto(type, type.getLabel(), false, doc, false, urlBuilder)));
        return result;
    }

    private DocumentDto toDocumentDto(DocumentType type, String label, boolean mandatory, CandidateDocument doc,
                                      boolean uploadWindowOpen, Function<CandidateDocument, String> urlBuilder) {
        List<DocumentDto.CourseOption> courseOptions = type.courseOptions().stream()
                .map(DocumentDto.CourseOption::from)
                .toList();
        if (doc == null) {
            return new DocumentDto(null, type, label, mandatory, DocumentStatus.PENDING,
                    DocumentStatus.PENDING.getLabel(), type.requiresCourse(), courseOptions, null, null,
                    null, null, null, null, null, null, null, uploadWindowOpen, null);
        }
        boolean canUpload = uploadWindowOpen && doc.getStatus() == DocumentStatus.REJECTED;
        EducationCourse course = doc.getEducationCourse();
        return new DocumentDto(doc.getId(), type, label, mandatory, doc.getStatus(), doc.getStatus().getLabel(),
                type.requiresCourse(), courseOptions, course, course == null ? null : course.getLabel(),
                doc.getOriginalFilename(), doc.getSizeBytes(), doc.getVersion(), doc.getUploadedAt(),
                doc.getReviewedBy(), doc.getReviewedAt(), doc.getRejectReason(), canUpload,
                urlBuilder == null ? null : urlBuilder.apply(doc));
    }

    public List<RequiredDocumentDto> toRequiredDocumentDtos(Candidate candidate) {
        return orderedRequirements(candidate).stream().map(RequiredDocumentDto::from).toList();
    }

    /** Requirements sorted by the declared document-type order. */
    private List<RequiredDocument> orderedRequirements(Candidate candidate) {
        return candidate.getRequiredDocuments().stream()
                .sorted(Comparator.comparingInt(rd -> rd.getDocumentType().ordinal()))
                .toList();
    }

    public DocumentProgressDto progress(Candidate candidate, List<CandidateDocument> uploaded) {
        Map<DocumentType, CandidateDocument> byType = new LinkedHashMap<>();
        uploaded.forEach(doc -> byType.put(doc.getDocumentType(), doc));

        int required = candidate.getRequiredDocuments().size();
        int verified = 0;
        int submitted = 0;
        int rejected = 0;
        int missing = 0;

        for (RequiredDocument requirement : orderedRequirements(candidate)) {
            CandidateDocument doc = byType.get(requirement.getDocumentType());
            if (doc == null) {
                missing++;
                continue;
            }
            switch (doc.getStatus()) {
                case VERIFIED -> verified++;
                case SUBMITTED -> submitted++;
                case REJECTED -> rejected++;
                default -> missing++;
            }
        }
        return new DocumentProgressDto(required, verified, submitted, rejected, missing);
    }

    public CandidateSummaryDto toSummary(Candidate candidate, List<CandidateDocument> documents,
                                         Offer offer) {
        DocumentProgressDto progress = progress(candidate, documents);
        return new CandidateSummaryDto(
                candidate.getId(),
                candidate.getName(),
                candidate.getEmail(),
                candidate.getRole(),
                candidate.getDepartment(),
                candidate.getStage(),
                candidate.getStage().getLabel(),
                progress.required(),
                progress.verified(),
                progress.submitted(),
                progress.rejected(),
                progress.missing(),
                offer == null ? null : offer.getStatus(),
                offer != null,
                candidate.getSubmittedForReviewAt(),
                candidate.getInvitationSentAt(),
                candidate.getTokenExpiresAt(),
                candidate.isTokenActive(Instant.now()),
                candidate.getCreatedAt(),
                candidate.getUpdatedAt(),
                candidate.getCompletedAt());
    }

    public OfferDto toOfferDto(Offer offer, String downloadUrl) {
        if (offer == null) {
            return null;
        }
        return new OfferDto(offer.getId(), offer.getStatus(), offer.getOriginalFilename(), offer.getSizeBytes(),
                offer.getSentAt(), offer.getViewedAt(), offer.getAcceptedAt(), offer.getAcceptedByName(),
                offer.getUploadedBy(), offer.getNotes(), downloadUrl);
    }

    /** HR-side download path for a candidate document. */
    public String hrDocumentUrl(CandidateDocument doc) {
        return "/api/hr/documents/" + doc.getId() + "/file";
    }

    /** Candidate-side download path, scoped to their portal token. */
    public String portalDocumentUrl(String token, CandidateDocument doc) {
        return "/api/portal/" + token + "/documents/" + doc.getId() + "/file";
    }
}
