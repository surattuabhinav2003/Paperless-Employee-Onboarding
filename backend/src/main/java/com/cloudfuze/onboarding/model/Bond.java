package com.cloudfuze.onboarding.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The employment bond, signed through SignatureOne as the final onboarding step. */
@Entity
@Table(name = "bonds", indexes = {
        @Index(name = "idx_bonds_candidate", columnList = "candidate_id", unique = true),
        @Index(name = "idx_bonds_status", columnList = "status"),
        @Index(name = "idx_bonds_signature_ref", columnList = "signature_request_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_bonds_candidate"))
    private Candidate candidate;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 260)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "document_version", nullable = false, length = 40)
    private String documentVersion = "v1.0";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30,
            columnDefinition = "varchar(30)")
    private BondStatus status = BondStatus.NOT_INITIATED;

    /** SignatureOne request identifier. */
    @Column(name = "signature_request_id", length = 120)
    private String signatureRequestId;

    /** SignatureOne signature/envelope reference returned once signing completes. */
    @Column(name = "signature_ref", length = 160)
    private String signatureRef;

    @Column(name = "signing_url", length = 600)
    private String signingUrl;

    @Column(name = "signed_document_key", length = 400)
    private String signedDocumentKey;

    @Column(name = "signed_document_hash", length = 128)
    private String signedDocumentHash;

    @Column(name = "signature_initiated_at")
    private Instant signatureInitiatedAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signer_name", length = 160)
    private String signerName;

    @Column(name = "signer_ip", length = 60)
    private String signerIp;

    @Column(name = "failure_reason", length = 600)
    private String failureReason;

    @Column(name = "uploaded_by", nullable = false, length = 180)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bond_audit_events",
            joinColumns = @JoinColumn(name = "bond_id", foreignKey = @ForeignKey(name = "fk_bond_audit_bond")),
            indexes = @Index(name = "idx_bond_audit_bond", columnList = "bond_id"))
    @OrderBy("occurredAt ASC")
    private List<BondAuditEvent> auditTrail = new ArrayList<>();

    public Bond(Candidate candidate) {
        this.candidate = candidate;
    }

    public void addAuditEvent(BondAuditEvent event) {
        this.auditTrail.add(event);
    }
}
