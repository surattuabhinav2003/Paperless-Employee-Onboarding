package com.cloudfuze.onboarding.config;

import com.cloudfuze.onboarding.model.DocumentStatus;
import com.cloudfuze.onboarding.model.CandidateField;
import com.cloudfuze.onboarding.model.HrRole;
import com.cloudfuze.onboarding.model.NocStatus;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.Stage;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets path variables and query parameters use the API-facing enum codes
 * ({@code docs_pending}, {@code pan_card}) instead of Java constant names.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Stage.class,
                source -> source == null || source.isBlank() ? null : Stage.fromCode(source));
        registry.addConverter(String.class, DocumentType.class,
                source -> source == null || source.isBlank() ? null : DocumentType.fromCode(source));
        registry.addConverter(String.class, EducationCourse.class,
                source -> source == null || source.isBlank() ? null : EducationCourse.fromCode(source));
        registry.addConverter(String.class, DocumentStatus.class,
                source -> source == null || source.isBlank() ? null : DocumentStatus.fromCode(source));
        registry.addConverter(String.class, CandidateField.class,
                source -> source == null || source.isBlank() ? null : CandidateField.fromCode(source));
        registry.addConverter(String.class, HrRole.class,
                source -> source == null || source.isBlank() ? null : HrRole.fromCode(source));
        registry.addConverter(String.class, NocStatus.class,
                source -> source == null || source.isBlank() ? null : NocStatus.fromCode(source));
    }
}
