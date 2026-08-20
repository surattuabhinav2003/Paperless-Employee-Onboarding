package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Gender {

    MALE("male", "Male"),
    FEMALE("female", "Female"),
    OTHER("other", "Other"),
    PREFER_NOT_TO_SAY("prefer_not_to_say", "Prefer not to say");

    private final String code;
    private final String label;

    Gender(String code, String label) {
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
    public static Gender fromCode(String code) {
        return Arrays.stream(values())
                .filter(g -> g.code.equalsIgnoreCase(code) || g.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown gender: " + code));
    }
}
