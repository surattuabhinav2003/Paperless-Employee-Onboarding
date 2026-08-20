package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.AcceptOfferRequest;
import com.cloudfuze.onboarding.dto.OfferDto;
import com.cloudfuze.onboarding.dto.PortalOfferDto;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.StorageException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.OfferStatus;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.OfferRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Offer letter handling. HR can prepare the offer at any time, but the candidate
 * cannot reach it until their documents are approved, and acceptance is the only
 * thing that unlocks bond signing.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final OfferRepository offerRepository;
    private final CandidateRepository candidateRepository;
    private final FileStorageService storageService;
    private final FileUploadValidator uploadValidator;
    private final StageGuard stageGuard;
    private final AuditService auditService;
    private final OnboardingMapper mapper;

    public OfferService(OfferRepository offerRepository, CandidateRepository candidateRepository,
                        FileStorageService storageService, FileUploadValidator uploadValidator,
                        StageGuard stageGuard, AuditService auditService, OnboardingMapper mapper) {
        this.offerRepository = offerRepository;
        this.candidateRepository = candidateRepository;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.stageGuard = stageGuard;
        this.auditService = auditService;
        this.mapper = mapper;
    }

    /** HR uploads (or replaces) the offer letter for a candidate. */
    @Transactional
    public OfferDto upload(Candidate candidate, MultipartFile file, String notes, HrPrincipal hrUser,
                           String ipAddress) {
        uploadValidator.validate(file);

        Offer offer = offerRepository.findByCandidateId(candidate.getId()).orElseGet(() -> new Offer(candidate));
        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            throw new BusinessRuleException("OFFER_ALREADY_ACCEPTED",
                    "This offer has already been accepted and can no longer be replaced.");
        }

        String previousKey = offer.getStorageKey();
        StoredFile stored = store(candidate.getId(), file);
        offer.setStorageKey(stored.key());
        offer.setOriginalFilename(stored.originalFilename());
        offer.setContentType(file.getContentType());
        offer.setSizeBytes(stored.sizeBytes());
        offer.setStatus(OfferStatus.SENT);
        offer.setSentAt(Instant.now());
        offer.setViewedAt(null);
        offer.setUploadedBy(hrUser.getEmail());
        offer.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        offerRepository.save(offer);

        if (previousKey != null && !previousKey.equals(stored.key())) {
            safeDelete(previousKey);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filename", stored.originalFilename());
        metadata.put("sizeBytes", stored.sizeBytes());
        metadata.put("sha256", stored.sha256());
        metadata.put("candidateStage", candidate.getStage().getCode());
        metadata.put("visibleToCandidate", candidate.getStage().isAtLeast(Stage.DOCS_APPROVED));
        auditService.recordHrEvent(candidate.getId(), AuditEventType.OFFER_UPLOADED, hrUser.getEmail(),
                ipAddress, stored.originalFilename(), metadata);

        log.info("Offer letter uploaded for {} by {}", candidate.getEmail(), hrUser.getEmail());
        return mapper.toOfferDto(offer, "/api/hr/candidates/" + candidate.getId() + "/offer/file");
    }

    @Transactional(readOnly = true)
    public Optional<Offer> find(UUID candidateId) {
        return offerRepository.findByCandidateId(candidateId);
    }

    @Transactional(readOnly = true)
    public OfferDto hrView(UUID candidateId) {
        return offerRepository.findByCandidateId(candidateId)
                .map(offer -> mapper.toOfferDto(offer, "/api/hr/candidates/" + candidateId + "/offer/file"))
                .orElse(null);
    }

    /** Candidate view of the offer. Requires docs_approved or later. */
    @Transactional(readOnly = true)
    public PortalOfferDto portalView(Candidate candidate, String token) {
        stageGuard.requireOfferAccess(candidate);
        Offer offer = offerRepository.findByCandidateId(candidate.getId()).orElse(null);
        if (offer == null) {
            return new PortalOfferDto(false, null, null, null, null, null, null, false,
                    "Your documents are approved. HR is preparing your offer letter - "
                            + "you will be able to review it here shortly.",
                    candidate.getName(), candidate.getRole(), candidate.getDepartment());
        }
        boolean canAccept = candidate.getStage() == Stage.DOCS_APPROVED && offer.getStatus() != OfferStatus.ACCEPTED;
        String message = switch (offer.getStatus()) {
            case ACCEPTED -> "You have accepted this offer. Continue to bond signing to finish.";
            case VIEWED -> "Review the offer letter and accept it to continue to bond signing.";
            case SENT -> "Your offer letter is ready. Open it to review the details.";
        };
        return new PortalOfferDto(true, offer.getStatus(), offer.getOriginalFilename(), offer.getSentAt(),
                offer.getViewedAt(), offer.getAcceptedAt(),
                "/api/portal/" + token + "/offer/file", canAccept, message,
                candidate.getName(), candidate.getRole(), candidate.getDepartment());
    }

    /** Records that the candidate opened the offer letter. */
    @Transactional
    public PortalOfferDto markViewed(Candidate candidate, String token, String ipAddress, String userAgent) {
        stageGuard.requireOfferAccess(candidate);
        Offer offer = requireOffer(candidate);
        if (offer.getStatus() == OfferStatus.SENT) {
            offer.setStatus(OfferStatus.VIEWED);
            offer.setViewedAt(Instant.now());
            offerRepository.save(offer);
            auditService.recordCandidateEvent(candidate.getId(), AuditEventType.OFFER_VIEWED,
                    candidate.getEmail(), ipAddress, offer.getOriginalFilename(),
                    Map.of("userAgent", userAgent == null ? "unknown" : userAgent));
            log.info("Candidate {} viewed their offer letter", candidate.getEmail());
        } else if (offer.getViewedAt() == null) {
            offer.setViewedAt(Instant.now());
            offerRepository.save(offer);
        }
        return portalView(candidate, token);
    }

    /** Candidate acceptance. This is the only transition to offer_accepted. */
    @Transactional
    public PortalOfferDto accept(Candidate candidate, String token, AcceptOfferRequest request,
                                String ipAddress, String userAgent) {
        stageGuard.requireOfferAcceptable(candidate);
        Offer offer = requireOffer(candidate);

        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            throw new BusinessRuleException("OFFER_ALREADY_ACCEPTED", "This offer has already been accepted.");
        }

        Instant now = Instant.now();
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setAcceptedAt(now);
        offer.setAcceptedByName(request.acknowledgementName().trim());
        offer.setAcceptedFromIp(ipAddress);
        if (offer.getViewedAt() == null) {
            offer.setViewedAt(now);
        }
        offerRepository.save(offer);

        candidate.setStage(Stage.OFFER_ACCEPTED);
        candidateRepository.save(candidate);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("acknowledgementName", offer.getAcceptedByName());
        metadata.put("offerFilename", offer.getOriginalFilename());
        metadata.put("userAgent", userAgent == null ? "unknown" : userAgent);
        auditService.recordCandidateEvent(candidate.getId(), AuditEventType.OFFER_ACCEPTED, candidate.getEmail(),
                ipAddress, offer.getOriginalFilename(), metadata);
        auditService.recordSystemEvent(candidate.getId(), AuditEventType.BOND_UNLOCKED,
                Map.of("stage", Stage.OFFER_ACCEPTED.getCode()));

        log.info("Candidate {} accepted their offer - bond stage unlocked", candidate.getEmail());
        return portalView(candidate, token);
    }

    /** Offer document for streaming to the candidate; enforces the stage gate. */
    @Transactional
    public Offer requireOfferForCandidateDownload(Candidate candidate, String ipAddress) {
        stageGuard.requireOfferAccess(candidate);
        Offer offer = requireOffer(candidate);
        if (offer.getStatus() == OfferStatus.SENT) {
            offer.setStatus(OfferStatus.VIEWED);
            offer.setViewedAt(Instant.now());
            offerRepository.save(offer);
            auditService.recordCandidateEvent(candidate.getId(), AuditEventType.OFFER_VIEWED,
                    candidate.getEmail(), ipAddress, offer.getOriginalFilename(), Map.of("via", "download"));
        }
        return offer;
    }

    public Offer requireOffer(Candidate candidate) {
        return offerRepository.findByCandidateId(candidate.getId())
                .orElseThrow(() -> new BusinessRuleException("OFFER_NOT_AVAILABLE",
                        "Your offer letter has not been published yet. HR is preparing it."));
    }

    private StoredFile store(UUID candidateId, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return storageService.store("candidates/" + candidateId + "/offer",
                    uploadValidator.safeFilename(file), file.getContentType(), in, file.getSize());
        } catch (IOException e) {
            throw new StorageException("Could not read the uploaded offer letter", e);
        }
    }

    private void safeDelete(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete replaced offer object {}: {}", key, e.getMessage());
        }
    }
}
