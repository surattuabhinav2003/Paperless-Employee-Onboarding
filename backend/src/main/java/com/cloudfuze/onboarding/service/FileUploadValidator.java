package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.exception.FileValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Paths;
import java.util.Locale;

/** Central file validation for every upload path (candidate, offer, bond). */
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
