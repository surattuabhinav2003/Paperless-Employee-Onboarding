package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.AddRequiredDocumentsRequest;
import com.cloudfuze.onboarding.dto.AuditLogDto;
import com.cloudfuze.onboarding.dto.BulkCreateCandidatesRequest;
import com.cloudfuze.onboarding.dto.BulkCreateResultDto;
import com.cloudfuze.onboarding.dto.CandidateCreatedDto;
import com.cloudfuze.onboarding.dto.CandidateDetailDto;
import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.dto.CandidateSummaryDto;
import com.cloudfuze.onboarding.dto.CreateCandidateRequest;
import com.cloudfuze.onboarding.dto.DashboardStatsDto;
import com.cloudfuze.onboarding.dto.DocumentDto;
import com.cloudfuze.onboarding.dto.InvitationDto;
import com.cloudfuze.onboarding.dto.PageResponse;
import com.cloudfuze.onboarding.dto.UpdateCandidateRequest;
import com.cloudfuze.onboarding.dto.UpdateMandatoryRequest;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.BulkCandidateService;
import com.cloudfuze.onboarding.service.CandidateService;
import com.cloudfuze.onboarding.service.DashboardService;
import com.cloudfuze.onboarding.service.CandidateProfileService;
import com.cloudfuze.onboarding.service.DocumentService;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** HR dashboard, pipeline and candidate lifecycle endpoints. */
@RestController
@RequestMapping("/api/hr")
public class HrCandidateController {

    private final CandidateService candidateService;
    private final DashboardService dashboardService;
    private final DocumentService documentService;
    private final CandidateProfileService profileService;
    private final BulkCandidateService bulkCandidateService;
    private final AuditService auditService;

    public HrCandidateController(CandidateService candidateService, DashboardService dashboardService,
                                 DocumentService documentService,
                                 CandidateProfileService profileService, BulkCandidateService bulkCandidateService,
                                 AuditService auditService) {
        this.candidateService = candidateService;
        this.dashboardService = dashboardService;
        this.documentService = documentService;
        this.profileService = profileService;
        this.bulkCandidateService = bulkCandidateService;
        this.auditService = auditService;
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsDto> stats() {
        return ResponseEntity.ok(dashboardService.stats());
    }

    @GetMapping("/candidates")
    public ResponseEntity<PageResponse<CandidateSummaryDto>> list(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "stage", required = false) Stage stage,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {
        return ResponseEntity.ok(candidateService.list(query, stage, page, size));
    }

    @PostMapping("/candidates")
    public ResponseEntity<CandidateCreatedDto> create(@Valid @RequestBody CreateCandidateRequest request,
                                                      @AuthenticationPrincipal HrPrincipal hrUser,
                                                      HttpServletRequest httpRequest) {
        CandidateCreatedDto created = candidateService.create(request, hrUser,
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Invites several candidates at once, all with the same document checklist.
     * Always 200: rows succeed or fail individually and the body reports which.
     */
    @PostMapping("/candidates/bulk")
    public ResponseEntity<BulkCreateResultDto> createBulk(@Valid @RequestBody BulkCreateCandidatesRequest request,
                                                          @AuthenticationPrincipal HrPrincipal hrUser,
                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(bulkCandidateService.createAll(request, hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    @GetMapping("/candidates/{id}")
    public ResponseEntity<CandidateDetailDto> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(candidateService.detail(id));
    }

    @GetMapping("/candidates/{id}/documents")
    public ResponseEntity<List<DocumentDto>> documents(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.hrDocumentView(candidateService.requireCandidate(id)));
    }

    @GetMapping("/candidates/{id}/profile")
    public ResponseEntity<CandidateProfileDto> profile(@PathVariable UUID id) {
        candidateService.requireCandidate(id);
        CandidateProfileDto profile = profileService.view(id);
        return profile == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(profile);
    }

    /** HR corrects the candidate's personal details on their behalf. */
    @PutMapping("/candidates/{id}/profile")
    public ResponseEntity<CandidateProfileDto> saveProfile(@PathVariable UUID id,
                                                           @Valid @RequestBody CandidateProfileRequest request,
                                                           @AuthenticationPrincipal HrPrincipal hrUser,
                                                           HttpServletRequest httpRequest) {
        Candidate candidate = candidateService.requireCandidate(id);
        return ResponseEntity.ok(profileService.saveAsHr(candidate, request, hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    /** HR corrects the name, role or department they entered at creation. */
    @PutMapping("/candidates/{id}")
    public ResponseEntity<CandidateSummaryDto> updateCandidate(@PathVariable UUID id,
                                                               @Valid @RequestBody UpdateCandidateRequest request,
                                                               @AuthenticationPrincipal HrPrincipal hrUser,
                                                               HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidateService.updateDetails(id, request, hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    /** Adds new document requirements to a candidate who has already been invited. */
    @PostMapping("/candidates/{id}/required-documents")
    public ResponseEntity<List<DocumentDto>> addRequiredDocuments(@PathVariable UUID id,
                                                                  @Valid @RequestBody AddRequiredDocumentsRequest request,
                                                                  @AuthenticationPrincipal HrPrincipal hrUser,
                                                                  HttpServletRequest httpRequest) {
        candidateService.addRequiredDocuments(id, request.requiredDocuments(), hrUser,
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(documentService.hrDocumentViewFor(id));
    }

    /** Toggles an existing requirement between mandatory and optional. */
    @PatchMapping("/candidates/{id}/required-documents/{type}")
    public ResponseEntity<List<DocumentDto>> updateRequiredDocumentMandatory(@PathVariable UUID id,
                                                                             @PathVariable String type,
                                                                             @Valid @RequestBody UpdateMandatoryRequest request,
                                                                             @AuthenticationPrincipal HrPrincipal hrUser,
                                                                             HttpServletRequest httpRequest) {
        candidateService.updateRequiredDocumentMandatory(id, type, request.mandatory(), hrUser,
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.ok(documentService.hrDocumentViewFor(id));
    }

    @GetMapping("/candidates/{id}/audit")
    public ResponseEntity<List<AuditLogDto>> audit(@PathVariable UUID id) {
        candidateService.requireCandidate(id);
        return ResponseEntity.ok(auditService.forCandidate(id));
    }

    /** The candidate's live portal link, so HR can copy it without invalidating it. */
    @GetMapping("/candidates/{id}/portal-link")
    public ResponseEntity<InvitationDto> portalLink(@PathVariable UUID id,
                                                    @AuthenticationPrincipal HrPrincipal hrUser,
                                                    HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidateService.portalLink(id, hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    @PostMapping("/candidates/{id}/resend-invite")
    public ResponseEntity<InvitationDto> resendInvite(@PathVariable UUID id,
                                                     @AuthenticationPrincipal HrPrincipal hrUser,
                                                     HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidateService.resendInvitation(id, hrUser,
                RequestContext.clientIp(httpRequest), false));
    }

    /** HR finished reviewing: email the candidate one summary of the outcome. */
    @PostMapping("/candidates/{id}/notify")
    public ResponseEntity<CandidateService.NotifyResult> notify(@PathVariable UUID id,
                                                                @AuthenticationPrincipal HrPrincipal hrUser,
                                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidateService.notifyReviewOutcome(id, hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    @PostMapping("/candidates/{id}/regenerate-token")
    public ResponseEntity<InvitationDto> regenerateToken(@PathVariable UUID id,
                                                        @AuthenticationPrincipal HrPrincipal hrUser,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidateService.resendInvitation(id, hrUser,
                RequestContext.clientIp(httpRequest), true));
    }
}
