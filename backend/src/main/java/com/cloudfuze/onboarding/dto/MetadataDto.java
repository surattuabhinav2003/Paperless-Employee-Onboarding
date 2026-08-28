package com.cloudfuze.onboarding.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Static reference data the frontend renders instead of hardcoding enums. */
public record MetadataDto(
        List<Option> documentTypes,
        List<Option> stages,
        List<Option> genders,
        List<Option> bloodGroups,
        List<Option> emergencyRelations,
        List<Option> educationCourses,
        List<Option> offerFieldTypes,
        List<Option> offerTextFonts,
        long maxUploadSizeBytes,
        List<String> allowedExtensions,
        /** Domains an NDA + NOC may be sent to. Empty means no restriction. */
        List<String> nocRecipientDomains,
        /** Which personal details to ask for, and which are required. */
        List<CandidateFieldDto> candidateFields,
        /** Extra detail fields an administrator created, in form order. */
        List<CustomCandidateFieldDto> customCandidateFields
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
