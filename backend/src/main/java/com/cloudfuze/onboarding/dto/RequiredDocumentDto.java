package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.RequiredDocument;

public record RequiredDocumentDto(DocumentType type, String label, boolean mandatory) {

    public static RequiredDocumentDto from(RequiredDocument required) {
        return new RequiredDocumentDto(required.getDocumentType(), required.displayName(), required.isMandatory());
    }
}
