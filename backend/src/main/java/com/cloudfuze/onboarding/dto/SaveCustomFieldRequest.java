package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.CandidateField;
import com.cloudfuze.onboarding.model.CustomFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** An admin creating or editing one of their own detail fields. */
public record SaveCustomFieldRequest(
        @NotBlank(message = "Give the field a label")
        @Size(max = 120, message = "Label must be 120 characters or fewer")
        String label,

        @NotNull(message = "Choose what kind of answer this field takes")
        CustomFieldType type,

        /** One choice per line; only read for a select field. */
        @Size(max = 2000, message = "That is too many options")
        String options,

        @Size(max = 300, message = "Help text must be 300 characters or fewer")
        String helpText,

        CandidateField.Group group,

        Boolean enabled,
        Boolean required
) {
}
