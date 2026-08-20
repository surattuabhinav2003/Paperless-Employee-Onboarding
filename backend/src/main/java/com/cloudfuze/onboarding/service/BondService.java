package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.BondAuditEventDto;
import com.cloudfuze.onboarding.dto.BondDto;
import com.cloudfuze.onboarding.dto.PortalBondDto;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.exception.StorageException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Bond;
import com.cloudfuze.onboarding.model.BondAuditEvent;
import com.cloudfuze.onboarding.model.BondStatus;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.BondRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureAuditEntry;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureStatusResult;
import com.cloudfuze.onboarding.signature.SignatureModels.SignedDocumentPayload;
import com.cloudfuze.onboarding.signature.SignatureOneService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Bond persistence and workflow state. The provider conversation itself lives in
 * {@link BondSigningService}, which calls the transactional steps here so no
 * database transaction is held open across a network call to SignatureOne.
 */
@Service
public class BondService {

    private static final Logger log = LoggerFactory.getLogger(BondService.class);

    private final BondRepository bondRepository;
    private final CandidateRepository candidateRepository;
    private final FileStorageService storageService;
    private final FileUploadValidator uploadValidator;
    private final SignatureOneService signatureService;
    private final StageGuard stageGuard;
    private final AuditService auditService;
    private final OnboardingMapper mapper;

