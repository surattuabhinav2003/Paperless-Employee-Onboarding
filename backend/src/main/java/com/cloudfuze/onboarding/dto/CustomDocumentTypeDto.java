package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.CustomDocumentType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** One admin-created document type, as the admin screen lists them. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomDocumentTypeDto(
        UUID id,
        String code,
        String label,
        String group,
        String groupLabel,
        String description,
        boolean enabled,
        boolean archived,
        int position
) {
    public static CustomDocumentTypeDto from(CustomDocumentType type) {
        return new CustomDocumentTypeDto(
                type.getId(),
                type.getCode(),
                type.getLabel(),
                type.getGroup().name().toLowerCase(),
                type.getGroup().getLabel(),
                type.getDescription(),
                type.isEnabled(),
                type.isArchived(),
                type.getPosition());
    }
}
