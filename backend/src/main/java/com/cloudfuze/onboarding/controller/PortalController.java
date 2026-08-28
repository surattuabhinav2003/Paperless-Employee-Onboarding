package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.CandidateProfileDto;
import com.cloudfuze.onboarding.dto.CandidateProfileRequest;
import com.cloudfuze.onboarding.dto.PortalDocumentsDto;
import com.cloudfuze.onboarding.dto.PortalOfferDto;
import com.cloudfuze.onboarding.dto.PortalOverviewDto;
import com.cloudfuze.onboarding.dto.SignOfferRequest;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.EducationCourse;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.service.DocumentService;
import com.cloudfuze.onboarding.service.OfferService;
import com.cloudfuze.onboarding.service.PortalService;
import com.cloudfuze.onboarding.storage.FileStorageService;
import com.cloudfuze.onboarding.util.DownloadResponses;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * The single candidate-facing surface. Authentication is the portal token in the
 * path; authorisation is the candidate stage, enforced in the service layer on
 * every call - including the file downloads.
 */
@RestController
@RequestMapping("/api/portal/{token}")
public class PortalController {

    private final PortalService portalService;
    private final DocumentService documentService;
    private final OfferService offerService;
    private final FileStorageService storageService;

    public PortalController(PortalService portalService, DocumentService documentService,
                            OfferService offerService,
                            FileStorageService storageService) {
        this.portalService = portalService;
        this.documentService = documentService;
        this.offerService = offerService;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<PortalOverviewDto> overview(@PathVariable String token, HttpServletRequest request) {
        return ResponseEntity.ok(portalService.overview(token, RequestContext.clientIp(request),
                RequestContext.userAgent(request)));
    }

    /** The candidate has checked everything and hands it to HR. */
    @PostMapping("/submit")
    public ResponseEntity<PortalOverviewDto> submitForReview(@PathVariable String token,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok(portalService.submitForReview(token, RequestContext.clientIp(request),
                RequestContext.userAgent(request)));
    }

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileDto> profile(@PathVariable String token) {
        CandidateProfileDto profile = portalService.profile(token);
        return profile == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(profile);
    }

    /** Create or update the candidate's personal and education details. */
    @PutMapping("/profile")
    public ResponseEntity<CandidateProfileDto> saveProfile(@PathVariable String token,
                                                           @Valid @RequestBody CandidateProfileRequest body,
                                                           HttpServletRequest request) {
        return ResponseEntity.ok(portalService.saveProfile(token, body, RequestContext.clientIp(request)));
    }

    @GetMapping("/documents")
    public ResponseEntity<PortalDocumentsDto> documents(@PathVariable String token) {
        return ResponseEntity.ok(portalService.documents(token));
    }

    @PostMapping(path = "/documents/{documentType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PortalDocumentsDto> upload(@PathVariable String token,
                                                     @PathVariable String documentType,
                                                     @RequestPart("file") MultipartFile file,
                                                     @RequestParam(name = "course", required = false)
                                                     EducationCourse course,
                                                     HttpServletRequest request) {
        return ResponseEntity.ok(portalService.uploadDocument(token, documentType, course, file,
                RequestContext.clientIp(request), RequestContext.userAgent(request)));
    }

    @GetMapping("/documents/{documentId}/file")
    public ResponseEntity<Resource> documentFile(@PathVariable String token, @PathVariable UUID documentId) {
        Candidate candidate = portalService.authenticate(token);
        CandidateDocument document = documentService.requireDocumentOfCandidate(documentId, candidate.getId());
        return DownloadResponses.inline(storageService.load(document.getStorageKey()),
                document.getOriginalFilename(), document.getContentType());
    }

    @GetMapping("/offer")
    public ResponseEntity<PortalOfferDto> offer(@PathVariable String token) {
        return ResponseEntity.ok(portalService.offer(token));
    }

    @PostMapping("/offer/view")
    public ResponseEntity<PortalOfferDto> markOfferViewed(@PathVariable String token, HttpServletRequest request) {
        return ResponseEntity.ok(portalService.markOfferViewed(token, RequestContext.clientIp(request),
                RequestContext.userAgent(request)));
    }

    /** The candidate completes every placed field; the answers are stamped into the PDF. */
    @PostMapping("/offer/sign")
    public ResponseEntity<PortalOfferDto> signOffer(@PathVariable String token,
                                                    @Valid @RequestBody SignOfferRequest body,
                                                    HttpServletRequest request) {
        return ResponseEntity.ok(portalService.signOffer(token, body.fieldValues(),
                RequestContext.clientIp(request), RequestContext.userAgent(request)));
    }

    @GetMapping("/offer/file")
    public ResponseEntity<Resource> offerFile(@PathVariable String token, HttpServletRequest request) {
        Candidate candidate = portalService.authenticate(token);
        Offer offer = offerService.requireOfferForCandidateDownload(candidate,
                RequestContext.clientIp(request));
        // Once signed, this is the document that carries the candidate's signature.
        String key = offer.getSignedStorageKey() != null ? offer.getSignedStorageKey() : offer.getStorageKey();
        return DownloadResponses.inline(storageService.load(key), offer.getOriginalFilename(),
                offer.getContentType());
    }
}
