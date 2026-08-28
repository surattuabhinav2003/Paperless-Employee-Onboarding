package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.BulkCreateCandidatesRequest;
import com.cloudfuze.onboarding.dto.BulkCreateResultDto;
import com.cloudfuze.onboarding.dto.CandidateCreatedDto;
import com.cloudfuze.onboarding.dto.CreateCandidateRequest;
import com.cloudfuze.onboarding.exception.ApiException;
import com.cloudfuze.onboarding.security.HrPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Inviting a batch of candidates who all need the same documents.
 *
 * <p>Deliberately its own bean rather than a method on {@link CandidateService}:
 * each row has to commit or fail on its own, and calling a {@code @Transactional}
 * method from inside the same bean would bypass the proxy and quietly run the
 * whole batch in one transaction - so a single duplicate email would roll back
 * every invitation that had already been sent.
 */
@Service
public class BulkCandidateService {

    private static final Logger log = LoggerFactory.getLogger(BulkCandidateService.class);

    private final CandidateService candidateService;

    public BulkCandidateService(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /**
     * Creates each candidate independently and reports the outcome row by row.
     * Never throws for a bad row - a batch where some rows fail is a normal
     * result HR needs to see, not an error.
     */
    public BulkCreateResultDto createAll(BulkCreateCandidatesRequest request, HrPrincipal hrUser,
                                         String ipAddress) {
        List<BulkCreateResultDto.Row> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int created = 0;

        for (BulkCreateCandidatesRequest.Entry entry : request.candidates()) {
            String email = entry.email() == null ? "" : entry.email().trim().toLowerCase();

            // Caught here rather than at the database, so the message names the
            // real problem: the same address typed twice in this batch.
            if (!seen.add(email)) {
                rows.add(BulkCreateResultDto.Row.failed(entry.email(), entry.name(), "DUPLICATE_IN_BATCH",
                        "This email appears more than once in the list."));
                continue;
            }

            try {
                CandidateCreatedDto result = candidateService.create(
                        new CreateCandidateRequest(entry.name(), entry.email(), entry.role(),
                                entry.department(), request.requiredDocuments()),
                        hrUser, ipAddress);
                rows.add(BulkCreateResultDto.Row.ok(result.candidate(),
                        result.invitation() != null && result.invitation().sentAt() != null));
                created++;
            } catch (ApiException e) {
                rows.add(BulkCreateResultDto.Row.failed(entry.email(), entry.name(), e.getCode(), e.getMessage()));
            } catch (RuntimeException e) {
                // One malformed row must not take the rest of the batch with it.
                log.error("Bulk invite failed for {}: {}", email, e.toString());
                rows.add(BulkCreateResultDto.Row.failed(entry.email(), entry.name(), "CANDIDATE_NOT_CREATED",
                        "Could not create this candidate. Please try again."));
            }
        }

        int failed = rows.size() - created;
        log.info("HR {} bulk-invited {} candidate(s): {} created, {} failed", hrUser.getEmail(),
                request.candidates().size(), created, failed);
        return new BulkCreateResultDto(created, failed, rows);
    }
}
