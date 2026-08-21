package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.DashboardStatsDto;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.OfferStatus;
import com.cloudfuze.onboarding.model.RequiredDocument;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.repository.CandidateDocumentRepository;
import com.cloudfuze.onboarding.repository.CandidateRepository;
import com.cloudfuze.onboarding.repository.OfferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Aggregates the four dashboard counters plus supporting context. */
@Service
public class DashboardService {

    private final CandidateRepository candidateRepository;
    private final CandidateDocumentRepository documentRepository;
    private final OfferRepository offerRepository;
    private final CandidateService candidateService;
    private final AuditService auditService;

    public DashboardService(CandidateRepository candidateRepository,
                            CandidateDocumentRepository documentRepository,
                            OfferRepository offerRepository,
                            CandidateService candidateService,
                            AuditService auditService) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.offerRepository = offerRepository;
        this.candidateService = candidateService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto stats() {
        long totalCandidates = candidateRepository.count();
        long activeCandidates = candidateRepository.countByStageNot(Stage.OFFER_ACCEPTED);
        long awaitingReview = documentRepository.countByStatus(DocumentStatus.SUBMITTED);
        long onboardingComplete = candidateRepository.countByStage(Stage.OFFER_ACCEPTED);
        long offersAwaitingAcceptance = offerRepository.countByStatus(OfferStatus.SENT)
                + offerRepository.countByStatus(OfferStatus.VIEWED);

        Map<String, Long> stageBreakdown = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            stageBreakdown.put(stage.getCode(), candidateRepository.countByStage(stage));
        }

        return new DashboardStatsDto(
                activeCandidates,
                countDocumentsWaitingOnCandidates(),
                awaitingReview,
                onboardingComplete,
                totalCandidates,
                offersAwaitingAcceptance,
                stageBreakdown,
                candidateService.summaries(candidateRepository.findTop8ByOrderByCreatedAtDesc()),
                auditService.recent(12));
    }

    /**
     * Requested documents that still need the candidate to act: never uploaded, or
     * rejected and awaiting a replacement. Only candidates still in the document
     * stage are counted.
     */
    private long countDocumentsWaitingOnCandidates() {
        List<Candidate> pending = candidateRepository.findByStage(Stage.DOCS_PENDING);
        if (pending.isEmpty()) {
            return 0;
        }
        List<UUID> ids = pending.stream().map(Candidate::getId).toList();
        Map<UUID, Map<DocumentType, DocumentStatus>> statuses = documentRepository.findByCandidateIdIn(ids).stream()
                .collect(Collectors.groupingBy(doc -> doc.getCandidate().getId(),
                        Collectors.toMap(CandidateDocument::getDocumentType, CandidateDocument::getStatus,
                                (first, second) -> second)));

        long waiting = 0;
        for (Candidate candidate : pending) {
            Map<DocumentType, DocumentStatus> byType = statuses.getOrDefault(candidate.getId(), Map.of());
            for (RequiredDocument requirement : candidate.getRequiredDocuments()) {
                DocumentStatus status = byType.get(requirement.getDocumentType());
                if (status == null || status == DocumentStatus.REJECTED) {
                    waiting++;
                }
            }
        }
        return waiting;
    }
}
