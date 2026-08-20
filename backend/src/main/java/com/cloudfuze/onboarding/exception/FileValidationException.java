package com.cloudfuze.onboarding.exception;

import org.springframework.http.HttpStatus;

public class FileValidationException extends ApiException {

    public FileValidationException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public static FileValidationException emptyFile() {
        return new FileValidationException("FILE_EMPTY", "The selected file is empty. Please choose a valid file.");
    }

    public static FileValidationException unsupportedType(String allowed) {
        return new FileValidationException("FILE_TYPE_NOT_ALLOWED",
                "That file type is not supported. Allowed formats: " + allowed + ".");
    }

    public static FileValidationException tooLarge(long maxBytes) {
        long mb = Math.max(1, maxBytes / (1024 * 1024));
        return new FileValidationException("FILE_TOO_LARGE",
                "That file is too large. Maximum allowed size is " + mb + " MB.");
    }
}
