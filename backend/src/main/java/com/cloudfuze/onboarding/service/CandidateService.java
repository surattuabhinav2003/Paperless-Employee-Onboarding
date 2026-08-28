package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.CandidateCreatedDto;
import com.cloudfuze.onboarding.dto.CandidateDetailDto;
import com.cloudfuze.onboarding.dto.CandidateSummaryDto;
import com.cloudfuze.onboarding.dto.CreateCandidateRequest;
import com.cloudfuze.onboarding.dto.InvitationDto;
import com.cloudfuze.onboarding.dto.PageResponse;
import com.cloudfuze.onboarding.dto.RequiredDocumentRequest;
import com.cloudfuze.onboarding.dto.UpdateCandidateRequest;
import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.email.InvitationMailComposer;
import com.cloudfuze.onboarding.email.ReviewNotificationComposer;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.DuplicateResourceException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateDocumentRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.CandidateSpecifications;
import com.cloudfuze.onboarding.repository.OfferRepository;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.security.PortalTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Candidate lifecycle: creation with a single invitation email, pipeline
 * queries, and portal link regeneration.
 */
@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final OfferRepository offerRepository;
    private final CandidateProfileService profileService;
    private final PortalTokenService portalTokenService;
    private final EmailService emailService;
    private final InvitationMailComposer mailComposer;
    private final AuditService auditService;
    private final OnboardingMapper mapper;
    private final ReviewNotificationComposer reviewComposer;
    private final StageGuard stageGuard;
    private final DocumentApprovalService approvalService;

    private final DocumentCatalogService catalog;

    public CandidateService(CandidateRepository candidateRepository,
                           CandidateDocumentRepository documentRepository,
                           OfferRepository offerRepository,
                           CandidateProfileService profileService,
                           PortalTokenService portalTokenService,
                           EmailService emailService,
                           InvitationMailComposer mailComposer,
                           AuditService auditService,
                           OnboardingMapper mapper,
                           ReviewNotificationComposer reviewComposer,
                           StageGuard stageGuard,
                           DocumentApprovalService approvalService,
                           DocumentCatalogService catalog) {
        this.catalog = catalog;
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.offerRepository = offerRepository;
        this.profileService = profileService;
        this.portalTokenService = portalTokenService;
        this.emailService = emailService;
        this.mailComposer = mailComposer;
        this.auditService = auditService;
        this.mapper = mapper;
        this.reviewComposer = reviewComposer;
        this.stageGuard = stageGuard;
        this.approvalService = approvalService;
    }

    /**
     * Creates the candidate, issues one portal token and sends exactly one
     * invitation email covering the whole journey.
     */
    @Transactional
    public CandidateCreatedDto create(CreateCandidateRequest request, HrPrincipal hrUser, String ipAddress) {
        String email = request.email().trim().toLowerCase();
        if (candidateRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException(
                    "A candidate with the email " + email + " is already in the pipeline.");
        }

        Candidate candidate = new Candidate();
        candidate.setName(request.name().trim());
        candidate.setEmail(email);
        candidate.setRole(request.role().trim());
        candidate.setDepartment(request.department().trim());
        candidate.setStage(Stage.DOCS_PENDING);
        candidate.setCreatedBy(hrUser.getEmail());
        candidate.getRequiredDocuments().addAll(toRequiredDocuments(request.requiredDocuments()));

        PortalTokenService.IssuedPortalToken token = portalTokenService.issue();
        applyToken(candidate, token);
        candidateRepository.save(candidate);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.CANDIDATE_CREATED, hrUser.getEmail(),
                ipAddress, null, Map.of(
                        "role", candidate.getRole(),
                        "department", candidate.getDepartment(),
                        "requiredDocuments", candidate.getRequiredDocuments().stream()
                                .map(rd -> rd.getDocumentType().getCode()).toList()));

        InvitationDto invitation = deliverInvitation(candidate, token, InvitationMailComposer.Kind.INITIAL,
                hrUser.getEmail(), ipAddress, AuditEventType.INVITATION_GENERATED);

        log.info("Candidate {} created by {} with {} required documents", candidate.getEmail(), hrUser.getEmail(),
                candidate.getRequiredDocuments().size());
        return new CandidateCreatedDto(mapper.toSummary(candidate, List.of(), null, false), invitation);
    }

    /** Re-issues the portal link and emails it. The previous link stops working. */
    @Transactional
    public InvitationDto resendInvitation(UUID candidateId, HrPrincipal hrUser, String ipAddress,
                                          boolean regenerate) {
        Candidate candidate = requireCandidate(candidateId);
        PortalTokenService.IssuedPortalToken token = portalTokenService.issue();
        applyToken(candidate, token);
        candidateRepository.save(candidate);

        InvitationMailComposer.Kind kind = regenerate
                ? InvitationMailComposer.Kind.REGENERATED
                : InvitationMailComposer.Kind.RESEND;
        AuditEventType eventType = regenerate
                ? AuditEventType.PORTAL_TOKEN_REGENERATED
                : AuditEventType.INVITATION_RESENT;

        return deliverInvitation(candidate, token, kind, hrUser.getEmail(), ipAddress, eventType);
    }

    @Transactional(readOnly = true)
    public PageResponse<CandidateSummaryDto> list(String query, Stage stage, int page, int size) {
        Page<Candidate> result = candidateRepository.findAll(
                CandidateSpecifications.matching(query, stage),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        List<UUID> ids = result.getContent().stream().map(Candidate::getId).toList();
        Map<UUID, List<CandidateDocument>> documents = documentsByCandidate(ids);
        Map<UUID, Offer> offers = offersByCandidate(ids);

        return PageResponse.from(result, candidate -> {
            List<CandidateDocument> candidateDocuments = documents.getOrDefault(candidate.getId(), List.of());
            return mapper.toSummary(candidate, candidateDocuments, offers.get(candidate.getId()),
                    approvalService.isReadyForApproval(candidate, candidateDocuments));
        });
    }

    @Transactional(readOnly = true)
    public CandidateDetailDto detail(UUID candidateId) {
        Candidate candidate = requireCandidate(candidateId);
        List<CandidateDocument> documents = documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId);
        Offer offer = offerRepository.findByCandidateId(candidateId).orElse(null);

        return new CandidateDetailDto(
                mapper.toSummary(candidate, documents, offer, approvalService.isReadyForApproval(candidate, documents)),
                profileService.view(candidateId),
                mapper.toRequiredDocumentDtos(candidate),
                mapper.toDocumentDtos(candidate, documents, mapper::hrDocumentUrl),
                mapper.toOfferDto(offer, "/api/hr/candidates/" + candidateId + "/offer/file"),
                auditService.forCandidate(candidateId));
    }

    @Transactional(readOnly = true)
    public CandidateSummaryDto summary(UUID candidateId) {
        Candidate candidate = requireCandidate(candidateId);
        List<CandidateDocument> documents = documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId);
        return mapper.toSummary(candidate, documents,
                offerRepository.findByCandidateId(candidateId).orElse(null),
                approvalService.isReadyForApproval(candidate, documents));
    }

    @Transactional(readOnly = true)
    public List<CandidateSummaryDto> summaries(List<Candidate> candidates) {
        List<UUID> ids = candidates.stream().map(Candidate::getId).toList();
        Map<UUID, List<CandidateDocument>> documents = documentsByCandidate(ids);
        Map<UUID, Offer> offers = offersByCandidate(ids);
        return candidates.stream()
                .map(candidate -> {
                    List<CandidateDocument> candidateDocuments = documents.getOrDefault(candidate.getId(), List.of());
                    return mapper.toSummary(candidate, candidateDocuments, offers.get(candidate.getId()),
                            approvalService.isReadyForApproval(candidate, candidateDocuments));
                })
                .toList();
    }

    /**
     * The candidate's live portal link, for HR to copy or re-send. Every read is
     * audited, since it exposes the credential that opens the portal.
     */
    @Transactional
    public InvitationDto portalLink(UUID candidateId, HrPrincipal hrUser, String ipAddress) {
        Candidate candidate = requireCandidate(candidateId);
        if (!candidate.isTokenActive(Instant.now())) {
            throw new BusinessRuleException("LINK_EXPIRED",
                    "This candidate's link has expired. Generate a new one to give them access again.");
        }

        String rawToken = portalTokenService.decrypt(candidate.getInviteTokenCipher())
                .orElseThrow(() -> new BusinessRuleException("LINK_NOT_RECOVERABLE",
                        "This link was issued before links could be re-read, so it cannot be shown. "
                                + "Generate a new one to get a copyable link."));

        auditService.recordHrEvent(candidate.getId(), AuditEventType.PORTAL_LINK_VIEWED, hrUser.getEmail(),
                ipAddress, null, Map.of("expiresAt", String.valueOf(candidate.getTokenExpiresAt())));

        return new InvitationDto(portalTokenService.portalUrl(rawToken), candidate.getTokenExpiresAt(),
                candidate.getInvitationSentAt(), candidate.getInvitationCount(), candidate.getEmail(),
                emailProviderName());
    }

    private String emailProviderName() {
        return emailService.providerName();
    }

    public Candidate requireCandidate(UUID candidateId) {
        return candidateRepository.findById(candidateId)
                .orElseThrow(() -> ResourceNotFoundException.candidate(candidateId));
    }

    /**
     * HR corrects the facts they own: display name, role and department. The
     * email is not editable here - it is the address the invitation went to and
     * what the portal link is bound to, so changing it would leave a live link
     * pointing at the wrong person.
     */
    @Transactional
    public CandidateSummaryDto updateDetails(UUID candidateId, UpdateCandidateRequest request,
                                             HrPrincipal hrUser, String ipAddress) {
        Candidate candidate = requireCandidate(candidateId);

        Map<String, Object> changes = new LinkedHashMap<>();
        if (!candidate.getName().equals(request.name().trim())) {
            changes.put("name", candidate.getName() + " -> " + request.name().trim());
        }
        if (!candidate.getRole().equals(request.role().trim())) {
            changes.put("role", candidate.getRole() + " -> " + request.role().trim());
        }
        if (!candidate.getDepartment().equals(request.department().trim())) {
            changes.put("department", candidate.getDepartment() + " -> " + request.department().trim());
        }

        candidate.setName(request.name().trim());
        candidate.setRole(request.role().trim());
        candidate.setDepartment(request.department().trim());
        candidateRepository.save(candidate);

        // A no-op save is not worth an audit entry that says nothing changed.
        if (!changes.isEmpty()) {
            auditService.recordHrEvent(candidate.getId(), AuditEventType.CANDIDATE_UPDATED, hrUser.getEmail(),
                    ipAddress, null, changes);
            log.info("HR {} updated candidate {}: {}", hrUser.getEmail(), candidate.getEmail(), changes.keySet());
        }
        return summary(candidateId);
    }

    /**
     * Adds new document requirements to a candidate who has already been
     * invited - types already required are skipped rather than duplicated.
     * Only possible while documents are still open for review; once approved
     * there is nothing left to gate.
     */
    @Transactional
    public void addRequiredDocuments(UUID candidateId, List<RequiredDocumentRequest> requests, HrPrincipal hrUser,
                                     String ipAddress) {
        Candidate candidate = requireCandidate(candidateId);
        stageGuard.requireDocumentReviewOpen(candidate);

        Set<DocumentType> already = candidate.getRequiredDocuments().stream()
                .map(RequiredDocument::getDocumentType)
                .collect(Collectors.toSet());
        List<RequiredDocument> additions = toRequiredDocuments(requests).stream()
                .filter(rd -> !already.contains(rd.getDocumentType()))
                .toList();
        if (additions.isEmpty()) {
            throw new BusinessRuleException("NOTHING_TO_ADD",
                    "Every document in that selection is already required for this candidate.");
        }

        List<RequiredDocument> next = new ArrayList<>(candidate.getRequiredDocuments());
        next.addAll(additions);
        candidate.setRequiredDocuments(next);
        candidateRepository.save(candidate);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.REQUIRED_DOCUMENT_ADDED, hrUser.getEmail(),
                ipAddress, null, Map.of("added", additions.stream()
                        .map(rd -> rd.getDocumentType().getCode()).toList()));
        log.info("HR {} added {} document requirement(s) for candidate {}", hrUser.getEmail(), additions.size(),
                candidate.getEmail());
    }

    /**
     * Toggles an existing requirement between mandatory and optional. Only
     * possible while documents are still open for review, since a completed
     * approval already relied on the previous mandatory set.
     */
    @Transactional
    public void updateRequiredDocumentMandatory(UUID candidateId, String typeCode, boolean mandatory,
                                                HrPrincipal hrUser, String ipAddress) {
        Candidate candidate = requireCandidate(candidateId);
        stageGuard.requireDocumentReviewOpen(candidate);

        boolean found = candidate.getRequiredDocuments().stream()
                .anyMatch(rd -> rd.typeCode().equals(typeCode));
        if (!found) {
            throw new BusinessRuleException("DOCUMENT_NOT_REQUESTED",
                    "That document is not part of this candidate's requirements.");
        }

        List<RequiredDocument> next = candidate.getRequiredDocuments().stream()
                .map(rd -> rd.typeCode().equals(typeCode) ? withMandatory(rd, mandatory) : rd)
                .collect(Collectors.toCollection(ArrayList::new));
        candidate.setRequiredDocuments(next);
        candidateRepository.save(candidate);

        auditService.recordHrEvent(candidate.getId(), AuditEventType.REQUIRED_DOCUMENT_MANDATORY_CHANGED,
                hrUser.getEmail(), ipAddress, typeCode, Map.of("mandatory", mandatory));
        log.info("HR {} set {} to mandatory={} for candidate {}", hrUser.getEmail(), typeCode, mandatory,
                candidate.getEmail());
    }

    /** The outcome of a notify action, so HR sees what was sent. */
    public record NotifyResult(String outcome, int rejectedCount, String emailedTo) {
    }

    /**
     * HR's "I'm done reviewing" action - deliberately manual and one-shot, so a
     * candidate is never buried under one email per document.
     *
     * <p>When every mandatory document is verified and nothing is rejected, this
     * is also the moment approval actually happens: clicking this button is what
     * advances the candidate to {@code docs_approved} and unlocks the offer
     * stage, not the act of verifying the last document. That keeps every
     * verify/reject decision reversible right up until HR deliberately commits
     * to it here.
     */
    @Transactional
    public NotifyResult notifyReviewOutcome(UUID candidateId, HrPrincipal hrUser, String ipAddress) {
        Candidate candidate = requireCandidate(candidateId);

        if (!candidate.isSubmittedForReview()) {
            throw new BusinessRuleException("NOT_SUBMITTED",
                    "This candidate has not submitted their pack yet, so there is nothing to notify them about.");
        }
        if (!candidate.isTokenActive(Instant.now())) {
            throw new BusinessRuleException("LINK_EXPIRED",
                    "This candidate's link has expired. Generate a new one before notifying them.");
        }
        String portalUrl = portalTokenService.decrypt(candidate.getInviteTokenCipher())
                .map(portalTokenService::portalUrl)
                .orElseThrow(() -> new BusinessRuleException("LINK_NOT_RECOVERABLE",
                        "This link cannot be recovered to include in an email. Generate a new one first."));

        List<CandidateDocument> documents = documentRepository
                .findByCandidateIdOrderByUploadedAtAsc(candidate.getId());
        List<CandidateDocument> rejected = documents.stream()
                .filter(d -> d.getStatus() == DocumentStatus.REJECTED)
                .toList();

        EmailMessage message;
        String outcome;
        if (!rejected.isEmpty()) {
            message = reviewComposer.reupload(candidate, rejected, portalUrl);
            outcome = "reupload";
        } else if (candidate.getStage().isAtLeast(Stage.DOCS_APPROVED)) {
            // Approval is a one-shot decision, so there is no resend: HR is
            // expected to check everything before committing to it. A candidate
            // who lost the email can be re-sent their portal link instead.
            throw new BusinessRuleException("ALREADY_APPROVED",
                    "This candidate is already approved and has been emailed. Resend their onboarding "
                            + "link from the Details tab if they need it again.");
        } else if (approvalService.isReadyForApproval(candidate, documents)) {
            approvalService.approve(candidate, documents, hrUser, ipAddress);
            message = reviewComposer.approved(candidate, portalUrl);
            outcome = "approved";
        } else {
            throw new BusinessRuleException("NOTHING_TO_NOTIFY",
                    "Verify or reject this candidate's documents first - there is no review outcome to send yet.");
        }

        try {
            emailService.send(message);
        } catch (RuntimeException e) {
            log.error("Review notification to {} could not be delivered: {}", candidate.getEmail(), e.getMessage());
            throw new BusinessRuleException("EMAIL_NOT_SENT",
                    "The email could not be sent right now. Please try again.");
        }

        auditService.recordHrEvent(candidate.getId(), AuditEventType.CANDIDATE_NOTIFIED, hrUser.getEmail(),
                ipAddress, null, Map.of("outcome", outcome, "rejectedCount", rejected.size(),
                        "emailProvider", emailService.providerName()));
        log.info("Notified {} of review outcome '{}' ({} rejected) by {}",
                candidate.getEmail(), outcome, rejected.size(), hrUser.getEmail());
        return new NotifyResult(outcome, rejected.size(), candidate.getEmail());
    }

    private InvitationDto deliverInvitation(Candidate candidate, PortalTokenService.IssuedPortalToken token,
                                            InvitationMailComposer.Kind kind, String actor, String ipAddress,
                                            AuditEventType eventType) {
        EmailMessage message = mailComposer.compose(candidate, token.portalUrl(), token.expiresAt(), kind);
        Instant sentAt = null;
        try {
            emailService.send(message);
            sentAt = Instant.now();
            candidate.setInvitationSentAt(sentAt);
            candidate.setInvitationCount(candidate.getInvitationCount() + 1);
            candidateRepository.save(candidate);
        } catch (RuntimeException e) {
            // The candidate and token are already valid; HR can resend from the pipeline.
            log.error("Invitation email to {} could not be delivered: {}", candidate.getEmail(), e.getMessage());
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("emailKind", kind.name().toLowerCase());
        metadata.put("emailProvider", emailService.providerName());
        metadata.put("emailDelivered", sentAt != null);
        metadata.put("tokenExpiresAt", token.expiresAt().toString());
        auditService.recordHrEvent(candidate.getId(), eventType, actor, ipAddress, null, metadata);

        return new InvitationDto(token.portalUrl(), token.expiresAt(), sentAt, candidate.getInvitationCount(),
                candidate.getEmail(), emailService.providerName());
    }

    private void applyToken(Candidate candidate, PortalTokenService.IssuedPortalToken token) {
        candidate.setInviteTokenHash(token.tokenHash());
        candidate.setInviteTokenCipher(token.tokenCipher());
        candidate.setTokenIssuedAt(token.issuedAt());
        candidate.setTokenExpiresAt(token.expiresAt());
    }

    /* Requirements are value objects, so changing one means replacing it -
       carrying across whichever kind of type it was. */
    private static RequiredDocument withMandatory(RequiredDocument rd, boolean mandatory) {
        return rd.isCustomType()
                ? new RequiredDocument(rd.getCustomTypeCode(), mandatory, rd.getLabel())
                : new RequiredDocument(rd.getDocumentType(), mandatory, rd.getLabel());
    }

    private List<RequiredDocument> toRequiredDocuments(List<RequiredDocumentRequest> requests) {
        Set<String> seen = new LinkedHashSet<>();
        return requests.stream()
                .filter(request -> seen.add(request.type()))
                .map(this::toRequiredDocument)
                .toList();
    }

    /**
     * Turns one requested type code into a requirement.
     *
     * <p>An admin-created type has no enum constant and no label of its own on
     * the requirement, so its name is copied in at this point - the requirement
     * has to be able to name itself later even if the type is withdrawn.
     */
    private RequiredDocument toRequiredDocument(RequiredDocumentRequest request) {
        DocumentCatalogService.Entry type = catalog.requireSelectable(request.type());
        String label = request.label() == null || request.label().isBlank()
                ? null : request.label().trim();
        return type.custom()
                ? new RequiredDocument(type.code(), request.mandatory(),
                        label == null ? type.label() : label)
                : new RequiredDocument(type.builtIn(), request.mandatory(), label);
    }

    private Map<UUID, List<CandidateDocument>> documentsByCandidate(List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        return documentRepository.findByCandidateIdIn(candidateIds).stream()
                .collect(Collectors.groupingBy(doc -> doc.getCandidate().getId(), HashMap::new,
                        Collectors.toList()));
    }

    private Map<UUID, Offer> offersByCandidate(List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Offer> offers = new HashMap<>();
        offerRepository.findByCandidateIdIn(candidateIds)
                .forEach(offer -> offers.put(offer.getCandidate().getId(), offer));
        return offers;
    }

}
