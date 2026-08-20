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

    public RequiredDocument(DocumentType documentType, boolean mandatory, String label) {
        this.documentType = documentType;
        this.mandatory = mandatory;
        this.label = label;
    }

    public String displayName() {
        return (label == null || label.isBlank()) ? documentType.getLabel() : label;
    }
}
