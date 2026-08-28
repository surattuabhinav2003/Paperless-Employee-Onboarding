package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.NocRecipientViewDto;
import com.cloudfuze.onboarding.dto.SignNocRequest;
import com.cloudfuze.onboarding.service.NocService;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The recipient's side of an NDA + NOC packet.
 *
 * <p>Authenticated by the token in the link, which is hashed before lookup and
 * carries its own expiry. Recipients are on a restricted company domain, so the
 * link is the credential.
 */
@RestController
@RequestMapping("/api/noc/{token}")
public class NocSigningController {

    private final NocService nocService;

    public NocSigningController(NocService nocService) {
        this.nocService = nocService;
    }

    @GetMapping
    public ResponseEntity<NocRecipientViewDto> view(@PathVariable String token) {
        return ResponseEntity.ok(nocService.view(token));
    }

    /** Streams the combined document, or the signed copy once submitted. */
    @GetMapping("/file")
    public ResponseEntity<Resource> file(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"NDA-NOC.pdf\"")
                .body(new ByteArrayResource(nocService.recipientBytes(token)));
    }

    @PostMapping("/sign")
    public ResponseEntity<NocRecipientViewDto> sign(
            @PathVariable String token,
            @Valid @RequestBody SignNocRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(nocService.sign(token, request.signedByName(), request.fieldValues(),
                RequestContext.clientIp(httpRequest)));
    }
}
