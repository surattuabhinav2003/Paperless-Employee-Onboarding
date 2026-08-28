package com.cloudfuze.onboarding.validation;

import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DistinctEmergencyContactValidator
        implements ConstraintValidator<DistinctEmergencyContact, CandidateProfileRequest> {

    @Override
    public boolean isValid(CandidateProfileRequest request, ConstraintValidatorContext context) {
        if (request == null || isBlank(request.emergencyContactNumber())) {
            // NotBlank on the field itself reports a missing value; nothing to
            // compare here.
            return true;
        }
        String emergency = digitsOnly(request.emergencyContactNumber());
        boolean clashesWithContact = emergency.equals(digitsOnly(request.contactNumber()));
        boolean clashesWithAlternate = emergency.equals(digitsOnly(request.alternateContactNumber()));

        if (!clashesWithContact && !clashesWithAlternate) {
            return true;
        }

        // Attach the violation to the specific field, not the whole request, so it
        // reaches the candidate as a normal field error next to the input.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        "The emergency contact number cannot be the same as your contact or alternate number")
                .addPropertyNode("emergencyContactNumber")
                .addConstraintViolation();
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
