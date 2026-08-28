package com.cloudfuze.onboarding.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The emergency contact number must not be a candidate's own contact number -
 * otherwise HR could not reach anyone else if the candidate is unreachable,
 * which defeats the point of an emergency contact.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DistinctEmergencyContactValidator.class)
public @interface DistinctEmergencyContact {

    String message() default "The emergency contact number must be different from your own contact numbers";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
