package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
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
 * Verifying every mandatory document only makes a candidate <em>eligible</em>
 * for approval - it does not approve them. Individually verifying documents
 * (or un-verifying one to fix a mistake) stays completely reversible right up
 * until HR deliberately clicks approve, which is the one moment that locks
 * review, advances the stage, and unlocks the offer letter.
 */
@Service
public class DocumentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DocumentApprovalService.class);

    private final CandidateRepository candidateRepository;
    private final AuditService auditService;

    public DocumentApprovalService(CandidateRepository candidateRepository, AuditService auditService) {
        this.candidateRepository = candidateRepository;
        this.auditService = auditService;
    }

    /**
     * Whether every mandatory document is verified and the candidate's pack has
     * been submitted - the precondition for HR to approve them. Read-only; never
     * changes the stage itself.
     */
    public boolean isReadyForApproval(Candidate candidate, List<CandidateDocument> documents) {
        if (candidate.getStage() != Stage.DOCS_PENDING) {
            return false;
        }
        List<RequiredDocument> mandatory = candidate.mandatoryDocuments();
        if (mandatory.isEmpty() || !candidate.isSubmittedForReview()) {
            return false;
        }

        Map<DocumentType, DocumentStatus> statuses = new LinkedHashMap<>();
        documents.forEach(doc -> statuses.put(doc.getDocumentType(), doc.getStatus()));

        return mandatory.stream().allMatch(rd -> statuses.get(rd.getDocumentType()) == DocumentStatus.VERIFIED);
    }

    /**
     * HR's deliberate "approve" action: advances the candidate to
     * {@code docs_approved}, closing document review and unlocking the offer
     * stage. This is the only place that transition happens - it never occurs
     * automatically just because the last document was verified.
     */
    @Transactional
    public void approve(Candidate candidate, List<CandidateDocument> documents, HrPrincipal hrUser,
                        String ipAddress) {
        if (!isReadyForApproval(candidate, documents)) {
            throw new BusinessRuleException("NOT_READY_FOR_APPROVAL",
                    "Every mandatory document must be verified, and the candidate's pack submitted, "
                            + "before you can approve them.");
        }

        candidate.setStage(Stage.DOCS_APPROVED);
        candidate.setDocsApprovedAt(Instant.now());
        candidateRepository.save(candidate);

        List<RequiredDocument> mandatory = candidate.mandatoryDocuments();
        auditService.recordHrEvent(candidate.getId(), AuditEventType.DOCUMENTS_APPROVED, hrUser.getEmail(),
                ipAddress, null, Map.of(
                        "mandatoryDocuments", mandatory.stream().map(rd -> rd.getDocumentType().getCode()).toList(),
                        "unlocked", "offer"));
        log.info("HR {} approved candidate {} - offer stage unlocked", hrUser.getEmail(), candidate.getEmail());
    }
}
