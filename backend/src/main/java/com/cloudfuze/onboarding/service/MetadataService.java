package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.dto.MetadataDto;
import com.cloudfuze.onboarding.model.BloodGroup;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EmergencyContactRelation;
import com.cloudfuze.onboarding.model.Gender;
import com.cloudfuze.onboarding.model.OfferFieldType;
import com.cloudfuze.onboarding.model.OfferTextFont;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.Stage;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/** Reference data so the UI never hardcodes enum values or upload limits. */
@Service
public class MetadataService {

    private final AppProperties appProperties;
    private final CandidateFieldService candidateFieldService;
    private final CustomCandidateFieldService customCandidateFieldService;
    private final DocumentCatalogService documentCatalog;

    public MetadataService(AppProperties appProperties, CandidateFieldService candidateFieldService,
                           CustomCandidateFieldService customCandidateFieldService,
                           DocumentCatalogService documentCatalog) {
        this.appProperties = appProperties;
        this.candidateFieldService = candidateFieldService;
        this.customCandidateFieldService = customCandidateFieldService;
        this.documentCatalog = documentCatalog;
    }

    public MetadataDto metadata() {
        // Built-in and admin-created types in one list, so the picker does not
        // need to know the difference. Retired types stay readable on old
        // records but are never offered again.
        List<MetadataDto.Option> documentTypes = documentCatalog.selectable().stream()
                .map(type -> new MetadataDto.Option(type.code(), type.label(), type.group().getLabel()))
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
        List<MetadataDto.Option> emergencyRelations = Arrays.stream(EmergencyContactRelation.values())
                .map(relation -> new MetadataDto.Option(relation.getCode(), relation.getLabel()))
                .toList();
        List<MetadataDto.Option> educationCourses = Arrays.stream(EducationCourse.values())
                .map(course -> new MetadataDto.Option(course.getCode(), course.getLabel(),
                        course.level().getLabel()))
                .toList();

        List<MetadataDto.Option> offerFieldTypes = Arrays.stream(OfferFieldType.values())
                .map(type -> new MetadataDto.Option(type.getCode(), type.getLabel()))
                .toList();
        List<MetadataDto.Option> offerTextFonts = Arrays.stream(OfferTextFont.values())
                .map(font -> new MetadataDto.Option(font.getCode(), font.getLabel()))
                .toList();

        return new MetadataDto(documentTypes, stages, genders, bloodGroups, emergencyRelations, educationCourses,
                offerFieldTypes, offerTextFonts,
                appProperties.getUpload().getMaxFileSizeBytes(),
                appProperties.getUpload().getAllowedExtensions(),
                appProperties.getNocRecipientDomains(),
                candidateFieldService.list(),
                customCandidateFieldService.list());
    }
}
