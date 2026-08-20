package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateDocumentRepository;
import com.cloudfuze.onboarding.repository.CandidateProfileRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single place the document gate opens.
 * <p>
 * The onboarding checklist is documents <em>and</em> personal details, so the
 * candidate reaches {@code docs_approved} only when every mandatory document is
 * verified <em>and</em> their details are submitted. Either of those can happen
 * last, so both {@link DocumentService} and {@link CandidateProfileService}
 * re-evaluate through here - otherwise a candidate who submits details after the
 * final verification would never advance.
 */
@Service
public class DocumentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApprovalService.class);

    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final CandidateProfileRepository profileRepository;
    private final AuditService auditService;

    public DocumentApprovalService(CandidateRepository candidateRepository,
                                   CandidateDocumentRepository documentRepository,
                                   CandidateProfileRepository profileRepository,
                                   AuditService auditService) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.profileRepository = profileRepository;
        this.auditService = auditService;
    }

    /**
     * Advances the candidate to {@code docs_approved} when every requirement is
     * met. Safe to call after any document or profile change.
     *
     * @return true when this call advanced the stage
     */
    @Transactional
    public boolean evaluate(Candidate candidate) {
        if (candidate.getStage() != Stage.DOCS_PENDING) {
            return false;
        }
        List<RequiredDocument> mandatory = candidate.mandatoryDocuments();
        if (mandatory.isEmpty()) {
            return false;
        }

        Map<DocumentType, DocumentStatus> statuses = new LinkedHashMap<>();
        documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidate.getId())
                .forEach(doc -> statuses.put(doc.getDocumentType(), doc.getStatus()));

        boolean allVerified = mandatory.stream()
                .allMatch(rd -> statuses.get(rd.getDocumentType()) == DocumentStatus.VERIFIED);
        if (!allVerified) {
            return false;
        }
        if (!profileRepository.existsByCandidateId(candidate.getId())) {
            log.info("Candidate {} has every document verified but has not submitted their details yet",
                    candidate.getEmail());
            return false;
        }
        if (!candidate.isSubmittedForReview()) {
            log.info("Candidate {} has not submitted their onboarding package for review yet",
                    candidate.getEmail());
            return false;
        }

        candidate.setStage(Stage.DOCS_APPROVED);
        candidate.setDocsApprovedAt(Instant.now());
        candidateRepository.save(candidate);

        auditService.recordSystemEvent(candidate.getId(), AuditEventType.DOCUMENTS_APPROVED, Map.of(
                "mandatoryDocuments", mandatory.stream().map(rd -> rd.getDocumentType().getCode()).toList(),
                "detailsSubmitted", true,
                "submittedForReviewAt", String.valueOf(candidate.getSubmittedForReviewAt()),
                "unlocked", "offer"));
        log.info("Candidate {} moved to docs_approved - offer stage unlocked", candidate.getEmail());
        return true;
    }
}
