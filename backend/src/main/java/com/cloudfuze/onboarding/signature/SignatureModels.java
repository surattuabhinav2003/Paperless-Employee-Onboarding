package com.cloudfuze.onboarding.signature;

import java.time.Instant;
import java.util.UUID;

/** Value objects exchanged with the SignatureOne provider. */
public final class SignatureModels {

    private SignatureModels() {
    }

    /** Provider-side lifecycle of a signature request. */
    public enum SignatureState {
        CREATED,
        SENT,
        VIEWED,
        SIGNED,
        DECLINED,
        FAILED
    }

    /** Everything the provider needs to open a signing request. */
    public record SigningRequestSpec(
            UUID candidateId,
            String candidateName,
            String candidateEmail,
            String documentName,
            String documentContentType,
            byte[] documentContent,
            String documentVersion,
            String externalReference
    ) {
    }

    /** Handle returned when a signature request is created. */
    public record SignatureRequestHandle(
            String requestId,
            SignatureState state,
            Instant createdAt
    ) {
    }

    /** A short-lived signing session the candidate is sent to. */
    public record SigningSession(
            String requestId,
            String signingUrl,
            Instant expiresAt
    ) {
    }

    /** Signer intent captured in the portal before the provider is called. */
    public record SignerConsent(
            String signerName,
            String signerEmail,
            String ipAddress,
            String userAgent,
            boolean agreed
    ) {
    }

    public record SignatureStatusResult(
            String requestId,
            SignatureState state,
            Instant updatedAt,
            String signatureRef,
            String message
    ) {
        public boolean isSigned() {
            return state == SignatureState.SIGNED;
        }
    }

    public record SignedDocumentPayload(
            String requestId,
            String filename,
            String contentType,
            byte[] content,
            String sha256
    ) {
    }

    public record SignatureAuditEntry(
            String eventType,
            String actor,
            Instant occurredAt,
            String ipAddress,
            String detail
    ) {
    }
}
