package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BondStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BondDto(
        UUID id,
        BondStatus status,
        String documentVersion,
        String filename,
        long sizeBytes,
        String signatureRequestId,
        String signatureRef,
        String signingUrl,
        Instant signatureInitiatedAt,
        Instant signedAt,
        String signerName,
        String signerIp,
        String signedDocumentHash,
        String failureReason,
        String uploadedBy,
        Instant uploadedAt,
        String downloadUrl,
        String signedDownloadUrl,
        List<BondAuditEventDto> auditTrail
) {
}
