package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * The kinds of field HR can place on an offer letter for the candidate to
 * complete. {@link #SIGNATURE} carries an image; every other type carries text.
 */
public enum OfferFieldType {

    SIGNATURE("signature", "Signature"),
    NAME("name", "Name"),
    TITLE("title", "Title"),
    DATE("date", "Date"),
    TEXT("text", "Text");

    private final String code;
    private final String label;

    OfferFieldType(String code, String label) {
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

    /** True when the candidate's answer is drawn/uploaded rather than typed. */
    public boolean isSignature() {
        return this == SIGNATURE;
    }

    @JsonCreator
    public static OfferFieldType fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown offer field type: " + code));
    }
}
