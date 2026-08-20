package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.dto.PortalBondDto;
import com.cloudfuze.onboarding.dto.SignBondRequest;
import com.cloudfuze.onboarding.exception.SignatureProviderException;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureRequestHandle;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureStatusResult;
import com.cloudfuze.onboarding.signature.SignatureModels.SignedDocumentPayload;
import com.cloudfuze.onboarding.signature.SignatureModels.SignerConsent;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningRequestSpec;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningSession;
import com.cloudfuze.onboarding.signature.SignatureOneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the SignatureOne conversation for bond signing:
 * <pre>
 * prepare (tx) -> create request + session (provider) -> attach (tx)
 *              -> submit signature (provider)
 *              -> retrieve signed document + audit trail (provider)
 *              -> complete or record failure (tx)
 * </pre>
 * Deliberately not transactional: provider calls happen outside any database
 * transaction, and each persistence step commits on its own.
 */
@Service
public class BondSigningService {

    private static final Logger log = LoggerFactory.getLogger(BondSigningService.class);

    private final BondService bondService;
    private final SignatureOneService signatureService;

    public BondSigningService(BondService bondService, SignatureOneService signatureService) {
        this.bondService = bondService;
        this.signatureService = signatureService;
    }

    public PortalBondDto sign(Candidate candidate, String token, SignBondRequest request, String ipAddress,
                              String userAgent) {
        BondService.SigningPreparation preparation = bondService.prepareSigning(candidate);

        String requestId = preparation.existingSignatureRequestId();
        if (requestId == null) {
            SignatureRequestHandle handle = signatureService.createSigningRequest(new SigningRequestSpec(
                    preparation.candidateId(), preparation.candidateName(), preparation.candidateEmail(),
                    preparation.documentName(), preparation.contentType(), preparation.documentContent(),
                    preparation.documentVersion(), "candidate:" + preparation.candidateId()));
            requestId = handle.requestId();
            SigningSession session = signatureService.createSigningSession(requestId, null);
            bondService.attachSignatureRequest(preparation.bondId(), preparation.candidateId(),
                    preparation.candidateEmail(), requestId, session.signingUrl(), ipAddress);
        }

        String signerName = request.signerFullName().trim();
        SignatureStatusResult result;
        try {
            result = signatureService.submitSignature(requestId, new SignerConsent(signerName,
                    preparation.candidateEmail(), ipAddress, userAgent, request.consent()));
        } catch (RuntimeException e) {
            bondService.recordSignatureFailure(preparation.bondId(), preparation.candidateId(),
                    preparation.candidateEmail(), e.getMessage(), ipAddress);
            log.error("Signature submission failed for candidate {}: {}", preparation.candidateEmail(),
                    e.getMessage());
            throw e instanceof SignatureProviderException provider ? provider
                    : new SignatureProviderException("SignatureOne could not process the signature.", e);
        }

        if (!result.isSigned()) {
            String reason = result.message() == null
                    ? "The signature was not completed by the provider." : result.message();
            bondService.recordSignatureFailure(preparation.bondId(), preparation.candidateId(),
                    preparation.candidateEmail(), reason, ipAddress);
            throw new SignatureProviderException("SignatureOne could not complete the signature: " + reason);
        }

        SignedDocumentPayload signed;
        List<com.cloudfuze.onboarding.signature.SignatureModels.SignatureAuditEntry> trail;
        try {
            signed = signatureService.retrieveSignedDocument(requestId);
            trail = signatureService.getAuditTrail(requestId);
        } catch (RuntimeException e) {
            bondService.recordSignatureFailure(preparation.bondId(), preparation.candidateId(),
                    preparation.candidateEmail(),
                    "Signed document could not be retrieved: " + e.getMessage(), ipAddress);
            throw new SignatureProviderException(
                    "The signature completed but the signed document could not be retrieved. HR has been notified.",
                    e);
        }

        bondService.completeSigning(preparation.bondId(), preparation.candidateId(), result, signed, trail,
                signerName, ipAddress, userAgent);
        return bondService.portalViewById(preparation.candidateId(), token);
    }
}
