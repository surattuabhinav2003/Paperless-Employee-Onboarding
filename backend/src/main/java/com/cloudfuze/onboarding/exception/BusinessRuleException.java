package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

/** A request that is well formed and authorised but violates a workflow rule. */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
