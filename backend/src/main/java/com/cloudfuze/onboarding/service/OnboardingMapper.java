package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.CandidateSummaryDto;
import com.cloudfuze.onboarding.dto.DocumentDto;
import com.cloudfuze.onboarding.dto.DocumentProgressDto;
import com.cloudfuze.onboarding.dto.OfferDto;
import com.cloudfuze.onboarding.dto.RequiredDocumentDto;
import com.cloudfuze.onboarding.dto.OfferFieldDto;
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

    private final DocumentCatalogService catalog;

    public OnboardingMapper(DocumentCatalogService catalog) {
        this.catalog = catalog;
    }

    /**
     * Builds the document list for a candidate: one entry per requested document,
     * merged with whatever has been uploaded.
     *
     * @param urlBuilder produces the caller-appropriate download path for a document
     */
    public List<DocumentDto> toDocumentDtos(Candidate candidate, List<CandidateDocument> uploaded,
                                            Function<CandidateDocument, String> urlBuilder) {
        Map<String, CandidateDocument> byType = new LinkedHashMap<>();
        uploaded.forEach(doc -> byType.put(doc.typeCode(), doc));

        boolean uploadWindowOpen = candidate.getStage() == Stage.DOCS_PENDING;
        // Before the pack is handed to HR the candidate may still fix any mistake;
        // after it, only a document HR sent back reopens.
        boolean packOpen = uploadWindowOpen && !candidate.isSubmittedForReview();
        List<DocumentDto> result = new ArrayList<>();

        // Always presented in the canonical sequence - Class 10, secondary,
        // higher education, then identity - regardless of the order HR ticked them.
        for (RequiredDocument required : orderedRequirements(candidate)) {
            CandidateDocument doc = byType.remove(required.typeCode());
            result.add(toDocumentDto(catalog.resolve(required.typeCode()), required.displayName(),
                    required.isMandatory(), doc, uploadWindowOpen, packOpen, urlBuilder));
        }
        // Anything uploaded for a type HR later removed from the requirement list.
        byType.forEach((code, doc) -> {
            DocumentCatalogService.Entry entry = catalog.resolve(code);
            result.add(toDocumentDto(entry, entry.label(), false, doc, false, false, urlBuilder));
        });
        return result;
    }

    private DocumentDto toDocumentDto(DocumentCatalogService.Entry type, String label, boolean mandatory,
                                      CandidateDocument doc, boolean uploadWindowOpen, boolean packOpen,
                                      Function<CandidateDocument, String> urlBuilder) {
        List<DocumentDto.CourseOption> courseOptions = type.courseOptions().stream()
                .map(DocumentDto.CourseOption::from)
                .toList();
        if (doc == null) {
            // Nothing has ever been provided for this slot, so there is nothing the
            // "pack already submitted" protection needs to guard - this covers a
            // requirement HR adds after the candidate has already submitted.
            return new DocumentDto(null, type.code(), label, mandatory, DocumentStatus.PENDING,
                    DocumentStatus.PENDING.getLabel(), type.requiresCourse(), courseOptions, null, null,
                    null, null, null, null, null, null, null, uploadWindowOpen, null);
        }
        // Rejected reopens even after submitting; an already-uploaded document can
        // be replaced only while the pack is still the candidate's to edit.
        boolean canUpload = switch (doc.getStatus()) {
            case REJECTED -> uploadWindowOpen;
            case SUBMITTED -> packOpen;
            default -> false;
        };
        EducationCourse course = doc.getEducationCourse();
        return new DocumentDto(doc.getId(), type.code(), label, mandatory, doc.getStatus(), doc.getStatus().getLabel(),
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
                .sorted(Comparator.comparingInt(rd -> catalog.resolve(rd.typeCode()).sortOrder()))
                .toList();
    }

    public DocumentProgressDto progress(Candidate candidate, List<CandidateDocument> uploaded) {
        Map<String, CandidateDocument> byType = new LinkedHashMap<>();
        uploaded.forEach(doc -> byType.put(doc.typeCode(), doc));

        int required = candidate.getRequiredDocuments().size();
        int verified = 0;
        int submitted = 0;
        int rejected = 0;
        int missing = 0;

        for (RequiredDocument requirement : orderedRequirements(candidate)) {
            CandidateDocument doc = byType.get(requirement.typeCode());
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
                                         Offer offer, boolean readyForApproval) {
        DocumentProgressDto progress = progress(candidate, documents);
        return new CandidateSummaryDto(
                candidate.getId(),
                candidate.getName(),
                candidate.getEmail(),
                candidate.getRole(),
                candidate.getDepartment(),
                candidate.getStage(),
                candidate.getStage().getLabel(),
                readyForApproval,
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
                offer.getUploadedBy(), offer.getNotes(), downloadUrl, toOfferFieldDtos(offer),
                offer.getSignedStorageKey() != null);
    }

    public List<OfferFieldDto> toOfferFieldDtos(Offer offer) {
        return offer.getFields().stream()
                .map(f -> new OfferFieldDto(f.getType(), f.getPage(), f.getXPct(), f.getYPct(), f.getWidthPct(),
                        f.getHeightPct(), f.getPrefill(), f.getTextColor(), f.getTextFont()))
                .toList();
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
