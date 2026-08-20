package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BondStatus {

    NOT_INITIATED("not_initiated"),
    AWAITING_SIGNATURE("awaiting_signature"),
    SIGNED("signed"),
    FAILED("failed");

    private final String code;

    BondStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
