package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** What the recipient sees when they open their link. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NocRecipientViewDto(
        String recipientName,
        String title,
        int pageCount,
        List<OfferFieldDto> fields,
        boolean signed,
        Instant signedAt,
        String signedByName,
        Instant expiresAt,
        /** Path to stream the document they are signing. */
        String documentUrl
) {
}
