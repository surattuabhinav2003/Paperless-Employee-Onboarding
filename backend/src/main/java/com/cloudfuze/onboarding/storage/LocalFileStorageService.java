package com.cloudfuze.onboarding.storage;

import com.cloudfuze.onboarding.config.StorageProperties;
import com.cloudfuze.onboarding.exception.StorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * Development / single-node implementation. Files land under a configured root
 * that must sit outside any statically served directory; nothing here is
 * reachable without going through an authorised controller.
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        this.root = Paths.get(properties.getLocal().getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new StorageException("Unable to create storage root " + root, e);
        }
        log.info("Local file storage initialised at {}", root);
    }

    @Override
    public StoredFile store(String folder, String originalFilename, String contentType,
                            InputStream content, long sizeBytes) {
        String safeName = sanitize(originalFilename);
        String extension = extensionOf(safeName);
        String key = normaliseFolder(folder) + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = resolve(key);

        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long written;
            try (InputStream in = content;
                 OutputStream fileOut = Files.newOutputStream(target);
                 DigestOutputStream out = new DigestOutputStream(fileOut, digest)) {
                written = in.transferTo(out);
            }
            String sha256 = HexFormat.of().formatHex(digest.digest());
            return new StoredFile(key, safeName, contentType, written, sha256);
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 unavailable in this JVM", e);
        } catch (IOException e) {
            throw new StorageException("Failed to store file " + safeName, e);
        }
    }

    @Override
    public Resource load(String key) {
        Path path = resolve(key);
        if (!Files.exists(path)) {
            throw new StorageException("Stored object is missing: " + key);
        }
        return new FileSystemResource(path);
    }

    @Override
    public byte[] readAllBytes(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to read stored object " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to delete stored object " + key, e);
        }
    }

    @Override
    public String providerName() {
        return "local";
    }

    /** Copies an existing object to a new key, used when archiving previous versions. */
    public String copy(String sourceKey, String targetFolder) {
        Path source = resolve(sourceKey);
        String extension = extensionOf(sourceKey);
        String key = normaliseFolder(targetFolder) + "/" + UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new StorageException("Failed to archive stored object " + sourceKey, e);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException("Storage key must not be empty");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            // Defensive: a key must never be able to escape the storage root.
            throw new StorageException("Rejected storage key outside of root: " + key);
        }
        return resolved;
    }

    private static String normaliseFolder(String folder) {
        String value = folder == null ? "misc" : folder.replace("\\", "/").replaceAll("^/+|/+$", "");
        return value.isBlank() ? "misc" : value;
    }

    private static String sanitize(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String base = Paths.get(filename).getFileName().toString();
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
