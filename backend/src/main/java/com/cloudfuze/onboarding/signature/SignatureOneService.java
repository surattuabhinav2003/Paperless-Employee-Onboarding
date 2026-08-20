package com.cloudfuze.onboarding.signature;

import com.cloudfuze.onboarding.signature.SignatureModels.SignatureAuditEntry;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureRequestHandle;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureStatusResult;
import com.cloudfuze.onboarding.signature.SignatureModels.SignedDocumentPayload;
import com.cloudfuze.onboarding.signature.SignatureModels.SignerConsent;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningRequestSpec;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningSession;

import java.util.List;

/**
 * Integration boundary for SignatureOne. The bond service talks only to this
 * interface, so the mock provider used in development and the real HTTP client
 * are fully interchangeable - no provider conditionals leak into controllers or
 * business services.
 */
public interface SignatureOneService {

    /** Registers the bond document with the provider and returns its request id. */
    SignatureRequestHandle createSigningRequest(SigningRequestSpec spec);

    /** Creates a signing session/link the candidate is directed to. */
    SigningSession createSigningSession(String requestId, String returnUrl);

    /** Submits the signer intent captured in the portal. */
    SignatureStatusResult submitSignature(String requestId, SignerConsent consent);

    SignatureStatusResult getStatus(String requestId);

    SignedDocumentPayload retrieveSignedDocument(String requestId);

    List<SignatureAuditEntry> getAuditTrail(String requestId);

    String providerName();
}