    public BondService(BondRepository bondRepository, CandidateRepository candidateRepository,
                       FileStorageService storageService, FileUploadValidator uploadValidator,
                       SignatureOneService signatureService, StageGuard stageGuard,
                       AuditService auditService, OnboardingMapper mapper) {
        this.bondRepository = bondRepository;
        this.candidateRepository = candidateRepository;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.signatureService = signatureService;
        this.stageGuard = stageGuard;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /** HR uploads (or replaces) the bond document and its version label. */
    @Transactional
    public BondDto upload(Candidate candidate, MultipartFile file, String documentVersion, HrPrincipal hrUser,
                          String ipAddress) {
        uploadValidator.validate(file);

        Bond bond = bondRepository.findByCandidateId(candidate.getId()).orElseGet(() -> new Bond(candidate));
        if (bond.getStatus() == BondStatus.SIGNED) {
            throw new BusinessRuleException("BOND_ALREADY_SIGNED",
                    "This bond has already been signed and cannot be replaced.");
        }

        String previousKey = bond.getStorageKey();
        StoredFile stored = store(candidate.getId(), file);
        bond.setStorageKey(stored.key());
        bond.setOriginalFilename(stored.originalFilename());
        bond.setContentType(file.getContentType());
        bond.setSizeBytes(stored.sizeBytes());
        bond.setDocumentVersion(documentVersion == null || documentVersion.isBlank()
                ? "v1.0" : documentVersion.trim());
        bond.setStatus(BondStatus.NOT_INITIATED);
        bond.setSignatureRequestId(null);
        bond.setSigningUrl(null);
        bond.setFailureReason(null);
        bond.setUploadedBy(hrUser.getEmail());
        bond.setUploadedAt(Instant.now());
        bond.addAuditEvent(new BondAuditEvent("bond_uploaded", hrUser.getEmail(), Instant.now(), ipAddress,
                "Bond document " + bond.getDocumentVersion() + " uploaded by HR"));
        bondRepository.save(bond);

        if (previousKey != null && !previousKey.equals(stored.key())) {
            safeDelete(previousKey);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filename", stored.originalFilename());
        metadata.put("documentVersion", bond.getDocumentVersion());
        metadata.put("sha256", stored.sha256());
        metadata.put("candidateStage", candidate.getStage().getCode());
        auditService.recordHrEvent(candidate.getId(), AuditEventType.BOND_UPLOADED, hrUser.getEmail(), ipAddress,
                stored.originalFilename(), metadata);

        log.info("Bond {} uploaded for {} by {}", bond.getDocumentVersion(), candidate.getEmail(),
                hrUser.getEmail());
        return hrDto(bond, candidate.getId());
    }

    @Transactional(readOnly = true)
    public Optional<Bond> find(UUID candidateId) {
        return bondRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public BondDto hrView(UUID candidateId) {
        return bondRepository.findByCandidateId(candidateId).map(bond -> hrDto(bond, candidateId)).orElse(null);
    }

    /** Candidate view of the bond. Requires an accepted offer. */
    @Transactional(readOnly = true)
    public PortalBondDto portalView(Candidate candidate, String token) {
        stageGuard.requireBondAccess(candidate);
        Bond bond = bondRepository.findByCandidateId(candidate.getId()).orElse(null);
        if (bond == null) {
            return new PortalBondDto(false, null, null, null, null, null, signatureService.providerName(),
                    null, null, null, null, false,
                    "Your offer is accepted. HR is preparing your employment bond for signature.",
                    candidate.getName(), List.of());
        }
        boolean canSign = candidate.getStage() == Stage.OFFER_ACCEPTED && bond.getStatus() != BondStatus.SIGNED;
        String message = switch (bond.getStatus()) {
            case SIGNED -> "Your bond is signed. Onboarding is complete - welcome to CloudFuze.";
            case AWAITING_SIGNATURE -> "A signature session is open with SignatureOne. Complete it to finish.";
            case FAILED -> "The last signature attempt did not complete. Please try signing again.";
            case NOT_INITIATED -> "Review the bond document, then sign it electronically through SignatureOne.";
        };
        return new PortalBondDto(true, bond.getStatus(), bond.getDocumentVersion(), bond.getOriginalFilename(),
                "/api/portal/" + token + "/bond/file",
                bond.getSignedDocumentKey() == null ? null : "/api/portal/" + token + "/bond/signed-file",
                signatureService.providerName(), bond.getSignatureRef(), bond.getSigningUrl(), bond.getSignedAt(),
                bond.getSignerName(), canSign, message, candidate.getName(),
                bond.getAuditTrail().stream().map(BondAuditEventDto::from).toList());
    }

    /** Same view, re-reading the candidate so a just-committed stage change is reflected. */
    @Transactional(readOnly = true)
    public PortalBondDto portalViewById(UUID candidateId, String token) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> ResourceNotFoundException.candidate(candidateId));
        return portalView(candidate, token);
    }

    /**
     * Step 1 of signing: validate the gate and hand the caller everything the
     * provider needs. Runs read-only - nothing is mutated yet.
     */
    @Transactional(readOnly = true)
    public SigningPreparation prepareSigning(Candidate candidate) {
        stageGuard.requireBondSignable(candidate);
        Bond bond = bondRepository.findByCandidateId(candidate.getId())
                .orElseThrow(() -> new BusinessRuleException("BOND_NOT_AVAILABLE",
                        "Your bond document is not ready yet. HR is preparing it."));
        if (bond.getStatus() == BondStatus.SIGNED) {
            throw new BusinessRuleException("BOND_ALREADY_SIGNED", "This bond has already been signed.");
        }
        byte[] content = storageService.readAllBytes(bond.getStorageKey());
        return new SigningPreparation(bond.getId(), candidate.getId(), candidate.getName(), candidate.getEmail(),
                bond.getOriginalFilename(), bond.getContentType(), content, bond.getDocumentVersion(),
                bond.getSignatureRequestId());
    }

    /** Step 2: persist the provider request id and signing session. */
    @Transactional
    public void attachSignatureRequest(UUID bondId, UUID candidateId, String candidateEmail, String requestId,
                                       String signingUrl, String ipAddress) {
        Bond bond = requireBond(bondId);
        bond.setSignatureRequestId(requestId);
        bond.setSigningUrl(signingUrl);
        bond.setStatus(BondStatus.AWAITING_SIGNATURE);
        bond.setSignatureInitiatedAt(Instant.now());
        bond.addAuditEvent(new BondAuditEvent("signature_initiated", candidateEmail, Instant.now(), ipAddress,
                "Signature request " + requestId + " opened with SignatureOne"));
        bondRepository.save(bond);

        auditService.recordCandidateEvent(candidateId, AuditEventType.SIGNATURE_INITIATED, candidateEmail,
                ipAddress, bond.getDocumentVersion(), Map.of(
                        "signatureRequestId", requestId,
                        "provider", signatureService.providerName(),
                        "documentVersion", bond.getDocumentVersion()));
    }

    /** Step 3a: signing succeeded - store the signed artefact and complete onboarding. */
    @Transactional
    public void completeSigning(UUID bondId, UUID candidateId, SignatureStatusResult result,
                               SignedDocumentPayload signed, List<SignatureAuditEntry> providerTrail,
                               String signerName, String ipAddress, String userAgent) {
        Bond bond = requireBond(bondId);
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> ResourceNotFoundException.candidate(candidateId));

        StoredFile storedSigned = storageService.store("candidates/" + candidateId + "/bond/signed",
                signed.filename(), signed.contentType(), signed.content());
        Instant signedAt = result.updatedAt() == null ? Instant.now() : result.updatedAt();

        bond.setStatus(BondStatus.SIGNED);
        bond.setSignatureRef(result.signatureRef());
        bond.setSignedDocumentKey(storedSigned.key());
        bond.setSignedDocumentHash(storedSigned.sha256());
        bond.setSignedAt(signedAt);
        bond.setSignerName(signerName);
        bond.setSignerIp(ipAddress);
        bond.setFailureReason(null);
        providerTrail.forEach(entry -> bond.addAuditEvent(new BondAuditEvent(entry.eventType(), entry.actor(),
                entry.occurredAt() == null ? signedAt : entry.occurredAt(), entry.ipAddress(), entry.detail())));
        bondRepository.save(bond);

        candidate.setStage(Stage.BOND_SIGNED);
        candidate.setCompletedAt(signedAt);
        candidateRepository.save(candidate);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("signatureRequestId", result.requestId());
        metadata.put("signatureRef", result.signatureRef());
        metadata.put("documentVersion", bond.getDocumentVersion());
        metadata.put("signedDocumentSha256", storedSigned.sha256());
        metadata.put("signerName", signerName);
        metadata.put("provider", signatureService.providerName());
        metadata.put("userAgent", userAgent == null ? "unknown" : userAgent);
        auditService.recordCandidateEvent(candidateId, AuditEventType.BOND_SIGNED, candidate.getEmail(),
                ipAddress, bond.getDocumentVersion(), metadata);
        auditService.recordSystemEvent(candidateId, AuditEventType.ONBOARDING_COMPLETED,
                Map.of("completedAt", signedAt.toString(), "stage", Stage.BOND_SIGNED.getCode()));

        log.info("Candidate {} signed bond {} (ref {}) - onboarding complete", candidate.getEmail(),
                bond.getDocumentVersion(), result.signatureRef());
    }

