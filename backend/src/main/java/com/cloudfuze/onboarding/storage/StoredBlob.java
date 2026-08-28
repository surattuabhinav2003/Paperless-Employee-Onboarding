package com.cloudfuze.onboarding.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An uploaded or generated document, held in the database.
 *
 * <p>The key is the primary key rather than a surrogate id: the application
 * already treats it as the handle to a file, and matching that exactly means a
 * database-backed store and a disk-backed one are interchangeable without
 * migrating a single stored key.
 */
@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
public class StoredBlob {

    @Id
    @Column(name = "storage_key", nullable = false, updatable = false, length = 400)
    private String key;

    @Column(name = "original_filename", nullable = false, length = 400)
    private String originalFilename;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Hex digest of the stored bytes, kept for the audit trail. */
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    /*
     * Declared as bytea rather than left to @Lob. Hibernate maps @Lob byte[] to
     * "blob", which Postgres does not have and which H2 rejects outright even in
     * PostgreSQL mode - so the tests could not build the table at all. Naming the
     * type here makes the entity and V2__stored_files.sql say the same thing,
     * which is what ddl-auto=validate is checking for anyway.
     */
    @Column(name = "data", nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
