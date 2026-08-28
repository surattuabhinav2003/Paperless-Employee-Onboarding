package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.dto.DocumentProgressDto;
import com.cloudfuze.onboarding.dto.PortalDocumentsDto;
import com.cloudfuze.onboarding.dto.PortalOfferDto;
import com.cloudfuze.onboarding.dto.PortalOverviewDto;
import com.cloudfuze.onboarding.dto.PortalStepDto;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.InvalidPortalTokenException;
import com.cloudfuze.onboarding.exception.PortalTokenExpiredException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.OfferStatus;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.security.PortalTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The candidate side of the portal. Every method starts by resolving the token
 * (hash lookup + expiry) and then relies on {@link StageGuard} for gating, so a
 * candidate cannot reach a stage they have not unlocked - regardless of what the
 * frontend does or which URL they guess.
 */
@Service
public class PortalService {

    /** Portal access is only re-audited after this quiet period, to avoid noise. */
    private static final Duration ACCESS_AUDIT_INTERVAL = Duration.ofMinutes(30);

    private final CandidateRepository candidateRepository;
    private final PortalTokenService portalTokenService;
    private final DocumentService documentService;
    private final CandidateProfileService profileService;
    private final HrNotifier hrNotifier;
    private final OfferService offerService;
    /* @Lazy breaks the cycle: verification needs the portal to authenticate
       a link, and the portal needs verification to gate one. */
    private final PortalVerificationService verificationService;
    private final AuditService auditService;
    private final OnboardingMapper mapper;
    private final AppProperties appProperties;
    private final EmailProperties emailProperties;

    public PortalService(CandidateRepository candidateRepository, PortalTokenService portalTokenService,
                         DocumentService documentService,
                         CandidateProfileService profileService, HrNotifier hrNotifier,
                         OfferService offerService, AuditService auditService,
                         OnboardingMapper mapper, AppProperties appProperties, EmailProperties emailProperties,
                         @org.springframework.context.annotation.Lazy
                         PortalVerificationService verificationService) {
        this.candidateRepository = candidateRepository;
        this.portalTokenService = portalTokenService;
        this.documentService = documentService;
        this.profileService = profileService;
        this.hrNotifier = hrNotifier;
        this.offerService = offerService;
        this.verificationService = verificationService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.emailProperties = emailProperties;
    }

    /** Header the browser sends back once a device has been verified. */
    public static final String DEVICE_HEADER = "X-Portal-Device";

