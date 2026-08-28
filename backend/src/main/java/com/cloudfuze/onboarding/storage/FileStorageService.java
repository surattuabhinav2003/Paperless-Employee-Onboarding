package com.cloudfuze.onboarding.storage;

import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Storage abstraction for every uploaded or generated document.
 * <p>
 * The application only ever handles opaque keys, so swapping the local
 * implementation for S3, Azure Blob or CloudFuze secure storage is a matter of
 * providing another bean of this type - no calling code changes.
 */
public interface FileStorageService {

    /**
     * Stores a stream under a logical folder.
     *
     * @param folder           logical prefix, e.g. {@code candidates/<id>/documents}
     * @param originalFilename filename supplied by the uploader
     * @param contentType      declared content type
     * @param content          stream to persist; closed by the implementation
     * @param sizeBytes        expected size, or -1 when unknown
     */
    StoredFile store(String folder, String originalFilename, String contentType, InputStream content, long sizeBytes);

    default StoredFile store(String folder, String originalFilename, String contentType, byte[] content) {
        return store(folder, originalFilename, contentType, new ByteArrayInputStream(content), content.length);
    }

    /** Loads a stored object for streaming back to an authorised caller. */
    Resource load(String key);

    /** Reads the full contents of a stored object. */
    byte[] readAllBytes(String key);

    boolean exists(String key);

    void delete(String key);

    String providerName();
}
