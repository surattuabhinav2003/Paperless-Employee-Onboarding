package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * The kinds of answer an admin-created field can ask for.
 *
 * <p>Deliberately a short list. Every type here has an obvious input on the
 * candidate's form and an obvious way to be checked; offering something with no
 * clear validation would mean collecting values nobody can trust.
 */
public enum CustomFieldType {

    TEXT("text", "Short text", null, "Enter a value"),
    TEXTAREA("textarea", "Long text", null, "Enter a value"),
    NUMBER("number", "Number", Pattern.compile("^-?\\d+(\\.\\d+)?$"), "Enter a number"),
    DATE("date", "Date", Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"), "Enter a valid date"),
    EMAIL("email", "Email address", Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),
            "Enter a valid email address"),
    /* Same shape the built-in phone fields accept, so the two never disagree
       about what a valid number looks like. */
    PHONE("phone", "Phone number", Pattern.compile("^\\+?[0-9][0-9 \\-]{7,19}$"),
            "Enter a valid phone number"),
    /** One of a list the admin writes; the answer must be on that list. */
    SELECT("select", "Choice from a list", null, "Choose one of the listed options");

    private final String code;
    private final String label;
    private final Pattern pattern;
    private final String message;

    CustomFieldType(String code, String label, Pattern pattern, String message) {
        this.code = code;
        this.label = label;
        this.pattern = pattern;
        this.message = message;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** Whether the admin has to supply the list of answers. */
    public boolean hasOptions() {
        return this == SELECT;
    }

    /**
     * @return null when the value is acceptable, otherwise why it is not. A
     *         blank value is always acceptable here - whether it is allowed to
     *         be blank is the field's requiredness, decided elsewhere.
     */
    public String validate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return pattern == null || pattern.matcher(value.trim()).matches() ? null : message;
    }

    @JsonCreator
    public static CustomFieldType fromCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown field type: " + code));
    }
}
