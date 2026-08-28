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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The offer letter HR prepares for a candidate. One offer per candidate. */
@Entity
@Table(name = "offers", indexes = {
        @Index(name = "idx_offers_candidate", columnList = "candidate_id", unique = true),
        @Index(name = "idx_offers_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_offers_candidate"))
    private Candidate candidate;

    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    /** Set once the candidate signs; the version with their signature stamped in. */
    @Column(name = "signed_storage_key", length = 400)
    private String signedStorageKey;

    /** Where HR placed fields on the PDF. Empty until prepared for signing. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "offer_fields",
            joinColumns = @JoinColumn(name = "offer_id", foreignKey = @ForeignKey(name = "fk_offer_fields_offer")),
            indexes = @Index(name = "idx_offer_fields_offer", columnList = "offer_id"))
    @OrderColumn(name = "field_order")
    private List<OfferField> fields = new ArrayList<>();

    /** True once at least one signature field is placed - required before sending. */
    public boolean hasSignatureField() {
        return fields.stream().anyMatch(f -> f.getType() == OfferFieldType.SIGNATURE);
    }

    @Column(name = "original_filename", nullable = false, length = 260)
    private String originalFilename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30,
            columnDefinition = "varchar(30)")
    private OfferStatus status = OfferStatus.DRAFT;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by_name", length = 160)
    private String acceptedByName;

    @Column(name = "accepted_from_ip", length = 60)
    private String acceptedFromIp;

    @Column(name = "uploaded_by", nullable = false, length = 180)
    private String uploadedBy;

    @Column(name = "notes", length = 1000)
    private String notes;

    public Offer(Candidate candidate) {
        this.candidate = candidate;
    }
}
