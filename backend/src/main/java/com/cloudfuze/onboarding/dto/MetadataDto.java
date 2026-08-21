package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Static reference data the frontend renders instead of hardcoding enums. */
public record MetadataDto(
        List<Option> documentTypes,
        List<Option> stages,
        List<Option> genders,
        List<Option> bloodGroups,
        List<Option> educationCourses,
        long maxUploadSizeBytes,
        List<String> allowedExtensions
) {
    /**
     * @param group optional heading the option belongs under, e.g. the document
     *              category or the qualification level
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Option(String value, String label, String group) {

        public Option(String value, String label) {
            this(value, label, null);
        }
    }
}
