package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.OfferDto;
import com.cloudfuze.onboarding.dto.PortalOfferDto;
import com.cloudfuze.onboarding.dto.OfferFieldDto;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.email.ReviewNotificationComposer;
import com.cloudfuze.onboarding.security.PortalTokenService;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.StorageException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.OfferField;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Offer letter handling. HR can prepare the offer at any time, but the candidate
 * cannot reach it until their documents are approved, and acceptance is the final
 * step of onboarding.
 */
@Service
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final OfferRepository offerRepository;
    private final CandidateRepository candidateRepository;
    private final FileStorageService storageService;
    private final FileUploadValidator uploadValidator;
    private final HrNotifier hrNotifier;
    private final DocumentConversionService conversionService;
    private final StageGuard stageGuard;
    private final AuditService auditService;
    private final OnboardingMapper mapper;
    private final OfferSigningService signingService;
    private final PortalTokenService portalTokenService;
    private final EmailService emailService;
    private final ReviewNotificationComposer reviewComposer;

    public OfferService(OfferRepository offerRepository, CandidateRepository candidateRepository,
                        FileStorageService storageService, FileUploadValidator uploadValidator, DocumentConversionService conversionService,
                        HrNotifier hrNotifier,
                        StageGuard stageGuard, AuditService auditService, OnboardingMapper mapper,
                        OfferSigningService signingService,
                        PortalTokenService portalTokenService,
                        EmailService emailService,
                        ReviewNotificationComposer reviewComposer) {
        this.offerRepository = offerRepository;
        this.candidateRepository = candidateRepository;
        this.storageService = storageService;
        this.uploadValidator = uploadValidator;
        this.hrNotifier = hrNotifier;
        this.conversionService = conversionService;
        this.stageGuard = stageGuard;
        this.auditService = auditService;
        this.mapper = mapper;
        this.signingService = signingService;
        this.portalTokenService = portalTokenService;
        this.emailService = emailService;
        this.reviewComposer = reviewComposer;
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
        String previousSignedKey = offer.getSignedStorageKey();
        StoredFile stored = store(candidate.getId(), file);
        offer.setStorageKey(stored.key());
        offer.setOriginalFilename(stored.originalFilename());
        offer.setContentType(stored.contentType());
        offer.setSizeBytes(stored.sizeBytes());
        // Uploading prepares a draft; the candidate sees nothing until HR sends it.
        // Replacing an already-sent letter pulls it back to draft, so a new file is
        // never live until it has been reviewed and sent again. A new file may have
        // an entirely different layout, so any signature boxes placed on the old one
        // (and any already-signed copy) no longer mean anything.
        offer.setStatus(OfferStatus.DRAFT);
        offer.setSentAt(Instant.now());
        offer.setViewedAt(null);
        offer.setUploadedBy(hrUser.getEmail());
        offer.setNotes(notes == null || notes.isBlank() ? null : notes.trim());
        offer.getFields().clear();
        offer.setSignedStorageKey(null);
        offerRepository.save(offer);

        if (previousKey != null && !previousKey.equals(stored.key())) {
            safeDelete(previousKey);
        }
        if (previousSignedKey != null) {
            safeDelete(previousSignedKey);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("filename", stored.originalFilename());
        metadata.put("sizeBytes", stored.sizeBytes());
        metadata.put("sha256", stored.sha256());
        metadata.put("candidateStage", candidate.getStage().getCode());
        metadata.put("visibleToCandidate", candidate.getStage().isAtLeast(Stage.DOCS_APPROVED));
        auditService.recordHrEvent(candidate.getId(), AuditEventType.OFFER_UPLOADED, hrUser.getEmail(),
                ipAddress, stored.originalFilename(), metadata);

        log.info("Offer letter uploaded (draft) for {} by {}", candidate.getEmail(), hrUser.getEmail());
        return mapper.toOfferDto(offer, "/api/hr/candidates/" + candidate.getId() + "/offer/file");
    }

    /**
     * HR's placement of fields on the offer PDF, as percentages of each page's
     * dimensions. Replaces whatever was placed before. Only possible while the
     * letter is still a draft - once sent, the candidate is looking at fixed
     * positions and moving them would be confusing at best.
     */
    @Transactional
    public OfferDto saveFields(Candidate candidate, List<OfferFieldDto> fields, HrPrincipal hrUser,
                               String ipAddress) {
        Offer offer = offerRepository.findByCandidateId(candidate.getId())
                .orElseThrow(() -> new BusinessRuleException("OFFER_NOT_FOUND",
                        "Upload an offer letter before placing fields."));
        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new BusinessRuleException("OFFER_ALREADY_SENT",
                    "This offer has already been sent, so its fields can no longer be moved.");
        }

        List<OfferField> next = fields.stream()
                .map(f -> new OfferField(f.type(), f.page(), f.xPct(), f.yPct(), f.widthPct(), f.heightPct(),
                        blankToNull(f.prefill()), blankToNull(f.textColor()), f.textFont()))
                .collect(Collectors.toCollection(ArrayList::new));
        offer.getFields().clear();
        offer.getFields().addAll(next);
        offerRepository.save(offer);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.OFFER_SIGNATURE_FIELDS_SET, hrUser.getEmail(),
                ipAddress, offer.getOriginalFilename(), Map.of("fieldCount", next.size()));

        log.info("HR {} placed {} field(s) on the offer for {}", hrUser.getEmail(), next.size(),
                candidate.getEmail());
        return mapper.toOfferDto(offer, "/api/hr/candidates/" + candidate.getId() + "/offer/file");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Releases a drafted offer letter to the candidate.
     *
     * <p>Separate from {@link #upload} so HR can look the letter over before it
     * becomes visible; only a draft can be sent, and only once. A letter with no
     * signature field placed cannot be sent - the candidate would have nowhere to
     * sign.
     */
    @Transactional
    public OfferDto send(Candidate candidate, HrPrincipal hrUser, String ipAddress) {
        Offer offer = offerRepository.findByCandidateId(candidate.getId())
                .orElseThrow(() -> new BusinessRuleException("OFFER_NOT_FOUND",
                        "Upload an offer letter before sending it."));
        if (offer.getStatus() != OfferStatus.DRAFT) {
            throw new BusinessRuleException("OFFER_ALREADY_SENT",
                    "This offer letter has already been sent to the candidate.");
        }
        if (!offer.hasSignatureField()) {
            throw new BusinessRuleException("SIGNATURE_FIELDS_REQUIRED",
                    "Place at least one signature field on the offer letter before sending it.");
        }

        /*
         * Build the link before committing to anything. Sending was previously
         * silent - the letter was marked SENT and the candidate was never told,
         * so it sat unread until they happened to open the portal. An offer that
         * nobody was told about has not really been sent.
         */
        String offerUrl = offerUrl(candidate);

        offer.setStatus(OfferStatus.SENT);
        offer.setSentAt(Instant.now());
        offerRepository.save(offer);

        /*
         * The email is the point of this operation, so a failure to deliver it
         * fails the whole thing: the transaction rolls back, the letter stays a
         * draft, and HR can try again. Marking it sent while the mail bounced
         * would leave everyone waiting on the other.
         */
        try {
            emailService.send(reviewComposer.offerReady(candidate, offerUrl));
        } catch (RuntimeException e) {
            log.error("Offer email to {} could not be delivered: {}", candidate.getEmail(), e.getMessage());
            throw new BusinessRuleException("EMAIL_NOT_SENT",
                    "The offer letter could not be emailed right now, so it has not been sent. "
                            + "Please try again.");
        }

        auditService.recordHrEvent(candidate.getId(), AuditEventType.OFFER_SENT, hrUser.getEmail(),
                ipAddress, offer.getOriginalFilename(),
                Map.of("visibleToCandidate", candidate.getStage().isAtLeast(Stage.DOCS_APPROVED),
                        "emailedTo", candidate.getEmail()));

        log.info("Offer letter sent to {} by {}", candidate.getEmail(), hrUser.getEmail());
        return mapper.toOfferDto(offer, "/api/hr/candidates/" + candidate.getId() + "/offer/file");
    }

    /**
     * The candidate's signing page, as an absolute URL.
     *
     * <p>An expired link is a hard stop rather than a degraded email: without a
     * working token the candidate cannot open the letter at all, so telling them
     * it is ready would only frustrate them. HR regenerates the link first.
     */
    private String offerUrl(Candidate candidate) {
        if (!candidate.isTokenActive(Instant.now())) {
            throw new BusinessRuleException("LINK_EXPIRED",
                    "This candidate's onboarding link has expired, so they could not open the letter. "
                            + "Generate a new link before sending the offer.");
        }
        return portalTokenService.decrypt(candidate.getInviteTokenCipher())
                .map(portalTokenService::portalUrl)
                .map(url -> url + "/offer")
                .orElseThrow(() -> new BusinessRuleException("LINK_NOT_RECOVERABLE",
                        "This candidate's link cannot be recovered to put in an email. "
                                + "Generate a new one first."));
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
        // A draft is HR's to review; to the candidate it does not exist yet.
        if (offer == null || offer.getStatus() == OfferStatus.DRAFT) {
            return new PortalOfferDto(false, null, null, null, null, null, null, false,
                    "Your documents are approved. HR is preparing your offer letter - "
                            + "you will be able to review it here shortly.",
                    candidate.getName(), candidate.getRole(), candidate.getDepartment(), List.of());
        }
        boolean canAccept = candidate.getStage() == Stage.DOCS_APPROVED && offer.getStatus() != OfferStatus.ACCEPTED;
        String message = switch (offer.getStatus()) {
            case ACCEPTED -> "You have signed this offer. Your onboarding is complete.";
            case VIEWED -> "Review the offer letter, then draw your signature to accept it.";
            case SENT -> "Your offer letter is ready. Open it to review the details.";
            // Unreachable: a draft is filtered out above. Present only so the
            // switch stays exhaustive over the enum.
            case DRAFT -> "Your offer letter is being prepared.";
        };
        return new PortalOfferDto(true, offer.getStatus(), offer.getOriginalFilename(), offer.getSentAt(),
                offer.getViewedAt(), offer.getAcceptedAt(),
                "/api/portal/" + token + "/offer/file", canAccept, message,
                candidate.getName(), candidate.getRole(), candidate.getDepartment(),
                mapper.toOfferFieldDtos(offer));
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

    /**
     * Candidate acceptance: their drawn signature is stamped onto every field HR
     * placed, producing the signed PDF. This is the only transition to
     * offer_accepted, which is the terminal stage - signing completes onboarding.
     */
    @Transactional
    public PortalOfferDto sign(Candidate candidate, String token, Map<Integer, String> fieldValues,
                              String ipAddress, String userAgent) {
        stageGuard.requireOfferAcceptable(candidate);
        // requireOffer refuses a draft, so a candidate cannot sign a letter that
        // has not been sent to them.
        Offer offer = requireOffer(candidate);

        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            throw new BusinessRuleException("OFFER_ALREADY_ACCEPTED", "This offer has already been signed.");
        }
        if (!offer.hasSignatureField()) {
            // Guarded against at send-time too; only reachable if data is inconsistent.
            throw new BusinessRuleException("NO_SIGNATURE_FIELDS",
                    "This offer has no signature field to sign. Contact HR.");
        }

        // The browser hides the submit button until everything is filled in, but
        // that is a convenience - the pack is only valid if the server agrees.
        List<OfferField> fields = offer.getFields();
        for (int i = 0; i < fields.size(); i++) {
            String value = fieldValues.get(i);
            if (value == null || value.isBlank()) {
                throw new BusinessRuleException("FIELD_INCOMPLETE",
                        "Complete every field on the offer letter before submitting.");
            }
            if (fields.get(i).getType().isSignature()) {
                // Validates the data URI and size; throws with a clear message if not.
                signingService.decodeSignatureImage(value);
            }
        }

        byte[] originalPdf = storageService.readAllBytes(offer.getStorageKey());
        byte[] signedPdf = signingService.stamp(originalPdf, fields, fieldValues);

        StoredFile stored = storageService.store("candidates/" + candidate.getId() + "/offer",
                "signed-" + offer.getOriginalFilename(), offer.getContentType(), signedPdf);
        if (offer.getSignedStorageKey() != null) {
            safeDelete(offer.getSignedStorageKey());
        }
        offer.setSignedStorageKey(stored.key());

        Instant now = Instant.now();
        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setAcceptedAt(now);
        offer.setAcceptedByName(candidate.getName());
        offer.setAcceptedFromIp(ipAddress);
        if (offer.getViewedAt() == null) {
            offer.setViewedAt(now);
        }
        offerRepository.save(offer);

        candidate.setStage(Stage.OFFER_ACCEPTED);
        candidate.setCompletedAt(now);
        candidateRepository.save(candidate);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("signedBy", offer.getAcceptedByName());
        metadata.put("offerFilename", offer.getOriginalFilename());
        metadata.put("fieldCount", offer.getFields().size());
        metadata.put("userAgent", userAgent == null ? "unknown" : userAgent);
        auditService.recordCandidateEvent(candidate.getId(), AuditEventType.OFFER_ACCEPTED, candidate.getEmail(),
                ipAddress, offer.getOriginalFilename(), metadata);
        auditService.recordSystemEvent(candidate.getId(), AuditEventType.ONBOARDING_COMPLETED,
                Map.of("stage", Stage.OFFER_ACCEPTED.getCode(), "completedAt", now.toString()));

        log.info("Candidate {} signed their offer - onboarding complete", candidate.getEmail());
        hrNotifier.offerSigned(candidate, offer.getAcceptedByName());
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

    /**
     * The candidate's offer, or a clear refusal. A draft counts as "not there yet"
     * to the candidate, so every candidate-facing path - view, download, accept -
     * is closed until HR sends it.
     */
    public Offer requireOffer(Candidate candidate) {
        Offer offer = offerRepository.findByCandidateId(candidate.getId())
                .orElseThrow(() -> new BusinessRuleException("OFFER_NOT_AVAILABLE",
                        "Your offer letter has not been published yet. HR is preparing it."));
        if (offer.getStatus() == OfferStatus.DRAFT) {
            throw new BusinessRuleException("OFFER_NOT_SENT",
                    "Your offer letter has not been published yet. HR is preparing it.");
        }
        return offer;
    }

    /**
     * Stores the letter as PDF. A Word upload is converted first, because the
     * candidate signs by having values stamped onto PDF pages - there is no
     * signing a .docx.
     */
    private StoredFile store(UUID candidateId, MultipartFile file) {
        byte[] pdf = conversionService.toPdf(file);
        return storageService.store("candidates/" + candidateId + "/offer",
                pdfFilename(uploadValidator.safeFilename(file)), "application/pdf", pdf);
    }

    /** Word in, PDF out - so the stored name matches what is actually stored. */
    private static String pdfFilename(String original) {
        int dot = original.lastIndexOf('.');
        return (dot < 0 ? original : original.substring(0, dot)) + ".pdf";
    }

    private void safeDelete(String key) {
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete replaced offer object {}: {}", key, e.getMessage());
        }
    }
}
