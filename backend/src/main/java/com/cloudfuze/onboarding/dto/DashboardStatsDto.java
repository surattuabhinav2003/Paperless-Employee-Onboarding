package com.cloudfuze.onboarding.dto;

import java.util.List;
import java.util.Map;

/**
 * Top-of-dashboard counters.
 *
 * @param activeCandidates candidates who have not finished onboarding
 * @param documentsPending  requested documents still waiting on the candidate
 *                          (never uploaded, or rejected and awaiting re-upload)
 * @param awaitingReview    uploaded documents waiting for an HR decision
 * @param onboardingComplete candidates who have accepted their offer
 */
public record DashboardStatsDto(
        long activeCandidates,
        long documentsPending,
        long awaitingReview,
        long onboardingComplete,
        long totalCandidates,
        long offersAwaitingAcceptance,
        Map<String, Long> stageBreakdown,
        List<CandidateSummaryDto> recentCandidates,
        List<AuditLogDto> recentActivity
) {
}