    /**
     * Authenticates the link <em>and</em> requires that this device has proved
     * control of the candidate's inbox.
     *
     * <p>Every portal endpoint uses this; only the two verification endpoints
     * use {@link #authenticate(String)} directly, because they are how a device
     * earns its trust in the first place.
     */
    public Candidate authenticateVerified(String rawToken, String deviceMarker) {
        Candidate candidate = authenticate(rawToken);
        if (!verificationService.isTrusted(deviceMarker, candidate)) {
            throw new com.cloudfuze.onboarding.exception.ApiException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "VERIFICATION_REQUIRED",
                    "Please confirm the code we emailed you before continuing.");
        }
        return candidate;
    }

    /**
     * Resolves a raw portal token to its candidate. The raw token is hashed and
     * compared against the stored hash; expiry is enforced here, once, for every
     * candidate endpoint.
     */
    @Transactional(readOnly = true)
    public Candidate authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() < 20) {
            throw new InvalidPortalTokenException();
        }
        Candidate candidate = candidateRepository.findByInviteTokenHash(portalTokenService.hash(rawToken))
                .orElseThrow(InvalidPortalTokenException::new);
        Instant expiresAt = candidate.getTokenExpiresAt();
        if (expiresAt == null) {
            throw new InvalidPortalTokenException();
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new PortalTokenExpiredException(expiresAt);
        }
        return candidate;
    }

    /**
     * What the candidate still has to do before they can submit. Empty means the
     * Submit button is live.
     */
    @Transactional(readOnly = true)
    public List<String> outstandingItems(Candidate candidate, List<CandidateDocument> documents,
                                         boolean profileSubmitted) {
        List<String> outstanding = new ArrayList<>();
        if (!profileSubmitted) {
            outstanding.add("Fill in your personal details");
        }
        Map<DocumentType, CandidateDocument> byType = new LinkedHashMap<>();
        documents.forEach(doc -> byType.put(doc.getDocumentType(), doc));
        candidate.getRequiredDocuments().stream()
                .filter(RequiredDocument::isMandatory)
                .forEach(requirement -> {
                    CandidateDocument doc = byType.get(requirement.getDocumentType());
                    if (doc == null) {
                        outstanding.add("Upload " + requirement.displayName());
                    } else if (doc.getStatus() == DocumentStatus.REJECTED) {
                        outstanding.add("Re-upload " + requirement.displayName());
                    }
                });
        return outstanding;
    }

    /**
     * The candidate reviews everything and hands it to HR. Nothing reaches HR
     * review until this happens.
     */
    @Transactional
    public PortalOverviewDto submitForReview(String token, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        if (candidate.getStage() != Stage.DOCS_PENDING) {
            throw new BusinessRuleException("ALREADY_APPROVED",
                    "Your onboarding pack has already been approved by HR.");
        }
        if (candidate.isSubmittedForReview()) {
            throw new BusinessRuleException("ALREADY_SUBMITTED",
                    "You have already submitted your onboarding pack. HR will be in touch.");
        }

        List<CandidateDocument> documents = documentService.documentsOf(candidate.getId());
        boolean profileSubmitted = profileService.isComplete(candidate.getId());
        List<String> outstanding = outstandingItems(candidate, documents, profileSubmitted);
        if (!outstanding.isEmpty()) {
            throw new BusinessRuleException("SUBMISSION_INCOMPLETE",
                    "Please finish these first: " + String.join("; ", outstanding) + ".");
        }

        candidate.setSubmittedForReviewAt(Instant.now());
        candidateRepository.save(candidate);
        auditService.recordCandidateEvent(candidate.getId(), AuditEventType.SUBMITTED_FOR_REVIEW,
                candidate.getEmail(), ipAddress, null, Map.of(
                        "documents", documents.size(),
                        "userAgent", userAgent == null ? "unknown" : userAgent));

        // HR is told once, here - this is the moment the pack becomes theirs.
        hrNotifier.candidateSubmitted(candidate, documents.size());

        return overview(token, ipAddress, userAgent);
    }

    @Transactional
    public PortalOverviewDto overview(String token, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        recordAccess(candidate, ipAddress, userAgent);

        List<CandidateDocument> documents = documentService.documentsOf(candidate.getId());
        DocumentProgressDto progress = mapper.progress(candidate, documents);
        Offer offer = offerService.find(candidate.getId()).orElse(null);

        boolean complete = candidate.getStage() == Stage.OFFER_ACCEPTED;
        boolean profileSubmitted = profileService.isComplete(candidate.getId());
        List<String> outstanding = candidate.getStage() == Stage.DOCS_PENDING
                ? outstandingItems(candidate, documents, profileSubmitted)
                : List.of();
        boolean editable = candidate.getStage() == Stage.DOCS_PENDING && !candidate.isSubmittedForReview();
        return new PortalOverviewDto(
                candidate.getName(),
                candidate.getRole(),
                candidate.getDepartment(),
                candidate.getStage(),
                candidate.getStage().getLabel(),
                headlineFor(candidate.getStage(), candidate.isSubmittedForReview(), progress.rejected() > 0),
                messageFor(candidate, progress, offer, profileSubmitted,
                        candidate.isSubmittedForReview()),
                buildSteps(candidate, progress, offer, profileSubmitted, candidate.isSubmittedForReview()),
                currentStepFor(candidate.getStage()),
                progress,
                profileSubmitted,
                editable,
                outstanding.isEmpty() && candidate.getStage() == Stage.DOCS_PENDING
                        && !candidate.isSubmittedForReview(),
                candidate.isSubmittedForReview(),
                candidate.getSubmittedForReviewAt(),
                outstanding,
                editable,
                candidate.getStage().isAtLeast(Stage.DOCS_APPROVED),
                // A drafted-but-unsent letter is not yet the candidate's business, so
                // they may still review their documents until HR sends it.
                offer != null && offer.getStatus() != com.cloudfuze.onboarding.model.OfferStatus.DRAFT,
                complete,
                candidate.getTokenExpiresAt(),
                candidate.getCompletedAt(),
                emailProperties.getSupportContact());
    }

    @Transactional(readOnly = true)
    public CandidateProfileDto profile(String token) {
        Candidate candidate = authenticate(token);
        return profileService.view(candidate.getId());
    }

    @Transactional
    public CandidateProfileDto saveProfile(String token, CandidateProfileRequest request, String ipAddress) {
        Candidate candidate = authenticate(token);
        return profileService.save(candidate, request, ipAddress);
    }

    @Transactional(readOnly = true)
    public PortalDocumentsDto documents(String token) {
        Candidate candidate = authenticate(token);
        return documentsView(candidate, token);
    }

    @Transactional
    public PortalDocumentsDto uploadDocument(String token, String typeCode, EducationCourse course,
                                            MultipartFile file, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        documentService.upload(candidate, typeCode, course, file, ipAddress, userAgent);
        return documentsView(candidate, token);
    }

    @Transactional(readOnly = true)
    public PortalOfferDto offer(String token) {
        Candidate candidate = authenticate(token);
        return offerService.portalView(candidate, token);
    }

    @Transactional
    public PortalOfferDto markOfferViewed(String token, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        return offerService.markViewed(candidate, token, ipAddress, userAgent);
    }

    @Transactional
    public PortalOfferDto signOffer(String token, java.util.Map<Integer, String> fieldValues, String ipAddress,
                                    String userAgent) {
        Candidate candidate = authenticate(token);
        return offerService.sign(candidate, token, fieldValues, ipAddress, userAgent);
    }

    @Transactional(readOnly = true)
    public PortalDocumentsDto documentsView(Candidate candidate, String token) {
        List<CandidateDocument> documents = documentService.documentsOf(candidate.getId());
        DocumentProgressDto progress = mapper.progress(candidate, documents);
        boolean uploadAllowed = candidate.getStage() == Stage.DOCS_PENDING;
        String message;
        if (!uploadAllowed) {
            message = "All of your documents have been approved. Nothing further is needed here.";
        } else if (candidate.isSubmittedForReview() && progress.rejected() == 0) {
            message = "We are reviewing your documents. If HR needs any re-uploaded, you will be able "
                    + "to do it here - we will email you when the next step is ready.";
        } else {
            message = documentMessage(progress);
        }
        return new PortalDocumentsDto(uploadAllowed, message, progress,
                mapper.toDocumentDtos(candidate, documents, doc -> mapper.portalDocumentUrl(token, doc)),
                appProperties.getUpload().getMaxFileSizeBytes(),
                appProperties.getUpload().getAllowedExtensions());
    }

    private void recordAccess(Candidate candidate, String ipAddress, String userAgent) {
        Instant last = candidate.getLastPortalAccessAt();
        Instant now = Instant.now();
        candidate.setLastPortalAccessAt(now);
        candidateRepository.save(candidate);
        if (last == null || last.isBefore(now.minus(ACCESS_AUDIT_INTERVAL))) {
            auditService.recordCandidateEvent(candidate.getId(), AuditEventType.PORTAL_ACCESSED,
                    candidate.getEmail(), ipAddress, null,
                    Map.of("stage", candidate.getStage().getCode(),
                            "userAgent", userAgent == null ? "unknown" : userAgent));
        }
    }

    /**
     * The candidate only ever sees the step they are on. Steps they have finished
     * and steps they have not unlocked are left out entirely, so the portal never
     * dangles a padlocked stage or a completed one in front of them.
     */
    private List<PortalStepDto> buildSteps(Candidate candidate, DocumentProgressDto progress, Offer offer,
                                           boolean profileSubmitted, boolean submitted) {
        Stage stage = candidate.getStage();
        return switch (stage) {
            case DOCS_PENDING -> List.of(new PortalStepDto("documents", "Details & Documents",
                    PortalStepDto.CURRENT, documentStatusText(progress, profileSubmitted, submitted), null));
            case DOCS_APPROVED -> List.of(new PortalStepDto("offer", "Offer Letter",
                    PortalStepDto.CURRENT, offerStatusText(stage, offer), null));
            case OFFER_ACCEPTED -> List.of(new PortalStepDto("offer", "Offer Letter",
                    PortalStepDto.COMPLETED, offerStatusText(stage, offer), null));
        };
    }

    /** The single page the candidate belongs on right now. */
    private String currentStepFor(Stage stage) {
        return switch (stage) {
            case DOCS_PENDING -> "documents";
            case DOCS_APPROVED -> "offer";
            case OFFER_ACCEPTED -> "offer";
        };
    }

    private String documentStatusText(DocumentProgressDto progress, boolean profileSubmitted,
                                      boolean submitted) {
        // A sent-back document is the candidate's job again, so it outranks the
        // "submitted" state - otherwise the tracker keeps saying "with HR" while
        // the candidate is being asked to re-upload.
        if (progress.rejected() > 0) {
            return progress.rejected() == 1
                    ? "1 document needs re-upload"
                    : progress.rejected() + " documents need re-upload";
        }
        if (submitted) {
            return "Submitted - with HR";
        }
        if (!profileSubmitted) {
            return "Your details are needed";
        }
        if (progress.missing() > 0) {
            return progress.missing() == 1
                    ? "1 document still to upload"
                    : progress.missing() + " documents still to upload";
        }
        return "Ready to submit";
    }

    private String offerStatusText(Stage stage, Offer offer) {
        if (stage.isAtLeast(Stage.OFFER_ACCEPTED)) {
            return "Accepted";
        }
        if (offer == null) {
            return "Being prepared by HR";
        }
        return offer.getStatus() == OfferStatus.VIEWED ? "Awaiting your acceptance" : "Ready to review";
    }

    private String headlineFor(Stage stage, boolean submitted, boolean hasRejected) {
        // A rejection outranks "submitted": the candidate has something to do again.
        if (stage == Stage.DOCS_PENDING && hasRejected) {
            return "HR needs a document re-uploaded";
        }
        /* Once it is submitted the work is done, so the headline must not keep
           telling the candidate to complete it. */
        if (stage == Stage.DOCS_PENDING && submitted) {
            return "Thanks - your documents are under review";
        }
        return switch (stage) {
            case DOCS_PENDING -> "Complete your details and documents";
            case DOCS_APPROVED -> "Review and accept your offer letter";
            case OFFER_ACCEPTED -> "Onboarding Complete";
        };
    }

    private String messageFor(Candidate candidate, DocumentProgressDto progress, Offer offer,
                              boolean profileSubmitted, boolean submitted) {
        return switch (candidate.getStage()) {
            // A rejection reopens work even after submitting, so it takes priority.
            case DOCS_PENDING -> progress.rejected() > 0
                    ? documentMessage(progress)
                    : submitted
                        ? "We are reviewing your documents now. If HR needs any of them re-uploaded, "
                          + "you will be able to do it here - we will email you as soon as the next "
                          + "step is ready."
                        : profileSubmitted
                            ? documentMessage(progress)
                            : "Start by filling in your details, then upload the documents listed for you.";
            case DOCS_APPROVED -> offer == null
                    ? "Your documents are approved. HR is preparing your offer letter."
                    : "Your documents are approved. Review your offer letter and accept it to continue.";
            case OFFER_ACCEPTED -> "Everything is done. Welcome to Neutara - your HR team will be in "
                    + "touch with your joining details.";
        };
    }

    private String documentMessage(DocumentProgressDto progress) {
        if (progress.rejected() > 0) {
            return "HR asked for " + progress.rejected() + " document"
                    + (progress.rejected() == 1 ? "" : "s") + " to be re-uploaded. "
                    + "Check the notes below and upload a replacement.";
        }
        if (progress.missing() > 0) {
            return "Upload the documents listed below. You can do it in any order and come back any time.";
        }
        return "You have uploaded everything. Review and submit when you are ready.";
    }
}