    /** Step 3b: signing failed - record it so HR and the candidate can see why. */
    @Transactional
    public void recordSignatureFailure(UUID bondId, UUID candidateId, String candidateEmail, String reason,
                                       String ipAddress) {
        Bond bond = requireBond(bondId);
        String safeReason = reason == null ? "Signature was not completed." : reason;
        if (safeReason.length() > 590) {
            safeReason = safeReason.substring(0, 590);
        }
        bond.setStatus(BondStatus.FAILED);
        bond.setFailureReason(safeReason);
        bond.addAuditEvent(new BondAuditEvent("signature_failed", candidateEmail, Instant.now(), ipAddress,
                safeReason));
        bondRepository.save(bond);
        auditService.recordCandidateEvent(candidateId, AuditEventType.SIGNATURE_FAILED, candidateEmail,
                ipAddress, bond.getDocumentVersion(), Map.of("reason", safeReason));
    }

    /** Bond document for candidate download; enforces the stage gate. */
    @Transactional(readOnly = true)
    public Bond requireBondForCandidate(Candidate candidate) {
        stageGuard.requireBondAccess(candidate);
        return bondRepository.findByCandidateId(candidate.getId())
                .orElseThrow(() -> new BusinessRuleException("BOND_NOT_AVAILABLE",
                        "Your bond document is not ready yet."));
    }

    public Bond requireBond(UUID bondId) {
        return bondRepository.findById(bondId)
                .orElseThrow(() -> new ResourceNotFoundException("Bond not found: " + bondId));
    }

    private BondDto hrDto(Bond bond, UUID candidateId) {
        return mapper.toBondDto(bond, "/api/hr/candidates/" + candidateId + "/bond/file",
                "/api/hr/candidates/" + candidateId + "/bond/signed-file");
    }

    private StoredFile store(UUID candidateId, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return storageService.store("candidates/" + candidateId + "/bond",
                    uploadValidator.safeFilename(file), file.getContentType(), in, file.getSize());
        } catch (IOException e) {
            throw new StorageException("Could not read the uploaded bond document", e);
        }
    }

    private void safeDelete(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete replaced bond object {}: {}", key, e.getMessage());
        }
    }

    /** Everything the signing orchestrator needs, read in one transaction. */
    public record SigningPreparation(
            UUID bondId,
            UUID candidateId,
            String candidateName,
            String candidateEmail,
            String documentName,
            String contentType,
            byte[] documentContent,
            String documentVersion,
            String existingSignatureRequestId
    ) {
    }
}
