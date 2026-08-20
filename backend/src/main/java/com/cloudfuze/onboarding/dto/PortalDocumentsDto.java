package com.cloudfuze.onboarding.dto;

import java.util.List;

public record PortalDocumentsDto(
        boolean uploadAllowed,
        String message,
        DocumentProgressDto progress,
        List<DocumentDto> documents,
        long maxFileSizeBytes,
        List<String> allowedExtensions
) {
}
