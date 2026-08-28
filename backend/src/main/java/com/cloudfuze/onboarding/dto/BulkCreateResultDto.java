package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * The outcome of a bulk invite, row by row.
 *
 * <p>One bad row does not sink the batch: each candidate is created in its own
 * transaction and reported individually, so HR can see exactly who went out and
 * who needs fixing rather than being told "something failed".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BulkCreateResultDto(
        int created,
        int failed,
        List<Row> rows
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(
            String email,
            String name,
            boolean success,
            /** Present when the candidate was created. */
            CandidateSummaryDto candidate,
            /** Whether the invitation email actually went out. */
            Boolean invitationSent,
            /** Machine-readable failure code, e.g. DUPLICATE_RESOURCE. */
            String errorCode,
            /** Human-readable reason this row failed. */
            String error
    ) {

        public static Row ok(CandidateSummaryDto candidate, boolean invitationSent) {
            return new Row(candidate.email(), candidate.name(), true, candidate, invitationSent, null, null);
        }

        public static Row failed(String email, String name, String code, String message) {
            return new Row(email, name, false, null, null, code, message);
        }
    }
}
