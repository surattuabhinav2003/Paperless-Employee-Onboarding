package com.cloudfuze.onboarding.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The onboarding aggregate root. {@link #stage} is the authoritative gate for
 * everything the candidate portal is allowed to do.
 */
@Entity
@Table(name = "candidates", indexes = {
        @Index(name = "idx_candidates_email", columnList = "email", unique = true),
        @Index(name = "idx_candidates_stage", columnList = "stage"),
        @Index(name = "idx_candidates_token_hash", columnList = "invite_token_hash", unique = true),
        @Index(name = "idx_candidates_token_expiry", columnList = "token_expires_at"),
        @Index(name = "idx_candidates_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "job_role", nullable = false, length = 120)
    private String role;

    @Column(name = "department", nullable = false, length = 120)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 40,
            columnDefinition = "varchar(40)")
    private Stage stage = Stage.DOCS_PENDING;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "candidate_required_documents",
            joinColumns = @JoinColumn(name = "candidate_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_required_docs_candidate")),
            indexes = @Index(name = "idx_required_docs_candidate", columnList = "candidate_id"))
    private List<RequiredDocument> requiredDocuments = new ArrayList<>();

    /** SHA-256 hash of the portal token, used for lookups. */
    @Column(name = "invite_token_hash", length = 128)
    private String inviteTokenHash;

    /**
     * The same token encrypted, so HR can re-read a link that is still valid.
     * Useless without the key, which lives in configuration, not the database.
     */
    @Column(name = "invite_token_cipher", length = 400)
    private String inviteTokenCipher;

    @Column(name = "token_issued_at")
    private Instant tokenIssuedAt;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "invitation_sent_at")
    private Instant invitationSentAt;

    @Column(name = "invitation_count", nullable = false)
    private int invitationCount = 0;

    @Column(name = "last_portal_access_at")
    private Instant lastPortalAccessAt;

    /** Set when the candidate reviews everything and submits it to HR. */
    @Column(name = "submitted_for_review_at")
    private Instant submittedForReviewAt;

    @Column(name = "docs_approved_at")
    private Instant docsApprovedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, length = 180)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public List<RequiredDocument> mandatoryDocuments() {
        return requiredDocuments.stream().filter(RequiredDocument::isMandatory).toList();
    }

    public boolean requires(DocumentType type) {
        return requiredDocuments.stream().anyMatch(rd -> rd.getDocumentType() == type);
    }

    public boolean isSubmittedForReview() {
        return submittedForReviewAt != null;
    }

    public boolean isTokenActive(Instant now) {
        return inviteTokenHash != null && tokenExpiresAt != null && tokenExpiresAt.isAfter(now);
    }
}
