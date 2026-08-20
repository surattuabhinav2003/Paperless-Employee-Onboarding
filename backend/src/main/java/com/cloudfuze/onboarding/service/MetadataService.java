package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.dto.MetadataDto;
import com.cloudfuze.onboarding.model.BloodGroup;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.Gender;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.signature.SignatureOneService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** Reference data so the UI never hardcodes enum values or upload limits. */
@Service
public class MetadataService {

    private final AppProperties appProperties;
    private final SignatureOneService signatureService;

    public MetadataService(AppProperties appProperties, SignatureOneService signatureService) {
        this.appProperties = appProperties;
        this.signatureService = signatureService;
    }

    public MetadataDto metadata() {
        // Retired document types stay readable on old records but are never offered again.
        List<MetadataDto.Option> documentTypes = DocumentType.selectableValues().stream()
                .map(type -> new MetadataDto.Option(type.getCode(), type.getLabel(), type.group().getLabel()))
                .toList();
        List<MetadataDto.Option> stages = Arrays.stream(Stage.values())
                .map(stage -> new MetadataDto.Option(stage.getCode(), stage.getLabel()))
                .toList();
        List<MetadataDto.Option> genders = Arrays.stream(Gender.values())
                .map(gender -> new MetadataDto.Option(gender.getCode(), gender.getLabel()))
                .toList();
        List<MetadataDto.Option> bloodGroups = Arrays.stream(BloodGroup.values())
                .map(group -> new MetadataDto.Option(group.getCode(), group.getLabel()))
                .toList();
        List<MetadataDto.Option> educationCourses = Arrays.stream(EducationCourse.values())
                .map(course -> new MetadataDto.Option(course.getCode(), course.getLabel(),
                        course.level().getLabel()))
                .toList();

        return new MetadataDto(documentTypes, stages, genders, bloodGroups, educationCourses,
                appProperties.getUpload().getMaxFileSizeBytes(),
                appProperties.getUpload().getAllowedExtensions(), signatureService.providerName());
    }
}
