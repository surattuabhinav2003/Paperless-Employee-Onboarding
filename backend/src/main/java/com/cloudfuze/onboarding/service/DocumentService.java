package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.DocumentDto;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.FileValidationException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateDocumentRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.storage.FileStorageService;
import com.cloudfuze.onboarding.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Document submission and review.
 * <p>
 * Rules enforced here: a candidate may only upload documents HR requested, only
 * while the stage is {@code docs_pending}, and only for a slot that is empty or
 * rejected. A rejected document reopens for re-upload by the candidate; HR can
 * also reopen a verified document for its own re-review. Verifying every
 * mandatory document only makes a candidate eligible for approval - see
 * {@link DocumentApprovalService} for the actual, deliberate approval step.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final DocumentApprovalService approvalService;
    private final FileStorageService storageService;
    private final FileUploadValidator uploadValidator;
    private final StageGuard stageGuard;
    private final AuditService auditService;
    private final OnboardingMapper mapper;

    private final DocumentCatalogService catalog;

    public DocumentService(CandidateRepository candidateRepository,
                          CandidateDocumentRepository documentRepository,
                          DocumentApprovalService approvalService,
                          FileStorageService storageService,
                          FileUploadValidator uploadValidator,
                          StageGuard stageGuard,
                          AuditService auditService,
                          OnboardingMapper mapper,
                          DocumentCatalogService catalog) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.approvalService = approvalService;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.stageGuard = stageGuard;
        this.auditService = auditService;
        this.mapper = mapper;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<CandidateDocument> documentsOf(UUID candidateId) {
        return documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId);
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> hrDocumentView(Candidate candidate) {
        return mapper.toDocumentDtos(candidate, documentsOf(candidate.getId()), mapper::hrDocumentUrl);
    }

    /** Same view, loading the candidate fresh - used right after a review decision. */
    @Transactional(readOnly = true)
    public List<DocumentDto> hrDocumentViewFor(UUID candidateId) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> ResourceNotFoundException.candidate(candidateId));
        return hrDocumentView(candidate);
    }

    /**
     * A fresh record for this type. A built-in one is stored under its enum; an
     * admin-created one under OTHER plus its code, because the enum column is
     * NOT NULL and has no constant to hold a runtime type.
     */
    private CandidateDocument newDocument(Candidate candidate, DocumentCatalogService.Entry type) {
        return type.custom()
                ? new CandidateDocument(candidate, type.code())
                : new CandidateDocument(candidate, type.builtIn());
    }

    /** Candidate upload / re-upload of a single requested document. */
    @Transactional
    public CandidateDocument upload(Candidate candidate, String typeCode, EducationCourse course,
                                    MultipartFile file, String ipAddress, String userAgent) {
        // Matched on the requirement's code, so a type an admin created behaves
        // exactly like a built-in one here.
        RequiredDocument requirement = candidate.getRequiredDocuments().stream()
                .filter(rd -> rd.typeCode().equals(typeCode))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("DOCUMENT_NOT_REQUESTED",
                        "That document was not requested for your onboarding."));

        DocumentCatalogService.Entry type = catalog.resolve(typeCode);

        stageGuard.requireDocumentUploadOpen(candidate);

        CandidateDocument existing = documentRepository
                .findByCandidateIdAndTypeCode(candidate.getId(), typeCode)
                .orElse(null);

        // Once submitted, only a document HR sent back (or one added as a new
        // requirement after submission, so it was never provided at all) may
        // still be uploaded.
        boolean replacingRejected = existing != null && existing.getStatus() == DocumentStatus.REJECTED;
        if (candidate.isSubmittedForReview() && existing != null && !replacingRejected) {
            throw new BusinessRuleException("ALREADY_SUBMITTED",
                    "Your onboarding pack is already with HR. You can only replace a document they ask "
                            + "you to re-upload.");
        }
        uploadValidator.validate(file);

        // Education certificates above Class 10 must say which course they are for.
        if (type.requiresCourse()) {
            if (course == null) {
                throw new FileValidationException("COURSE_REQUIRED",
                        "Select which course this certificate is for before uploading it.");
            }
            if (!type.builtIn().accepts(course)) {
                throw new FileValidationException("COURSE_NOT_ALLOWED",
                        "That course cannot be used for " + type.label() + ".");
            }
        } else if (course != null) {
            throw new FileValidationException("COURSE_NOT_APPLICABLE",
                    type.label() + " does not take a course selection.");
        }

        boolean isReupload = false;
        if (existing != null) {
            switch (existing.getStatus()) {
                case VERIFIED -> throw new BusinessRuleException("DOCUMENT_ALREADY_VERIFIED",
                        "This document has already been verified by HR and cannot be replaced.");
                case SUBMITTED -> {
                    // Reaching here means the pack is not yet with HR (the guard above
                    // blocks that case), so the candidate may still fix a mistake by
                    // replacing what they uploaded.
                    if (candidate.isSubmittedForReview()) {
                        throw new BusinessRuleException("DOCUMENT_AWAITING_REVIEW",
                                "This document is already submitted and waiting for HR review.");
                    }
                    isReupload = true;
                }
                case REJECTED -> isReupload = true;
                default -> {
                    // PENDING is never persisted; nothing to do.
                }
            }
        }

        StoredFile stored = store(candidate.getId(), file);

        CandidateDocument document = existing == null ? newDocument(candidate, type) : existing;
        String replacedKey = isReupload ? document.getStorageKey() : null;
        document.setStorageKey(stored.key());
        document.setOriginalFilename(stored.originalFilename());
        document.setContentType(file.getContentType());
        document.setSizeBytes(stored.sizeBytes());
        document.setEducationCourse(course);
        document.setStatus(DocumentStatus.SUBMITTED);
        document.setUploadedAt(Instant.now());
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        document.setRejectReason(null);
        if (isReupload) {
            document.setVersion(document.getVersion() + 1);
        }
        documentRepository.save(document);

        if (replacedKey != null && !replacedKey.equals(stored.key())) {
            // The rejected file is superseded; its hash stays in the audit trail.
            safeDelete(replacedKey);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("documentType", type.code());
        metadata.put("filename", stored.originalFilename());
        metadata.put("sizeBytes", stored.sizeBytes());
        metadata.put("sha256", stored.sha256());
        metadata.put("version", document.getVersion());
        metadata.put("mandatory", requirement.isMandatory());
        if (course != null) {
            metadata.put("course", course.getCode());
        }
        if (userAgent != null) {
            metadata.put("userAgent", userAgent);
        }
        if (isReupload) {
            metadata.put("replacedRejectedUpload", true);
        }

        auditService.recordCandidateEvent(candidate.getId(),
                isReupload ? AuditEventType.DOCUMENT_REUPLOADED : AuditEventType.DOCUMENT_UPLOADED,
                candidate.getEmail(), ipAddress, type.code(), metadata);

        log.info("Candidate {} uploaded {} (v{})", candidate.getEmail(), type.code(), document.getVersion());
        return document;
    }

    /** HR verifies a single document; may complete the document stage. Returns the candidate id. */
    @Transactional
    public UUID verify(UUID documentId, HrPrincipal hrUser, String ipAddress) {
        CandidateDocument document = requireDocument(documentId);
        Candidate candidate = document.getCandidate();
        stageGuard.requireDocumentReviewOpen(candidate);

        if (document.getStatus() != DocumentStatus.SUBMITTED) {
            throw new BusinessRuleException("DOCUMENT_NOT_REVIEWABLE",
                    "Only submitted documents can be verified. This one is currently "
                            + document.getStatus().getCode() + ".");
        }

        document.setStatus(DocumentStatus.VERIFIED);
        document.setReviewedBy(hrUser.getEmail());
        document.setReviewedAt(Instant.now());
        document.setRejectReason(null);
        documentRepository.save(document);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.DOCUMENT_VERIFIED, hrUser.getEmail(),
                ipAddress, document.typeCode(), Map.of(
                        "documentType", document.typeCode(),
                        "filename", document.getOriginalFilename(),
                        "version", document.getVersion()));

        return candidate.getId();
    }

    /**
     * HR re-opens a verified document for another look, undoing a mistaken
     * verify. Only possible before final approval - once HR approves the
     * candidate, review closes for good.
     */
    @Transactional
    public UUID reopen(UUID documentId, HrPrincipal hrUser, String ipAddress) {
        CandidateDocument document = requireDocument(documentId);
        Candidate candidate = document.getCandidate();
        stageGuard.requireDocumentReviewOpen(candidate);

        if (document.getStatus() != DocumentStatus.VERIFIED) {
            throw new BusinessRuleException("DOCUMENT_NOT_VERIFIED",
                    "Only a verified document can be reopened for re-review. This one is currently "
                            + document.getStatus().getCode() + ".");
        }

        document.setStatus(DocumentStatus.SUBMITTED);
        document.setReviewedBy(null);
        document.setReviewedAt(null);
        documentRepository.save(document);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.DOCUMENT_REOPENED, hrUser.getEmail(),
                ipAddress, document.typeCode(), Map.of(
                        "documentType", document.typeCode(),
                        "filename", document.getOriginalFilename(),
                        "version", document.getVersion()));

        log.info("HR {} reopened {} for candidate {} for re-review", hrUser.getEmail(),
                document.typeCode(), candidate.getEmail());
        return candidate.getId();
    }

    /** HR rejects a single document. Only that document reopens for re-upload. */
    @Transactional
    public UUID reject(UUID documentId, String reason, HrPrincipal hrUser, String ipAddress) {
        CandidateDocument document = requireDocument(documentId);
        Candidate candidate = document.getCandidate();
        stageGuard.requireDocumentReviewOpen(candidate);

        if (document.getStatus() != DocumentStatus.SUBMITTED) {
            throw new BusinessRuleException("DOCUMENT_NOT_REVIEWABLE",
                    "Only submitted documents can be rejected. This one is currently "
                            + document.getStatus().getCode() + ".");
        }

        document.setStatus(DocumentStatus.REJECTED);
        document.setReviewedBy(hrUser.getEmail());
        document.setReviewedAt(Instant.now());
        document.setRejectReason(reason.trim());
        documentRepository.save(document);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.DOCUMENT_REJECTED, hrUser.getEmail(),
                ipAddress, document.typeCode(), Map.of(
                        "documentType", document.typeCode(),
                        "reason", reason.trim(),
                        "version", document.getVersion()));

        log.info("HR {} rejected {} for candidate {}", hrUser.getEmail(), document.typeCode(),
                candidate.getEmail());
        return candidate.getId();
    }

    public CandidateDocument requireDocument(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> ResourceNotFoundException.document(documentId));
    }

    /** Loads a document for streaming, verifying it belongs to the given candidate. */
    @Transactional(readOnly = true)
    public CandidateDocument requireDocumentOfCandidate(UUID documentId, UUID candidateId) {
        CandidateDocument document = requireDocument(documentId);
        if (!document.getCandidate().getId().equals(candidateId)) {
            // Do not reveal that the document exists for someone else.
            throw ResourceNotFoundException.document(documentId);
        }
        return document;
    }

    private StoredFile store(UUID candidateId, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return storageService.store("candidates/" + candidateId + "/documents",
                    uploadValidator.safeFilename(file), file.getContentType(), in, file.getSize());
        } catch (IOException e) {
            throw new com.cloudfuze.onboarding.exception.StorageException("Could not read the uploaded file", e);
        }
    }

    private void safeDelete(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete superseded object {}: {}", key, e.getMessage());
        }
    }
}
