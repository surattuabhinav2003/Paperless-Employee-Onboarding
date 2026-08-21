package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * The single source of truth for what a candidate is allowed to do.
 * Order matters: {@link #ordinalIndex()} backs the "at least this far" checks
 * used by the stage guard. {@link #OFFER_ACCEPTED} is terminal - accepting the
 * offer completes onboarding.
 */
public enum Stage {

    DOCS_PENDING("docs_pending", "Documents Pending", 0),
    DOCS_APPROVED("docs_approved", "Documents Approved", 1),
    OFFER_ACCEPTED("offer_accepted", "Offer Accepted", 2);

    private final String code;
    private final String label;
    private final int index;

    Stage(String code, String label, int index) {
        this.code = code;
        this.label = label;
        this.index = index;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int ordinalIndex() {
        return index;
    }

    public boolean isAtLeast(Stage other) {
        return this.index >= other.index;
    }

    @JsonCreator
    public static Stage fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage: " + code));
    }
}
