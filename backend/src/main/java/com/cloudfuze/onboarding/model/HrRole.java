package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * What an HR user is allowed to do.
 *
 * <p>Two levels on purpose. Everyone who signs in can run the onboarding
 * workflow; only an admin can change the shape of it - the document catalogue,
 * which details candidates are asked for, and who else becomes an admin.
 * Configuration changes affect every future candidate, so they are worth
 * gating separately from day-to-day work.
 */
public enum HrRole {

    HR("hr", "HR"),
    ADMIN("admin", "Administrator");

    private final String code;
    private final String label;

    HrRole(String code, String label) {
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

    public boolean isAdmin() {
        return this == ADMIN;
    }

    @JsonCreator
    public static HrRole fromCode(String code) {
        return Arrays.stream(values())
                .filter(r -> r.code.equalsIgnoreCase(code) || r.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown HR role: " + code));
    }
}
