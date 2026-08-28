package com.cloudfuze.onboarding.storage;

import com.cloudfuze.onboarding.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Documents stored in the database, alongside the records that reference them.
 *
 * <p>The default in production, and the reason is deployment rather than
 * taste. The disk-backed store writes under {@code ./storage}, which on any
 * container platform is thrown away on the next restart or redeploy - the rows
 * survive, the files do not, and the console shows candidates whose documents
 * have silently ceased to exist. That is the worst kind of failure: invisible
 * until someone needs the paperwork.
 *
 * <p>Putting them in Postgres removes the whole class of problem. There is no
 * bucket to create, no credentials to rotate, no second thing that can be
 * unreachable, and the documents are inside the database backup rather than
 * needing one of their own. Uploads are capped at 10MB and this is an internal
 * HR tool, so the volume is measured in gigabytes over years - well within what
 * Postgres handles comfortably.
 *
 * <p>If that ever stops being true, object storage is another implementation of
 * {@link FileStorageService} and nothing that calls it has to change - which is
 * exactly what the interface was built for.
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "database")
public class DatabaseFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseFileStorageService.class);

    private final StoredBlobRepository repository;

    public DatabaseFileStorageService(StoredBlobRepository repository) {
        this.repository = repository;
        log.info("Database file storage initialised");
    }

    @Override
    @Transactional
    public StoredFile store(String folder, String originalFilename, String contentType,
                            InputStream content, long sizeBytes) {
        String safeName = safeName(originalFilename);
        String extension = extensionOf(safeName);
        String key = normaliseFolder(folder) + "/" + UUID.randomUUID()
                + (extension.isEmpty() ? "" : "." + extension);

        try (InputStream in = content) {
            byte[] data = in.readAllBytes();

            StoredBlob blob = new StoredBlob();
            blob.setKey(key);
            blob.setOriginalFilename(safeName);
            blob.setContentType(contentType);
            blob.setSizeBytes(data.length);
            blob.setSha256(HexFormat.of().formatHex(sha256().digest(data)));
            blob.setData(data);
            repository.save(blob);

            return new StoredFile(key, safeName, contentType, data.length, blob.getSha256());
        } catch (IOException e) {
            throw new StorageException("Unable to store " + safeName, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource load(String key) {
        return new ByteArrayResource(readAllBytes(key));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] readAllBytes(String key) {
        return repository.findById(key)
                .map(StoredBlob::getData)
                .orElseThrow(() -> new StorageException("No stored file for key " + key));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String key) {
        return key != null && repository.existsById(key);
    }

    @Override
    @Transactional
    public void delete(String key) {
        repository.deleteById(key);
    }

    @Override
    public String providerName() {
        return "database";
    }

    /* ---- helpers, matching the disk-backed store so keys are interchangeable ---- */

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required and always present", e);
        }
    }

    /** Strips any path the uploader's browser sent along with the name. */
    private static String safeName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload";
        }
        String name = Paths.get(originalFilename).getFileName().toString().trim();
        return name.isEmpty() ? "upload" : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String normaliseFolder(String folder) {
        String trimmed = folder == null ? "" : folder.replace('\\', '/').trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "misc" : trimmed;
    }
}
