package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.DocumentDto;
import com.cloudfuze.onboarding.dto.RejectDocumentRequest;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.DocumentService;
import com.cloudfuze.onboarding.storage.FileStorageService;
import com.cloudfuze.onboarding.util.DownloadResponses;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Document review: verify, reject, and secure download for HR. */
@RestController
@RequestMapping("/api/hr/documents")
public class HrDocumentController {

    private final DocumentService documentService;
    private final FileStorageService storageService;
    private final AuditService auditService;

    public HrDocumentController(DocumentService documentService, FileStorageService storageService,
                                AuditService auditService) {
        this.documentService = documentService;
        this.storageService = storageService;
        this.auditService = auditService;
    }

    @PostMapping("/{documentId}/verify")
    public ResponseEntity<List<DocumentDto>> verify(@PathVariable UUID documentId,
                                                    @AuthenticationPrincipal HrPrincipal hrUser,
                                                    HttpServletRequest httpRequest) {
        UUID candidateId = documentService.verify(documentId, hrUser, RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(documentService.hrDocumentViewFor(candidateId));
    }

    @PostMapping("/{documentId}/reopen")
    public ResponseEntity<List<DocumentDto>> reopen(@PathVariable UUID documentId,
                                                    @AuthenticationPrincipal HrPrincipal hrUser,
                                                    HttpServletRequest httpRequest) {
        UUID candidateId = documentService.reopen(documentId, hrUser, RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(documentService.hrDocumentViewFor(candidateId));
    }

    @PostMapping("/{documentId}/reject")
    public ResponseEntity<List<DocumentDto>> reject(@PathVariable UUID documentId,
                                                    @Valid @RequestBody RejectDocumentRequest request,
                                                    @AuthenticationPrincipal HrPrincipal hrUser,
                                                    HttpServletRequest httpRequest) {
        UUID candidateId = documentService.reject(documentId, request.reason(), hrUser,
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(documentService.hrDocumentViewFor(candidateId));
    }

    @GetMapping("/{documentId}/file")
    public ResponseEntity<Resource> download(@PathVariable UUID documentId,
                                            @AuthenticationPrincipal HrPrincipal hrUser,
                                            HttpServletRequest httpRequest) {
        CandidateDocument document = documentService.requireDocument(documentId);
        Resource resource = storageService.load(document.getStorageKey());
        auditService.recordHrEvent(document.getCandidate().getId(), AuditEventType.DOCUMENT_DOWNLOADED,
                hrUser.getEmail(), RequestContext.clientIp(httpRequest),
                document.typeCode(),
                Map.of("filename", document.getOriginalFilename(), "version", document.getVersion()));
        return DownloadResponses.inline(resource, document.getOriginalFilename(), document.getContentType());
    }
}
