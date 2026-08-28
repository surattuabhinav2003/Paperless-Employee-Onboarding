package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Typeface for a text field, limited to the three PDF standard families so the
 * stamped result never depends on a font being embedded or installed.
 */
public enum OfferTextFont {

    HELVETICA("helvetica", "Helvetica"),
    TIMES("times", "Times"),
    COURIER("courier", "Courier");

    private final String code;
    private final String label;

    OfferTextFont(String code, String label) {
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
    public static OfferTextFont fromCode(String code) {
        return Arrays.stream(values())
                .filter(f -> f.code.equalsIgnoreCase(code) || f.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown text font: " + code));
    }
}
