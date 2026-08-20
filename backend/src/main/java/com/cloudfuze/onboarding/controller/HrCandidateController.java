package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.audit.AuditService;
import com.cloudfuze.onboarding.dto.AuditLogDto;
import com.cloudfuze.onboarding.dto.CandidateCreatedDto;
import com.cloudfuze.onboarding.dto.CandidateDetailDto;
import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateSummaryDto;
import com.cloudfuze.onboarding.dto.CreateCandidateRequest;
import com.cloudfuze.onboarding.dto.DashboardStatsDto;
import com.cloudfuze.onboarding.dto.DocumentDto;
import com.cloudfuze.onboarding.dto.InvitationDto;
import com.cloudfuze.onboarding.dto.PageResponse;
import com.cloudfuze.onboarding.model.Stage;
import com.cloudfuze.onboarding.security.HrPrincipal;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final AuditService auditService;

    public HrCandidateController(CandidateService candidateService, DashboardService dashboardService,
                                 DocumentService documentService,
                                 CandidateProfileService profileService, AuditService auditService) {
        this.candidateService = candidateService;
        this.dashboardService = dashboardService;
        this.documentService = documentService;
        this.profileService = profileService;
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

    @PostMapping("/candidates/{id}/regenerate-token")
    public ResponseEntity<InvitationDto> regenerateToken(@PathVariable UUID id,
                                                        @AuthenticationPrincipal HrPrincipal hrUser,
                                                        HttpServletRequest httpRequest) {
        return ResponseEntity.ok(candidateService.resendInvitation(id, hrUser,
                RequestContext.clientIp(httpRequest), true));
    }
}
