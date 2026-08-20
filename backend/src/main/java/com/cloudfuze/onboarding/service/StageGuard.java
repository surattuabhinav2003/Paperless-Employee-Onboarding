package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.exception.StageForbiddenException;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Stage;
import org.springframework.stereotype.Component;

/**
 * Server-side enforcement of the gated workflow. Every candidate-facing
 * operation passes through one of these checks, so hiding or unhiding things in
 * React has no effect on what is actually permitted.
 *
 * <pre>
 * docs_pending -> docs_approved -> offer_accepted -> bond_signed
 * </pre>
 */
@Component
public class StageGuard {

    /** Uploads are only open while documents are still under review. */
    public void requireDocumentUploadOpen(Candidate candidate) {
        if (candidate.getStage() != Stage.DOCS_PENDING) {
            throw StageForbiddenException.documentsClosed(candidate.getStage());
        }
    }

    /** Reading the offer requires every mandatory document to be verified. */
    public void requireOfferAccess(Candidate candidate) {
        if (!candidate.getStage().isAtLeast(Stage.DOCS_APPROVED)) {
            throw StageForbiddenException.offerLocked(candidate.getStage());
        }
    }

    /** Accepting the offer is only valid in exactly the docs_approved stage. */
    public void requireOfferAcceptable(Candidate candidate) {
        if (candidate.getStage().isAtLeast(Stage.OFFER_ACCEPTED)) {
            throw new BusinessRuleException("OFFER_ALREADY_ACCEPTED",
                    "This offer has already been accepted. You can continue with bond signing.");
        }
        requireOfferAccess(candidate);
    }

    /** Reading the bond requires an accepted offer. */
    public void requireBondAccess(Candidate candidate) {
        if (!candidate.getStage().isAtLeast(Stage.OFFER_ACCEPTED)) {
            throw StageForbiddenException.bondLocked(candidate.getStage());
        }
    }

    /** Signing is only valid once, in exactly the offer_accepted stage. */
    public void requireBondSignable(Candidate candidate) {
        if (candidate.getStage() == Stage.BOND_SIGNED) {
            throw new BusinessRuleException("BOND_ALREADY_SIGNED",
                    "Your bond has already been signed. Onboarding is complete.");
        }
        requireBondAccess(candidate);
    }

    /** HR document decisions are only meaningful while documents are pending. */
    public void requireDocumentReviewOpen(Candidate candidate) {
        if (candidate.getStage() != Stage.DOCS_PENDING) {
            throw new BusinessRuleException("DOCUMENT_REVIEW_CLOSED",
                    "Documents for this candidate are already approved, so they can no longer be re-reviewed.");
        }
    }
}
