package com.cloudfuze.onboarding.controller;

import com.cloudfuze.onboarding.dto.NocPacketDto;
import com.cloudfuze.onboarding.dto.PageResponse;
import com.cloudfuze.onboarding.dto.SaveNocFieldsRequest;
import com.cloudfuze.onboarding.model.NocStatus;
import com.cloudfuze.onboarding.security.HrPrincipal;
import com.cloudfuze.onboarding.service.NocService;
import com.cloudfuze.onboarding.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/** The NDA + NOC section: combine two documents, place fields, send, track. */
@RestController
@RequestMapping("/api/hr/noc")
public class HrNocController {

    private final NocService nocService;

    public HrNocController(NocService nocService) {
        this.nocService = nocService;
    }

    /** Uploads the NDA and the NOC together; they are combined into one document. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NocPacketDto> create(@RequestParam("nda") MultipartFile nda,
                                               @RequestParam("noc") MultipartFile noc,
                                               @RequestParam("recipientName") String recipientName,
                                               @RequestParam("recipientEmail") String recipientEmail,
                                               @RequestParam(value = "title", required = false) String title,
                                               @AuthenticationPrincipal HrPrincipal hrUser,
                                               HttpServletRequest httpRequest) {
        NocPacketDto created = nocService.create(nda, noc, recipientName, recipientEmail, title, hrUser,
                RequestContext.clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<NocPacketDto>> list(
            @RequestParam(value = "status", required = false) NocStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(nocService.list(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NocPacketDto> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(nocService.detail(id));
    }

    /**
     * The live signing link, so HR can copy it or read it back to the recipient.
     * Kept off the detail response: it is the credential that opens the
     * document, so it is only handed over when explicitly asked for.
     */
    @GetMapping("/{id}/link")
    public ResponseEntity<Map<String, String>> link(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("signingUrl", nocService.signingUrl(id)));
    }

    /** Places the fields the recipient must complete, in combined-document coordinates. */
    @PutMapping("/{id}/fields")
    public ResponseEntity<NocPacketDto> saveFields(@PathVariable UUID id,
                                                   @Valid @RequestBody SaveNocFieldsRequest request,
                                                   @AuthenticationPrincipal HrPrincipal hrUser,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(nocService.saveFields(id, request.fields(), hrUser,
                RequestContext.clientIp(httpRequest)));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<NocPacketDto> send(@PathVariable UUID id,
                                             @AuthenticationPrincipal HrPrincipal hrUser,
                                             HttpServletRequest httpRequest) {
        return ResponseEntity.ok(nocService.send(id, hrUser, RequestContext.clientIp(httpRequest)));
    }

    /** The combined document - the signed version once it exists. */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        byte[] bytes = nocService.documentBytes(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + nocService.documentFilename(id) + "\"")
                .body(new ByteArrayResource(bytes));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal HrPrincipal hrUser) {
        nocService.delete(id, hrUser);
        return ResponseEntity.noContent().build();
    }
}
