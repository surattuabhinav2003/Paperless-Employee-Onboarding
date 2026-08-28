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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
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
 * An NDA and an NOC merged into a single document, sent to one recipient to
 * sign in one pass.
 *
 * <p>The two source PDFs are combined at upload time and only the merged file
 * is ever shown, signed or returned - the recipient sees one document, and HR
 * gets one signed document back. Field coordinates therefore refer to pages of
 * the <em>merged</em> PDF, which is why the merge has to happen before HR places
 * anything.
 *
 * <p>Unlike an offer letter this is not tied to a {@link Candidate}: HR types in
 * whichever address they want to send to, so the recipient is held as plain name
 * and email.
 */
@Entity
@Table(name = "noc_packets", indexes = {
        @Index(name = "idx_noc_status", columnList = "status"),
        @Index(name = "idx_noc_token", columnList = "access_token_hash", unique = true),
        @Index(name = "idx_noc_recipient", columnList = "recipient_email")
})
@Getter
@Setter
@NoArgsConstructor
public class NocPacket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_name", nullable = false, length = 160)
    private String recipientName;

    @Column(name = "recipient_email", nullable = false, length = 180)
    private String recipientEmail;

    /** Optional label so a packet is recognisable in the list. */
    @Column(name = "title", length = 200)
    private String title;

    /** The merged NDA + NOC. The only file the recipient ever sees. */
    @Column(name = "storage_key", nullable = false, length = 400)
    private String storageKey;

    /** Set once signed: the merged document with the answers stamped in. */
    @Column(name = "signed_storage_key", length = 400)
    private String signedStorageKey;

    /* Kept for provenance - which two files were combined to make this. */
    @Column(name = "nda_filename", length = 260)
    private String ndaFilename;

    @Column(name = "noc_filename", length = 260)
    private String nocFilename;

    @Column(name = "page_count")
    private int pageCount;

    @Column(name = "size_bytes")
    private long sizeBytes;

    /**
     * Where HR placed the fields, in merged-document coordinates. The same
     * embeddable the offer letter uses, so the editor, the review dialog and the
     * stamping code are shared rather than duplicated.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "noc_fields",
            joinColumns = @JoinColumn(name = "noc_id", foreignKey = @ForeignKey(name = "fk_noc_fields_packet")),
            indexes = @Index(name = "idx_noc_fields_packet", columnList = "noc_id"))
    @OrderColumn(name = "position")
    private List<OfferField> fields = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NocStatus status = NocStatus.DRAFT;

    /* The recipient's link. The hash is what lookups match on; the cipher exists
       only so HR can be shown the link again later - the raw token is never
       stored, exactly as with a candidate's portal token. */
    @Column(name = "access_token_hash", length = 128)
    private String accessTokenHash;

    @Column(name = "access_token_cipher", length = 400)
    private String accessTokenCipher;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;


    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "viewed_at")
    private Instant viewedAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by_name", length = 160)
    private String signedByName;

    @Column(name = "created_by", length = 180)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isSigned() {
        return status == NocStatus.SIGNED;
    }

    public boolean hasSignatureField() {
        return fields.stream().anyMatch(f -> f.getType() != null && f.getType().isSignature());
    }

    public boolean isTokenActive(Instant at) {
        return accessTokenHash != null && tokenExpiresAt != null && tokenExpiresAt.isAfter(at);
    }
}
