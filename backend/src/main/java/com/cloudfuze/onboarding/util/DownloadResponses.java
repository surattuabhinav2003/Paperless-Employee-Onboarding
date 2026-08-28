package com.cloudfuze.onboarding.util;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Builds document responses without ever revealing a storage key. */
public final class DownloadResponses {

    private DownloadResponses() {
    }

    /**
     * Uploaded files are always sent as a download, never rendered inline.
     *
     * <p>The in-app viewer fetches bytes over XHR and builds its own blob URL,
     * so it is unaffected - but pasting a document URL straight into the address
     * bar can no longer execute anything in the HR user's session.
     */
    public static ResponseEntity<Resource> inline(Resource resource, String filename, String contentType) {
        return build(resource, filename, contentType, true);
    }

    public static ResponseEntity<Resource> attachment(Resource resource, String filename, String contentType) {
        return build(resource, filename, contentType, true);
    }

    private static ResponseEntity<Resource> build(Resource resource, String filename, String contentType,
                                                 boolean attachment) {
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (contentType != null && !contentType.isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (RuntimeException ignored) {
                // Fall back to a safe binary type.
            }
        }
        ContentDisposition disposition = (attachment
                ? ContentDisposition.attachment()
                : ContentDisposition.inline())
                .filename(filename == null ? "document" : filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, private")
                .header(HttpHeaders.PRAGMA, "no-cache")
                // Without nosniff a browser may ignore the declared type and
                // execute what it decides the bytes actually are.
                .header("X-Content-Type-Options", "nosniff")
                .contentType(mediaType)
                .body(resource);
    }
}
