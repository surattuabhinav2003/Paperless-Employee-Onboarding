package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Every business-significant event that can happen during onboarding. */
public enum AuditEventType {

    CANDIDATE_CREATED("candidate_created", "Candidate created"),
    INVITATION_GENERATED("invitation_generated", "Invitation generated"),
    INVITATION_RESENT("invitation_resent", "Invitation resent"),
    PORTAL_TOKEN_REGENERATED("portal_token_regenerated", "Portal token regenerated"),
    PORTAL_LINK_VIEWED("portal_link_viewed", "Portal link viewed by HR"),
    PORTAL_ACCESSED("portal_accessed", "Candidate portal accessed"),
    PROFILE_SUBMITTED("profile_submitted", "Personal details submitted"),
    PROFILE_UPDATED("profile_updated", "Personal details updated"),
    SUBMITTED_FOR_REVIEW("submitted_for_review", "Submitted to HR for review"),
    DOCUMENT_UPLOADED("document_uploaded", "Document uploaded"),
    DOCUMENT_REUPLOADED("document_reuploaded", "Document re-uploaded"),
    DOCUMENT_VERIFIED("document_verified", "Document verified"),
    DOCUMENT_REJECTED("document_rejected", "Document rejected"),
    DOCUMENT_DOWNLOADED("document_downloaded", "Document downloaded"),
    DOCUMENTS_APPROVED("documents_approved", "All required documents approved"),
    OFFER_UPLOADED("offer_uploaded", "Offer letter uploaded"),
    OFFER_VIEWED("offer_viewed", "Offer letter viewed"),
    OFFER_ACCEPTED("offer_accepted", "Offer letter accepted"),
    BOND_UPLOADED("bond_uploaded", "Bond document uploaded"),
    BOND_UNLOCKED("bond_unlocked", "Bond stage unlocked"),
    SIGNATURE_INITIATED("signature_initiated", "Signature session initiated"),
    BOND_SIGNED("bond_signed", "Bond signed"),
    SIGNATURE_FAILED("signature_failed", "Signature failed"),
    ONBOARDING_COMPLETED("onboarding_completed", "Onboarding completed");

    private final String code;
    private final String label;

    AuditEventType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
