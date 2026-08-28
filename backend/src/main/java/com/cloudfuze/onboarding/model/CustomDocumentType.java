package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A document type an administrator invented, alongside the built-in catalogue.
 *
 * <p>{@link DocumentType} is a fixed enum because each of its values carries
 * behaviour - which education course applies, whether it is still offered. A
 * type created at runtime can carry none of that, so these are deliberately
 * plain: a label, a group to file them under, and nothing else.
 *
 * <p>The code is the permanent identity. It is derived from the label once, at
 * creation, and never changes - it is what every requirement and every uploaded
 * file is filed under, so renaming the type must not move them.
 */
@Entity
@Table(name = "custom_document_types", indexes = {
        @Index(name = "idx_custom_doc_type_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class CustomDocumentType {

    /** Codes the built-in catalogue already owns, including retired ones. */
    private static final List<String> RESERVED = Arrays.stream(DocumentType.values())
            .map(DocumentType::getCode)
            .toList();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 60, updatable = false)
    private String code;

    @Column(name = "label", nullable = false, length = 160)
    private String label;

    /** Which heading it appears under in the picker and on the checklist. */
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_group", nullable = false, length = 20)
    private DocumentType.Group group = DocumentType.Group.OTHER;

    /** Shown to the candidate under the upload slot. */
    @Column(name = "description", length = 300)
    private String description;

    /** Whether HR is offered it when choosing what to ask a candidate for. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /** Order within its group; ties break on creation time. */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Withdrawn from the picker but kept on record. A hard delete would strand
     * every requirement and uploaded file already filed under it, leaving
     * candidates with documents nothing can name.
     */
    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 180)
    private String createdBy;

    public CustomDocumentType(String code, String label, DocumentType.Group group) {
        this.code = code;
        this.label = label;
        this.group = group;
    }

    /**
     * Turns a label into a stable code. Anything colliding with a built-in type,
     * or left with nothing usable, is refused by the service rather than
     * silently mangled into something else.
     */
    public static String toCode(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public static boolean isReserved(String code) {
        return RESERVED.contains(code);
    }
}
