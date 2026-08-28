package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.CandidateFieldDto;
import com.cloudfuze.onboarding.dto.CreateHrUserRequest;
import com.cloudfuze.onboarding.dto.CustomCandidateFieldDto;
import com.cloudfuze.onboarding.dto.CustomDocumentTypeDto;
import com.cloudfuze.onboarding.dto.SaveCustomDocumentTypeRequest;
import com.cloudfuze.onboarding.dto.SaveCustomFieldRequest;
import com.cloudfuze.onboarding.dto.HrUserDto;
import com.cloudfuze.onboarding.dto.UpdateCandidateFieldRequest;
import com.cloudfuze.onboarding.model.CandidateField;
import com.cloudfuze.onboarding.dto.UpdateHrRoleRequest;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.CandidateFieldService;
import com.cloudfuze.onboarding.service.CustomCandidateFieldService;
import com.cloudfuze.onboarding.service.DocumentCatalogService;
import com.cloudfuze.onboarding.service.HrUserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Administrator-only settings.
 *
 * <p>Everything under {@code /api/hr/admin/**} requires the admin authority -
 * enforced in the security config, so a new endpoint added here is gated by
 * default rather than by remembering to annotate it.
 */
@RestController
@RequestMapping("/api/hr/admin")
public class HrAdminController {

    private final HrUserAdminService userAdminService;
    private final CandidateFieldService fieldService;
    private final CustomCandidateFieldService customFieldService;
    private final DocumentCatalogService documentCatalog;

    public HrAdminController(HrUserAdminService userAdminService, CandidateFieldService fieldService,
                             CustomCandidateFieldService customFieldService,
                             DocumentCatalogService documentCatalog) {
        this.userAdminService = userAdminService;
        this.fieldService = fieldService;
        this.customFieldService = customFieldService;
        this.documentCatalog = documentCatalog;
    }

    @GetMapping("/users")
    public ResponseEntity<List<HrUserDto>> users() {
        return ResponseEntity.ok(userAdminService.list());
    }

    /** Adds someone who has not signed in yet, with the role they should have. */
    @PostMapping("/users")
    public ResponseEntity<HrUserDto> addUser(@Valid @RequestBody CreateHrUserRequest request,
                                             @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.add(request, actor));
    }

    /** Grants or revokes admin for one user. */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<HrUserDto> setRole(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateHrRoleRequest request,
                                             @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(userAdminService.setRole(id, request.role(), actor));
    }

    /** Which personal details candidates are asked for. */
    @GetMapping("/candidate-fields")
    public ResponseEntity<List<CandidateFieldDto>> candidateFields() {
        return ResponseEntity.ok(fieldService.list());
    }

    @PutMapping("/candidate-fields/{field}")
    public ResponseEntity<List<CandidateFieldDto>> setCandidateField(
            @PathVariable CandidateField field,
            @Valid @RequestBody UpdateCandidateFieldRequest request,
            @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(fieldService.update(field, request.enabled(), request.required(), actor));
    }

    /* ---- admin-created fields --------------------------------------------
       Kept apart from the built-in list above: those are fixed columns that can
       only be switched on and off, these are created and removed at will. */

    @GetMapping("/custom-fields")
    public ResponseEntity<List<CustomCandidateFieldDto>> customFields() {
        return ResponseEntity.ok(customFieldService.listForAdmin());
    }

    @PostMapping("/custom-fields")
    public ResponseEntity<List<CustomCandidateFieldDto>> addCustomField(
            @Valid @RequestBody SaveCustomFieldRequest request,
            @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customFieldService.create(request, actor));
    }

    @PutMapping("/custom-fields/{id}")
    public ResponseEntity<List<CustomCandidateFieldDto>> editCustomField(
            @PathVariable UUID id,
            @Valid @RequestBody SaveCustomFieldRequest request,
            @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(customFieldService.update(id, request, actor));
    }

    /** Removes a field from the form, keeping every answer already given. */
    @DeleteMapping("/custom-fields/{id}")
    public ResponseEntity<List<CustomCandidateFieldDto>> removeCustomField(
            @PathVariable UUID id, @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(customFieldService.setArchived(id, true, actor));
    }

    @PutMapping("/custom-fields/{id}/restore")
    public ResponseEntity<List<CustomCandidateFieldDto>> restoreCustomField(
            @PathVariable UUID id, @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(customFieldService.setArchived(id, false, actor));
    }

    /* ---- admin-created document types ------------------------------------
       The built-in catalogue is a fixed enum whose values carry behaviour;
       these are plain types created and withdrawn at will. */

    @GetMapping("/document-types")
    public ResponseEntity<List<CustomDocumentTypeDto>> documentTypes() {
        return ResponseEntity.ok(documentCatalog.listForAdmin());
    }

    @PostMapping("/document-types")
    public ResponseEntity<List<CustomDocumentTypeDto>> addDocumentType(
            @Valid @RequestBody SaveCustomDocumentTypeRequest request,
            @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentCatalog.create(request, actor));
    }

    @PutMapping("/document-types/{id}")
    public ResponseEntity<List<CustomDocumentTypeDto>> editDocumentType(
            @PathVariable UUID id,
            @Valid @RequestBody SaveCustomDocumentTypeRequest request,
            @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(documentCatalog.update(id, request, actor));
    }

    /** Withdraws it from the picker, keeping every document already collected. */
    @DeleteMapping("/document-types/{id}")
    public ResponseEntity<List<CustomDocumentTypeDto>> removeDocumentType(
            @PathVariable UUID id, @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(documentCatalog.setArchived(id, true, actor));
    }

    @PutMapping("/document-types/{id}/restore")
    public ResponseEntity<List<CustomDocumentTypeDto>> restoreDocumentType(
            @PathVariable UUID id, @AuthenticationPrincipal HrPrincipal actor) {
        return ResponseEntity.ok(documentCatalog.setArchived(id, false, actor));
    }
}
