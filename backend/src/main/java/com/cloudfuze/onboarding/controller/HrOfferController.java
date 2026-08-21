package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.OfferDto;
import com.cloudfuze.onboarding.exception.BusinessRuleException;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.Offer;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.CandidateService;
import com.cloudfuze.onboarding.service.OfferService;
import com.cloudfuze.onboarding.storage.FileStorageService;
import com.cloudfuze.onboarding.util.DownloadResponses;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/** Offer letter management for HR. */
@RestController
@RequestMapping("/api/hr/candidates/{candidateId}")
public class HrOfferController {

    private final CandidateService candidateService;
    private final OfferService offerService;
    private final FileStorageService storageService;

    public HrOfferController(CandidateService candidateService, OfferService offerService,
                             FileStorageService storageService) {
        this.candidateService = candidateService;
        this.offerService = offerService;
        this.storageService = storageService;
    }

    @PostMapping(path = "/offer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OfferDto> uploadOffer(@PathVariable UUID candidateId,
                                                @RequestPart("file") MultipartFile file,
                                                @RequestParam(name = "notes", required = false) String notes,
                                                @AuthenticationPrincipal HrPrincipal hrUser,
                                                HttpServletRequest httpRequest) {
        Candidate candidate = candidateService.requireCandidate(candidateId);
        return ResponseEntity.ok(offerService.upload(candidate, file, notes, hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    @GetMapping("/offer")
    public ResponseEntity<OfferDto> offer(@PathVariable UUID candidateId) {
        candidateService.requireCandidate(candidateId);
        OfferDto offer = offerService.hrView(candidateId);
        return offer == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(offer);
    }

    @GetMapping("/offer/file")
    public ResponseEntity<Resource> offerFile(@PathVariable UUID candidateId) {
        candidateService.requireCandidate(candidateId);
        Offer offer = offerService.find(candidateId)
                .orElseThrow(() -> new BusinessRuleException("OFFER_NOT_AVAILABLE",
                        "No offer letter has been uploaded for this candidate yet."));
        return DownloadResponses.inline(storageService.load(offer.getStorageKey()),
                offer.getOriginalFilename(), offer.getContentType());
    }
}
