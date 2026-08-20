package com.cloudfuze.onboarding.signature;

import com.cloudfuze.onboarding.config.SignatureOneProperties;
import com.cloudfuze.onboarding.exception.SignatureProviderException;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureAuditEntry;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureRequestHandle;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureState;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureStatusResult;
import com.cloudfuze.onboarding.signature.SignatureModels.SignedDocumentPayload;
import com.cloudfuze.onboarding.signature.SignatureModels.SignerConsent;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningRequestSpec;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development provider that mirrors the SignatureOne contract in memory so the
 * complete bond flow is testable without credentials. It is a real
 * implementation of the interface - the rest of the application cannot tell the
 * difference, and no mock logic exists outside this class.
 * <p>
 * The mock returns the original bond bytes as the signed artefact and records the
 * signer, timestamp, IP and a deterministic signature reference. A production
 * provider would return a re-rendered PDF carrying the visible signature block.
 */
@Service
@ConditionalOnProperty(name = "signatureone.provider", havingValue = "mock", matchIfMissing = true)
public class MockSignatureOneProvider implements SignatureOneService {

    private static final Logger log = LoggerFactory.getLogger(MockSignatureOneProvider.class);

    private final SignatureOneProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Envelope> envelopes = new ConcurrentHashMap<>();

    public MockSignatureOneProvider(SignatureOneProperties properties) {
        this.properties = properties;
        log.warn("SignatureOne is running with the MOCK provider. Set signatureone.provider=http "
                + "with real credentials before production use.");
    }

    @Override
    public SignatureRequestHandle createSigningRequest(SigningRequestSpec spec) {
        String requestId = "so1_mock_" + randomId();
        Envelope envelope = new Envelope(requestId, spec);
        envelope.state = SignatureState.SENT;
        envelope.audit.add(new SignatureAuditEntry("request_created", "SignatureOne", Instant.now(), null,
                "Signature request created for " + spec.candidateEmail() + " (document " + spec.documentVersion() + ")"));
        envelopes.put(requestId, envelope);
        return new SignatureRequestHandle(requestId, envelope.state, envelope.createdAt);
    }

    @Override
    public SigningSession createSigningSession(String requestId, String returnUrl) {
        Envelope envelope = require(requestId);
        String sessionToken = randomId();
        envelope.sessionToken = sessionToken;
        envelope.audit.add(new SignatureAuditEntry("session_created", "SignatureOne", Instant.now(), null,
                "Signing session issued"));
        String url = properties.getMock().getSigningBaseUrl() + "/" + requestId + "?session=" + sessionToken;
        return new SigningSession(requestId, url, Instant.now().plus(Duration.ofHours(2)));
    }

    @Override
    public SignatureStatusResult submitSignature(String requestId, SignerConsent consent) {
        Envelope envelope = require(requestId);
        if (envelope.state == SignatureState.SIGNED) {
            return status(envelope, "Document was already signed");
        }
        if (!consent.agreed()) {
            envelope.state = SignatureState.DECLINED;
            envelope.audit.add(new SignatureAuditEntry("signature_declined", consent.signerName(), Instant.now(),
                    consent.ipAddress(), "Signer did not agree to the bond terms"));
            return status(envelope, "Signer declined the document");
        }

        envelope.audit.add(new SignatureAuditEntry("document_viewed", consent.signerName(), Instant.now(),
                consent.ipAddress(), "Signer opened the bond document"));

        if (!properties.getMock().isCompleteOnSubmit()) {
            envelope.state = SignatureState.VIEWED;
            return status(envelope, "Awaiting provider completion callback");
        }

        envelope.state = SignatureState.SIGNED;
        envelope.signedAt = Instant.now();
        envelope.signerName = consent.signerName();
        envelope.signatureRef = "SIG-" + requestId.substring(requestId.length() - 8).toUpperCase()
                + "-" + Long.toHexString(envelope.signedAt.toEpochMilli()).toUpperCase();
        envelope.audit.add(new SignatureAuditEntry("signature_applied", consent.signerName(), envelope.signedAt,
                consent.ipAddress(), "Electronic signature applied via " + safe(consent.userAgent())));
        envelope.audit.add(new SignatureAuditEntry("document_sealed", "SignatureOne", envelope.signedAt, null,
                "Signed document sealed with reference " + envelope.signatureRef));
        return status(envelope, "Signed");
    }

    @Override
    public SignatureStatusResult getStatus(String requestId) {
        return status(require(requestId), null);
    }

    @Override
    public SignedDocumentPayload retrieveSignedDocument(String requestId) {
        Envelope envelope = require(requestId);
        if (envelope.state != SignatureState.SIGNED) {
            throw new SignatureProviderException("Signed document is not available until signing completes.");
        }
        byte[] content = envelope.spec.documentContent();
        String filename = "signed-" + envelope.spec.documentName();
        return new SignedDocumentPayload(requestId, filename, envelope.spec.documentContentType(), content,
                sha256(content));
    }

    @Override
    public List<SignatureAuditEntry> getAuditTrail(String requestId) {
        return Collections.unmodifiableList(require(requestId).audit);
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private SignatureStatusResult status(Envelope envelope, String message) {
        return new SignatureStatusResult(envelope.requestId, envelope.state,
                envelope.signedAt == null ? Instant.now() : envelope.signedAt, envelope.signatureRef, message);
    }

    private Envelope require(String requestId) {
        Envelope envelope = envelopes.get(requestId);
        if (envelope == null) {
            throw new SignatureProviderException("Unknown signature request: " + requestId);
        }
        return envelope;
    }

    private String randomId() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "web portal" : value;
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content == null ? new byte[0] : content);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureProviderException("SHA-256 unavailable", e);
        }
    }

    private static final class Envelope {
        private final String requestId;
        private final SigningRequestSpec spec;
        private final Instant createdAt = Instant.now();
        private final List<SignatureAuditEntry> audit = new ArrayList<>();
        private SignatureState state = SignatureState.CREATED;
        private String sessionToken;
        private String signatureRef;
        private String signerName;
        private Instant signedAt;

        private Envelope(String requestId, SigningRequestSpec spec) {
            this.requestId = requestId;
            this.spec = new SigningRequestSpec(spec.candidateId(), spec.candidateName(), spec.candidateEmail(),
                    spec.documentName(), spec.documentContentType(),
                    spec.documentContent() == null ? new byte[0] : spec.documentContent().clone(),
                    spec.documentVersion(), spec.externalReference());
        }
    }
}
