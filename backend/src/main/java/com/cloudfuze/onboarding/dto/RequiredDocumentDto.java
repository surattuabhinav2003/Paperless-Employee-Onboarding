package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.RequiredDocument;

public record RequiredDocumentDto(String type, String label, boolean mandatory) {

    public static RequiredDocumentDto from(RequiredDocument required) {
        return new RequiredDocumentDto(required.typeCode(), required.displayName(), required.isMandatory());
    }
}
