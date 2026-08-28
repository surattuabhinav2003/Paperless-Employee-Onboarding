package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (candidate, document type). Re-uploads bump {@link #version} and
 * reset the review fields rather than creating a second row.
 */
@Entity
@Table(name = "candidate_documents",
        uniqueConstraints = @UniqueConstraint(name = "uk_document_candidate_type", columnNames = {"candidate_id", "document_type"}),
        indexes = {
                @Index(name = "idx_documents_candidate", columnList = "candidate_id"),
                @Index(name = "idx_documents_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
public class CandidateDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, foreignKey = @ForeignKey(name = "fk_documents_candidate"))
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60,
            columnDefinition = "varchar(60)")
    private DocumentType documentType;

    /**
     * Which course this certificate is for - set only for document types that
     * ask, e.g. Diploma for a secondary certificate or B.Tech for a degree.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "education_course", length = 40, columnDefinition = "varchar(40)")
    private EducationCourse educationCourse;

    /** Opaque storage key. Never exposed to any client. */
    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 260)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30,
            columnDefinition = "varchar(30)")
    private DocumentStatus status = DocumentStatus.SUBMITTED;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "reviewed_by", length = 180)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 600)
    private String rejectReason;

    /** Set only for a type an administrator created; see RequiredDocument. */
    @jakarta.persistence.Column(name = "custom_type_code", length = 60)
    private String customTypeCode;

    /** What was uploaded, whichever kind of type it is. */
    public String typeCode() {
        return customTypeCode != null && !customTypeCode.isBlank()
                ? customTypeCode : documentType.getCode();
    }

    public CandidateDocument(Candidate candidate, String customTypeCode) {
        this(candidate, DocumentType.OTHER);
        this.customTypeCode = customTypeCode;
    }

    public CandidateDocument(Candidate candidate, DocumentType documentType) {
        this.candidate = candidate;
        this.documentType = documentType;
    }
}
