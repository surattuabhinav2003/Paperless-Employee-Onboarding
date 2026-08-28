package com.cloudfuze.onboarding.storage;

/**
 * Result of a successful store operation.
 *
 * @param key            opaque storage key - the only handle the application keeps
 * @param originalFilename filename as supplied by the uploader (sanitised)
 * @param contentType    detected/declared content type
 * @param sizeBytes      stored size
 * @param sha256         hex digest of the stored bytes, used for audit evidence
 */
public record StoredFile(
        String key,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String sha256
) {
}
