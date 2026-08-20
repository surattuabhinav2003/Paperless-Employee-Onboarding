package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException candidate(Object id) {
        return new ResourceNotFoundException("Candidate not found: " + id);
    }

    public static ResourceNotFoundException document(Object id) {
        return new ResourceNotFoundException("Document not found: " + id);
    }
}
