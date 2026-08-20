package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A requested document plus whatever the candidate has submitted for it.
 * {@code downloadUrl} is a relative API path - raw storage keys never leave the
 * backend.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DocumentDto(
        UUID id,
        DocumentType type,
        String typeLabel,
        boolean mandatory,
        DocumentStatus status,
        String statusLabel,
        boolean requiresCourse,
        List<CourseOption> courseOptions,
        EducationCourse course,
        String courseLabel,
        String filename,
        Long sizeBytes,
        Integer version,
        Instant uploadedAt,
        String reviewedBy,
        Instant reviewedAt,
        String rejectReason,
        boolean uploadAllowed,
        String downloadUrl
) {
    /** A course the candidate may pick for this document. */
    public record CourseOption(String value, String label, String group) {

        public static CourseOption from(EducationCourse course) {
            return new CourseOption(course.getCode(), course.getLabel(), course.level().getLabel());
        }
    }
}
