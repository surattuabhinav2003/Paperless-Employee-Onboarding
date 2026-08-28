package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Who the candidate's emergency contact is to them. */
public enum EmergencyContactRelation {

    FATHER("father", "Father"),
    MOTHER("mother", "Mother"),
    SPOUSE("spouse", "Spouse"),
    SON("son", "Son"),
    DAUGHTER("daughter", "Daughter"),
    BROTHER("brother", "Brother"),
    SISTER("sister", "Sister"),
    GUARDIAN("guardian", "Guardian"),
    RELATIVE("relative", "Other relative"),
    FRIEND("friend", "Friend"),
    OTHER("other", "Other");

    private final String code;
    private final String label;

    EmergencyContactRelation(String code, String label) {
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
    public static EmergencyContactRelation fromCode(String code) {
        return Arrays.stream(values())
                .filter(r -> r.code.equalsIgnoreCase(code) || r.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown relation: " + code));
    }
}
