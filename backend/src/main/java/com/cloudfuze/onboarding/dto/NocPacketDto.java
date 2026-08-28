package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.NocStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One NDA + NOC packet as HR sees it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NocPacketDto(
        UUID id,
        String recipientName,
        String recipientEmail,
        String title,
        NocStatus status,
        String statusLabel,
        String ndaFilename,
        String nocFilename,
        int pageCount,
        long sizeBytes,
        List<OfferFieldDto> fields,
        boolean signedCopyAvailable,
        Instant sentAt,
        Instant viewedAt,
        Instant signedAt,
        String signedByName,
        Instant tokenExpiresAt,
        boolean linkActive,
        String createdBy,
        Instant createdAt,
        /** Download path for the merged (or signed) document. */
        String downloadUrl,

        /**
         * The recipient signing link. Surfaced exactly once, in the response to
         * sending, so HR can copy it; never stored raw and never returned again.
         */
        String signingUrl
) {
}
