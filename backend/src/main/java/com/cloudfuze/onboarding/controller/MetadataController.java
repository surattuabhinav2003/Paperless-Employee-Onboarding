package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.MetadataDto;
import com.cloudfuze.onboarding.service.MetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public reference data (document types, stages, upload limits). */
@RestController
@RequestMapping("/api/meta")
public class MetadataController {

    private final MetadataService metadataService;

    public MetadataController(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping
    public ResponseEntity<MetadataDto> metadata() {
        return ResponseEntity.ok(metadataService.metadata());
    }
}
