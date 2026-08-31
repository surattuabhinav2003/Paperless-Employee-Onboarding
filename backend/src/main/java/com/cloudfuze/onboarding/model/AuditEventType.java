package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonValue;

/** Every business-significant event that can happen during onboarding. */
public enum AuditEventType {

    CANDIDATE_CREATED("candidate_created", "Candidate created"),
    CANDIDATE_UPDATED("candidate_updated", "Candidate details updated by HR"),
    PROFILE_CORRECTED("profile_corrected", "Personal details corrected by HR"),
    INVITATION_GENERATED("invitation_generated", "Invitation generated"),
    INVITATION_RESENT("invitation_resent", "Invitation resent"),
    PORTAL_TOKEN_REGENERATED("portal_token_regenerated", "Portal token regenerated"),
    PORTAL_LINK_VIEWED("portal_link_viewed", "Portal link viewed by HR"),
    PORTAL_ACCESSED("portal_accessed", "Candidate portal accessed"),
    VERIFICATION_CODE_SENT("verification_code_sent", "Verification code sent"),
    VERIFICATION_SUCCEEDED("verification_succeeded", "Candidate verified their email"),
    VERIFICATION_FAILED("verification_failed", "Wrong verification code entered"),
    PROFILE_SUBMITTED("profile_submitted", "Personal details submitted"),
    PROFILE_UPDATED("profile_updated", "Personal details updated"),
    SUBMITTED_FOR_REVIEW("submitted_for_review", "Submitted to HR for review"),
    DOCUMENT_UPLOADED("document_uploaded", "Document uploaded"),
    DOCUMENT_REUPLOADED("document_reuploaded", "Document re-uploaded"),
    DOCUMENT_VERIFIED("document_verified", "Document verified"),
    DOCUMENT_REJECTED("document_rejected", "Document rejected"),
    DOCUMENT_DOWNLOADED("document_downloaded", "Document downloaded"),
    DOCUMENT_REOPENED("document_reopened", "Document reopened for re-review"),
    DOCUMENTS_APPROVED("documents_approved", "Candidate approved by HR"),
    CANDIDATE_NOTIFIED("candidate_notified", "Candidate notified of review outcome"),
    REQUIRED_DOCUMENT_ADDED("required_document_added", "Document requirement added"),
    REQUIRED_DOCUMENT_MANDATORY_CHANGED("required_document_mandatory_changed",
            "Document requirement mandatory flag changed"),
    OFFER_UPLOADED("offer_uploaded", "Offer letter uploaded"),
    OFFER_SIGNATURE_FIELDS_SET("offer_signature_fields_set", "Signature fields placed on offer letter"),
    OFFER_SENT("offer_sent", "Offer letter sent to candidate"),
    OFFER_VIEWED("offer_viewed", "Offer letter viewed"),
    OFFER_ACCEPTED("offer_accepted", "Offer letter accepted"),
    ONBOARDING_COMPLETED("onboarding_completed", "Onboarding completed"),

    /*
     * Deletions. Recorded against no candidate, because the candidate is the
     * thing being removed - the entry has to outlive the record it describes,
     * or removing someone would also remove the evidence that anyone did.
     */
    CANDIDATE_DELETED("candidate_deleted", "Candidate permanently deleted"),
    OFFER_DELETED("offer_deleted", "Offer letter permanently deleted"),
    NOC_DELETED("noc_deleted", "NDA + NOC permanently deleted");

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
