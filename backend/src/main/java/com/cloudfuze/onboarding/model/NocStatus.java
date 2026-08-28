package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Where a combined NDA + NOC packet has got to. */
public enum NocStatus {

    /** Uploaded and merged, fields possibly placed, not yet sent. */
    DRAFT("draft", "Draft"),
    SENT("sent", "Sent"),
    VIEWED("viewed", "Viewed"),
    SIGNED("signed", "Signed");

    private final String code;
    private final String label;

    NocStatus(String code, String label) {
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
    public static NocStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equalsIgnoreCase(code) || s.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown NOC status: " + code));
    }
}
