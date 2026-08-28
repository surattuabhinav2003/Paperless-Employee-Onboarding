package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.exception.FileValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.Locale;

/** Central file validation for every upload path (candidate documents, offer). */
@Component
public class FileUploadValidator {

    private final AppProperties appProperties;

    public FileUploadValidator(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw FileValidationException.emptyFile();
        }
        long maxBytes = appProperties.getUpload().getMaxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw FileValidationException.tooLarge(maxBytes);
        }

        var allowedExtensions = appProperties.getUpload().getAllowedExtensions();
        String extension = extensionOf(file.getOriginalFilename());
        if (extension.isEmpty() || !allowedExtensions.contains(extension)) {
            throw FileValidationException.unsupportedType(String.join(", ", allowedExtensions));
        }

        verifyMagicBytes(file, extension);

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        var allowedTypes = appProperties.getUpload().getAllowedContentTypes();
        if (!contentType.isBlank() && !allowedTypes.contains(contentType)) {
            throw FileValidationException.unsupportedType(String.join(", ", allowedExtensions));
        }
    }

    public String safeFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            return "upload";
        }
        return Paths.get(original).getFileName().toString();
    }


    /*
     * The extension and the Content-Type both come from the client, so neither
     * proves anything. Checking the leading bytes stops a script or an HTML page
     * being stored as "certificate.pdf" and later served back to an HR user.
     */
    private void verifyMagicBytes(MultipartFile file, String extension) {
        byte[] head = new byte[12];
        int read;
        try (java.io.InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (java.io.IOException e) {
            throw new FileValidationException("FILE_UNREADABLE", "That file could not be read. Please try again.");
        }
        if (read < 4) {
            throw new FileValidationException("FILE_INVALID", "That file looks empty or damaged.");
        }

        boolean ok = switch (extension) {
            // %PDF
            case "pdf" -> head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46;
            // \x89PNG
            case "png" -> (head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47;
            // JPEG SOI
            case "jpg", "jpeg" -> (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8;
            // RIFF....WEBP
            case "webp" -> head[0] == 0x52 && head[1] == 0x49 && head[2] == 0x46 && head[3] == 0x46
                    && read >= 12 && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50;
            // .docx is a zip container: PK\x03\x04
            case "docx" -> head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04;
            // Legacy .doc is an OLE2 compound file. Accepted here so the
            // converter can give a specific "save it as .docx" message rather
            // than the generic wrong-file-type one.
            case "doc" -> (head[0] & 0xFF) == 0xD0 && (head[1] & 0xFF) == 0xCF
                    && (head[2] & 0xFF) == 0x11 && (head[3] & 0xFF) == 0xE0;
            default -> false;
        };

        if (!ok) {
            throw new FileValidationException("FILE_CONTENT_MISMATCH",
                    "That file is not a real " + extension.toUpperCase(Locale.ROOT)
                            + ". Please upload the original document.");
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
