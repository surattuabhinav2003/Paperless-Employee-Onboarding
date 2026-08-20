package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.dto.AcceptOfferRequest;
import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.dto.DocumentProgressDto;
import com.cloudfuze.onboarding.dto.PortalBondDto;
import com.cloudfuze.onboarding.dto.PortalDocumentsDto;
import com.cloudfuze.onboarding.dto.PortalOfferDto;
import com.cloudfuze.onboarding.dto.PortalOverviewDto;
import com.cloudfuze.onboarding.dto.PortalStepDto;
import com.cloudfuze.onboarding.dto.SignBondRequest;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.InvalidPortalTokenException;
import com.cloudfuze.onboarding.exception.PortalTokenExpiredException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Bond;
import com.cloudfuze.onboarding.model.BondStatus;
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
    private final DocumentApprovalService approvalService;
    private final CandidateProfileService profileService;
    private final OfferService offerService;
    private final BondService bondService;
    private final BondSigningService bondSigningService;
    private final AuditService auditService;
    private final OnboardingMapper mapper;
    private final AppProperties appProperties;
    private final EmailProperties emailProperties;

    public PortalService(CandidateRepository candidateRepository, PortalTokenService portalTokenService,
                         DocumentService documentService, DocumentApprovalService approvalService,
                         CandidateProfileService profileService,
                         OfferService offerService, BondService bondService,
                         BondSigningService bondSigningService, AuditService auditService,
                         OnboardingMapper mapper, AppProperties appProperties, EmailProperties emailProperties) {
        this.candidateRepository = candidateRepository;
        this.portalTokenService = portalTokenService;
        this.documentService = documentService;
        this.approvalService = approvalService;
        this.profileService = profileService;
        this.offerService = offerService;
        this.bondService = bondService;
        this.bondSigningService = bondSigningService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.emailProperties = emailProperties;
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

        // Submitting can be the last missing piece - HR may already have verified
        // everything while the candidate was still finishing their pack.
        approvalService.evaluate(candidate);

        return overview(token, ipAddress, userAgent);
    }

    @Transactional
    public PortalOverviewDto overview(String token, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        recordAccess(candidate, ipAddress, userAgent);

        List<CandidateDocument> documents = documentService.documentsOf(candidate.getId());
        DocumentProgressDto progress = mapper.progress(candidate, documents);
        Offer offer = offerService.find(candidate.getId()).orElse(null);
        Bond bond = bondService.find(candidate.getId()).orElse(null);

        boolean complete = candidate.getStage() == Stage.BOND_SIGNED;
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
                headlineFor(candidate.getStage()),
                messageFor(candidate, progress, offer, bond, profileSubmitted,
                        candidate.isSubmittedForReview()),
                buildSteps(candidate, progress, offer, bond, profileSubmitted, candidate.isSubmittedForReview()),
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
                candidate.getStage().isAtLeast(Stage.OFFER_ACCEPTED),
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
    public PortalDocumentsDto uploadDocument(String token, DocumentType type, EducationCourse course,
                                            MultipartFile file, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        documentService.upload(candidate, type, course, file, ipAddress, userAgent);
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
    public PortalOfferDto acceptOffer(String token, AcceptOfferRequest request, String ipAddress,
                                      String userAgent) {
        Candidate candidate = authenticate(token);
        return offerService.accept(candidate, token, request, ipAddress, userAgent);
    }

    @Transactional(readOnly = true)
    public PortalBondDto bond(String token) {
        Candidate candidate = authenticate(token);
        return bondService.portalView(candidate, token);
    }

    /** Not transactional on purpose - the signing orchestrator manages its own steps. */
    public PortalBondDto signBond(String token, SignBondRequest request, String ipAddress, String userAgent) {
        Candidate candidate = authenticate(token);
        return bondSigningService.sign(candidate, token, request, ipAddress, userAgent);
    }

    @Transactional(readOnly = true)
    public PortalDocumentsDto documentsView(Candidate candidate, String token) {
        List<CandidateDocument> documents = documentService.documentsOf(candidate.getId());
        DocumentProgressDto progress = mapper.progress(candidate, documents);
        boolean uploadAllowed = candidate.getStage() == Stage.DOCS_PENDING;
        String message = uploadAllowed
                ? documentMessage(progress)
                : "All of your documents have been approved. Nothing further is needed here.";
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
                                           Bond bond, boolean profileSubmitted, boolean submitted) {
        Stage stage = candidate.getStage();
        return switch (stage) {
            case DOCS_PENDING -> List.of(new PortalStepDto("documents", "Details & Documents",
                    PortalStepDto.CURRENT, documentStatusText(progress, profileSubmitted, submitted), null));
            case DOCS_APPROVED -> List.of(new PortalStepDto("offer", "Offer Letter",
                    PortalStepDto.CURRENT, offerStatusText(stage, offer), null));
            case OFFER_ACCEPTED -> List.of(new PortalStepDto("bond", "Bond Signing",
                    PortalStepDto.CURRENT, bondStatusText(stage, bond), null));
            case BOND_SIGNED -> List.of(new PortalStepDto("bond", "Bond Signing",
                    PortalStepDto.COMPLETED, bondStatusText(stage, bond), null));
        };
    }

    /** The single page the candidate belongs on right now. */
    private String currentStepFor(Stage stage) {
        return switch (stage) {
            case DOCS_PENDING -> "documents";
            case DOCS_APPROVED -> "offer";
            case OFFER_ACCEPTED, BOND_SIGNED -> "bond";
        };
    }

    private String documentStatusText(DocumentProgressDto progress, boolean profileSubmitted,
                                      boolean submitted) {
        if (submitted) {
            return "Submitted - with HR";
        }
        if (!profileSubmitted) {
            return "Your details are needed";
        }
        if (progress.rejected() > 0) {
            return progress.rejected() == 1
                    ? "1 document needs re-upload"
                    : progress.rejected() + " documents need re-upload";
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

    private String bondStatusText(Stage stage, Bond bond) {
        if (stage == Stage.BOND_SIGNED) {
            return "Signed";
        }
        if (bond == null) {
            return "Being prepared by HR";
        }
        return bond.getStatus() == BondStatus.FAILED ? "Signature not completed" : "Ready to sign";
    }

    private String headlineFor(Stage stage) {
        return switch (stage) {
            case DOCS_PENDING -> "Complete your details and documents";
            case DOCS_APPROVED -> "Review and accept your offer letter";
            case OFFER_ACCEPTED -> "Sign your employment bond";
            case BOND_SIGNED -> "Onboarding Complete";
        };
    }

    private String messageFor(Candidate candidate, DocumentProgressDto progress, Offer offer, Bond bond,
                              boolean profileSubmitted, boolean submitted) {
        return switch (candidate.getStage()) {
            case DOCS_PENDING -> submitted
                    ? "Everything is with HR now. There is nothing more for you to do here - "
                      + "we will email you as soon as the next step is ready."
                    : profileSubmitted
                        ? documentMessage(progress)
                        : "Start by filling in your details, then upload the documents listed for you.";
            case DOCS_APPROVED -> offer == null
                    ? "Your documents are approved. HR is preparing your offer letter."
                    : "Your documents are approved. Review your offer letter and accept it to continue.";
            case OFFER_ACCEPTED -> bond == null
                    ? "Thanks for accepting your offer. HR is preparing your employment bond."
                    : "One step left: review and sign your employment bond through SignatureOne.";
            case BOND_SIGNED -> "Everything is done. Welcome to CloudFuze - your HR team will be in touch "
                    + "with your joining details.";
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
        return "Everything is with HR now. There is nothing more for you to do here - "
                + "we will email you as soon as the next step is ready.";
    }
}
