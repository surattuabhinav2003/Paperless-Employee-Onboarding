package com.cloudfuze.onboarding.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces the fields an administrator has marked required.
 *
 * <p>Requiredness cannot be a per-field annotation because it changes at
 * runtime. Doing it here rather than in the service means these errors arrive
 * in the same response as the format errors, so a candidate who both mistyped
 * an email and skipped a field sees both at once instead of fixing one, saving,
 * and being told about the other.
 */
@Documented
@Constraint(validatedBy = RequiredCandidateFieldsValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredCandidateFields {

    String message() default "Some required details are missing";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
