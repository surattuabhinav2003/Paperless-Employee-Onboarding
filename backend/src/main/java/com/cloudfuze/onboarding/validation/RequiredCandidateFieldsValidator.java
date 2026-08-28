package com.cloudfuze.onboarding.validation;

import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.service.CandidateFieldService;
import com.cloudfuze.onboarding.service.CustomCandidateFieldService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the administrator's field settings and reports a violation against each
 * required field that was left empty.
 *
 * <p>Spring injects the service here - constraint validators are Spring beans in
 * this application, which is what lets a runtime setting drive validation.
 */
public class RequiredCandidateFieldsValidator
        implements ConstraintValidator<RequiredCandidateFields, CandidateProfileRequest> {

    private final CandidateFieldService fieldService;
    private final CustomCandidateFieldService customFieldService;

    public RequiredCandidateFieldsValidator(CandidateFieldService fieldService,
                                            CustomCandidateFieldService customFieldService) {
        this.fieldService = fieldService;
        this.customFieldService = customFieldService;
    }

    @Override
    public boolean isValid(CandidateProfileRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        // Built-in and admin-created fields are gathered together so a
        // submission is judged once and the candidate sees every problem at
        // the same time, rather than fixing one set and discovering another.
        Map<String, String> missing = new LinkedHashMap<>(fieldService.missingRequired(request));
        missing.putAll(customFieldService.validate(request.customFields()));
        if (missing.isEmpty()) {
            return true;
        }

        // Attach each message to its own property so the response shape matches
        // an ordinary field error and the form highlights the right input.
        context.disableDefaultConstraintViolation();
        missing.forEach((property, message) -> context
                .buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation());
        return false;
    }
}
