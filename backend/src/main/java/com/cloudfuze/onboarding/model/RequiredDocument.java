package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A document HR asked this particular candidate to provide. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class RequiredDocument {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 60,
            columnDefinition = "varchar(60)")
    private DocumentType documentType;

    /** Mandatory documents gate the move to docs_approved; optional ones do not. */
    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "label", length = 160)
    private String label;

    /**
     * Set only for a type an administrator created; null for a built-in one.
     *
     * <p>{@link #documentType} stays populated either way - it is a NOT NULL
     * column on a table with rows in it - so a custom requirement is stored as
     * OTHER plus the code that actually identifies it. Read
     * {@link #typeCode()} rather than either field.
     */
    @Column(name = "custom_type_code", length = 60)
    private String customTypeCode;

    public RequiredDocument(DocumentType documentType, boolean mandatory, String label) {
        this.documentType = documentType;
        this.mandatory = mandatory;
        this.label = label;
    }

    public RequiredDocument(String customTypeCode, boolean mandatory, String label) {
        this.documentType = DocumentType.OTHER;
        this.customTypeCode = customTypeCode;
        this.mandatory = mandatory;
        this.label = label;
    }

    /** What this requirement is for, whichever kind of type it is. */
    public String typeCode() {
        return customTypeCode != null && !customTypeCode.isBlank()
                ? customTypeCode : documentType.getCode();
    }

    public boolean isCustomType() {
        return customTypeCode != null && !customTypeCode.isBlank();
    }

    /**
     * The name to show. A custom type has no enum label to fall back on, so its
     * name is carried in {@link #label} - which is why that is written at
     * creation for custom requirements rather than left null.
     */
    public String displayName() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return isCustomType() ? customTypeCode : documentType.getLabel();
    }
}
