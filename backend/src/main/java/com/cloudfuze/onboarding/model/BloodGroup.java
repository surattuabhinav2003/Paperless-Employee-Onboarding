package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum BloodGroup {

    A_POSITIVE("a_positive", "A+"),
    A_NEGATIVE("a_negative", "A-"),
    B_POSITIVE("b_positive", "B+"),
    B_NEGATIVE("b_negative", "B-"),
    AB_POSITIVE("ab_positive", "AB+"),
    AB_NEGATIVE("ab_negative", "AB-"),
    O_POSITIVE("o_positive", "O+"),
    O_NEGATIVE("o_negative", "O-"),
    UNKNOWN("unknown", "Not known");

    private final String code;
    private final String label;

    BloodGroup(String code, String label) {
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
    public static BloodGroup fromCode(String code) {
        return Arrays.stream(values())
                .filter(b -> b.code.equalsIgnoreCase(code) || b.name().equalsIgnoreCase(code)
                        || b.label.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown blood group: " + code));
    }
}
