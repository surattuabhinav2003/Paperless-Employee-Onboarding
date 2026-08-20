package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.CandidateCreatedDto;
import com.cloudfuze.onboarding.dto.CandidateDetailDto;
import com.cloudfuze.onboarding.dto.CandidateSummaryDto;
import com.cloudfuze.onboarding.dto.CreateCandidateRequest;
import com.cloudfuze.onboarding.dto.InvitationDto;
import com.cloudfuze.onboarding.dto.PageResponse;
import com.cloudfuze.onboarding.dto.RequiredDocumentRequest;
import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.email.InvitationMailComposer;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.DuplicateResourceException;
import com.cloudfuze.onboarding.exception.ResourceNotFoundException;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.Bond;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.BondRepository;
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
    private final BondRepository bondRepository;
    private final CandidateProfileService profileService;
    private final PortalTokenService portalTokenService;
    private final EmailService emailService;
    private final InvitationMailComposer mailComposer;
    private final AuditService auditService;
    private final OnboardingMapper mapper;

    public CandidateService(CandidateRepository candidateRepository,
                           CandidateDocumentRepository documentRepository,
                           OfferRepository offerRepository,
                           BondRepository bondRepository,
                           CandidateProfileService profileService,
                           PortalTokenService portalTokenService,
                           EmailService emailService,
                           InvitationMailComposer mailComposer,
                           AuditService auditService,
                           OnboardingMapper mapper) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.offerRepository = offerRepository;
        this.bondRepository = bondRepository;
        this.profileService = profileService;
        this.portalTokenService = portalTokenService;
        this.emailService = emailService;
        this.mailComposer = mailComposer;
        this.auditService = auditService;
        this.mapper = mapper;
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
        return new CandidateCreatedDto(mapper.toSummary(candidate, List.of(), null, null), invitation);
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
        Map<UUID, Bond> bonds = bondsByCandidate(ids);

        return PageResponse.from(result, candidate -> mapper.toSummary(candidate,
                documents.getOrDefault(candidate.getId(), List.of()),
                offers.get(candidate.getId()), bonds.get(candidate.getId())));
    }

    @Transactional(readOnly = true)
    public CandidateDetailDto detail(UUID candidateId) {
        Candidate candidate = requireCandidate(candidateId);
        List<CandidateDocument> documents = documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId);
        Offer offer = offerRepository.findByCandidateId(candidateId).orElse(null);
        Bond bond = bondRepository.findByCandidateId(candidateId).orElse(null);

        return new CandidateDetailDto(
                mapper.toSummary(candidate, documents, offer, bond),
                profileService.view(candidateId),
                mapper.toRequiredDocumentDtos(candidate),
                mapper.toDocumentDtos(candidate, documents, mapper::hrDocumentUrl),
                mapper.toOfferDto(offer, "/api/hr/candidates/" + candidateId + "/offer/file"),
                mapper.toBondDto(bond, "/api/hr/candidates/" + candidateId + "/bond/file",
                        "/api/hr/candidates/" + candidateId + "/bond/signed-file"),
                auditService.forCandidate(candidateId));
    }

    @Transactional(readOnly = true)
    public CandidateSummaryDto summary(UUID candidateId) {
        Candidate candidate = requireCandidate(candidateId);
        return mapper.toSummary(candidate,
                documentRepository.findByCandidateIdOrderByUploadedAtAsc(candidateId),
                offerRepository.findByCandidateId(candidateId).orElse(null),
                bondRepository.findByCandidateId(candidateId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<CandidateSummaryDto> summaries(List<Candidate> candidates) {
        List<UUID> ids = candidates.stream().map(Candidate::getId).toList();
        Map<UUID, List<CandidateDocument>> documents = documentsByCandidate(ids);
        Map<UUID, Offer> offers = offersByCandidate(ids);
        Map<UUID, Bond> bonds = bondsByCandidate(ids);
        return candidates.stream()
                .map(candidate -> mapper.toSummary(candidate,
                        documents.getOrDefault(candidate.getId(), List.of()),
                        offers.get(candidate.getId()), bonds.get(candidate.getId())))
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

    private List<RequiredDocument> toRequiredDocuments(List<RequiredDocumentRequest> requests) {
        Set<DocumentType> seen = new LinkedHashSet<>();
        return requests.stream()
                .filter(request -> seen.add(request.type()))
                .map(request -> new RequiredDocument(request.type(), request.mandatory(),
                        request.label() == null || request.label().isBlank() ? null : request.label().trim()))
                .toList();
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

    private Map<UUID, Bond> bondsByCandidate(List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Bond> bonds = new HashMap<>();
        bondRepository.findByCandidateIdIn(candidateIds)
                .forEach(bond -> bonds.put(bond.getCandidate().getId(), bond));
        return bonds;
    }
}
