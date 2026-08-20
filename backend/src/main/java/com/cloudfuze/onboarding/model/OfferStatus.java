package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum OfferStatus {

    SENT("sent"),
    VIEWED("viewed"),
    ACCEPTED("accepted");

    private final String code;

    OfferStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
