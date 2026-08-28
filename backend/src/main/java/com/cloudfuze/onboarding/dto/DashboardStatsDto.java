package com.cloudfuze.onboarding.dto;

import java.util.List;
import java.util.Map;

/**
 * Top-of-dashboard counters.
 *
 * <p>Every counter is a head-count. HR chases people, not paperwork, so a
 * candidate with six outstanding payslips counts once - and each number is the
 * length of the candidate list the tile links to.
 *
 * @param activeCandidates  candidates who have not finished onboarding
 * @param documentsPending  candidates with at least one document still to send
 *                          (never uploaded, or rejected and awaiting re-upload)
 * @param awaitingReview    candidates with at least one document in HR's queue
 * @param verificationDone  candidates HR has approved, now due an offer
 * @param onboardingComplete candidates who have signed their offer letter
 */
public record DashboardStatsDto(
        long activeCandidates,
        long documentsPending,
        long awaitingReview,
        long verificationDone,
        long onboardingComplete,
        long totalCandidates,
        long offersAwaitingAcceptance,
        Map<String, Long> stageBreakdown,
        List<CandidateSummaryDto> recentCandidates,
        List<AuditLogDto> recentActivity
) {
}
