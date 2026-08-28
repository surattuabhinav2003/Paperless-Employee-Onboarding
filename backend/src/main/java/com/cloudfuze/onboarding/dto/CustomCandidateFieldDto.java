package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.CustomCandidateField;
import com.cloudfuze.onboarding.model.CustomFieldType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

/** One admin-created detail field, as both the admin screen and the candidate form read it. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomCandidateFieldDto(
        UUID id,
        String code,
        String label,
        CustomFieldType type,
        String typeLabel,
        List<String> options,
        String helpText,
        String group,
        String groupLabel,
        boolean enabled,
        boolean required,
        boolean archived,
        int position
) {
    public static CustomCandidateFieldDto from(CustomCandidateField field) {
        return new CustomCandidateFieldDto(
                field.getId(),
                field.getCode(),
                field.getLabel(),
                field.getType(),
                field.getType().getLabel(),
                field.optionList(),
                field.getHelpText(),
                field.getGroup().name().toLowerCase(),
                field.getGroup().getLabel(),
                field.isEnabled(),
                field.isRequired(),
                field.isArchived(),
                field.getPosition());
    }
}
