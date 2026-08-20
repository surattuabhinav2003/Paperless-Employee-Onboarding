package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * PENDING is never persisted. It is the derived status of a document HR has
 * requested but the candidate has not uploaded yet.
 */
public enum DocumentStatus {

    PENDING("pending", "Not uploaded"),
    SUBMITTED("submitted", "Awaiting review"),
    VERIFIED("verified", "Verified"),
    REJECTED("rejected", "Rejected");

    private final String code;
    private final String label;

    DocumentStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static DocumentStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown document status: " + code));
    }
}
