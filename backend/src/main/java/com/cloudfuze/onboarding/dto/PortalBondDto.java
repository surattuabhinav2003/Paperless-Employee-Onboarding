package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BondStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortalBondDto(
        boolean prepared,
        BondStatus status,
        String documentVersion,
        String filename,
        String downloadUrl,
        String signedDownloadUrl,
        String signatureProvider,
        String signatureRef,
        String signingUrl,
        Instant signedAt,
        String signerName,
        boolean canSign,
        String message,
        String candidateName,
        List<BondAuditEventDto> auditTrail
) {
}
