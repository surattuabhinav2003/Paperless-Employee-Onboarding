package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.DashboardStatsDto;
import com.cloudfuze.onboarding.dto.DocumentProgressDto;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.OfferStatus;
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
    private final OnboardingMapper mapper;
    private final AuditService auditService;

    public DashboardService(CandidateRepository candidateRepository,
                            CandidateDocumentRepository documentRepository,
                            OfferRepository offerRepository,
                            CandidateService candidateService,
                            OnboardingMapper mapper,
                            AuditService auditService) {
        this.candidateRepository = candidateRepository;
        this.documentRepository = documentRepository;
        this.offerRepository = offerRepository;
        this.candidateService = candidateService;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto stats() {
        long totalCandidates = candidateRepository.count();
        long activeCandidates = candidateRepository.countByStageNot(Stage.OFFER_ACCEPTED);
        long verificationDone = candidateRepository.countByStage(Stage.DOCS_APPROVED);
        long onboardingComplete = candidateRepository.countByStage(Stage.OFFER_ACCEPTED);
        DocumentHeadcount waiting = countCandidatesWaiting();
        long offersAwaitingAcceptance = offerRepository.countByStatus(OfferStatus.SENT)
                + offerRepository.countByStatus(OfferStatus.VIEWED);

        Map<String, Long> stageBreakdown = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            stageBreakdown.put(stage.getCode(), candidateRepository.countByStage(stage));
        }

        return new DashboardStatsDto(
                activeCandidates,
                waiting.awaitingUpload(),
                waiting.awaitingReview(),
                verificationDone,
                onboardingComplete,
                totalCandidates,
                offersAwaitingAcceptance,
                stageBreakdown,
                candidateService.summaries(candidateRepository.findTop8ByOrderByCreatedAtDesc()),
                auditService.recent(12));
    }

    /**
     * How many *people* each document tile stands for. HR acts on candidates, not
     * on documents, so both counters are head-counts: one candidate with six
     * outstanding payslips is one name to chase, not six.
     *
     * <p>Only candidates still in the document stage can be waiting - once their
     * documents are approved there is nothing left to upload or review. The
     * progress numbers come from the same mapper the candidate table uses, so a
     * tile always agrees with the list it links to.
     */
    private DocumentHeadcount countCandidatesWaiting() {
        List<Candidate> pending = candidateRepository.findByStage(Stage.DOCS_PENDING);
        if (pending.isEmpty()) {
            return new DocumentHeadcount(0, 0);
        }
        List<UUID> ids = pending.stream().map(Candidate::getId).toList();
        Map<UUID, List<CandidateDocument>> byCandidate = documentRepository.findByCandidateIdIn(ids).stream()
                .collect(Collectors.groupingBy(doc -> doc.getCandidate().getId()));

        long awaitingUpload = 0;
        long awaitingReview = 0;
        for (Candidate candidate : pending) {
            DocumentProgressDto progress =
                    mapper.progress(candidate, byCandidate.getOrDefault(candidate.getId(), List.of()));
            if (progress.missing() + progress.rejected() > 0) {
                awaitingUpload++;
            }
            if (progress.submitted() > 0) {
                awaitingReview++;
            }
        }
        return new DocumentHeadcount(awaitingUpload, awaitingReview);
    }

    /**
     * @param awaitingUpload candidates with at least one document still to send
     *                       (never uploaded, or rejected and awaiting a replacement)
     * @param awaitingReview candidates with at least one document sitting in HR's queue
     */
    private record DocumentHeadcount(long awaitingUpload, long awaitingReview) {
    }
}
