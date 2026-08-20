package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ActorType {

    HR("hr"),
    CANDIDATE("candidate"),
    SYSTEM("system"),
    SIGNATURE_PROVIDER("signature_provider");

    private final String code;

    ActorType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }
}
