package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A detail field an administrator invented, alongside the fourteen built-in
 * ones.
 *
 * <p>Built-in fields are real columns on {@link CandidateProfile}; these cannot
 * be, because they are created at runtime. Their answers live in the profile's
 * custom-value map, keyed by {@link #code}.
 *
 * <p>That makes the code the permanent identity of the field: it is derived
 * from the label once, at creation, and never changes afterwards. Renaming a
 * field changes only its label, so answers already collected stay attached to
 * it.
 */
@Entity
@Table(name = "custom_candidate_fields", indexes = {
        @Index(name = "idx_custom_field_code", columnList = "code", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class CustomCandidateField {

    /** Codes the built-in fields already own, plus the profile's own property names. */
    private static final List<String> RESERVED = Arrays.stream(CandidateField.values())
            .map(CandidateField::getCode)
            .toList();

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 60, updatable = false)
    private String code;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private CustomFieldType type = CustomFieldType.TEXT;

    /** The choices for a select field, one per line. Null for every other type. */
    @Column(name = "options", length = 2000)
    private String options;

    /** Shown under the input on the candidate's form. */
    @Column(name = "help_text", length = 300)
    private String helpText;

    /** Which section of the form it appears under. */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_group", nullable = false, length = 20)
    private CandidateField.Group group = CandidateField.Group.ADDITIONAL;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "required", nullable = false)
    private boolean required;

    /** Order within its section; ties break on creation time. */
    @Column(name = "position", nullable = false)
    private int position;

    /**
     * Removed from the form but kept on record. A hard delete would strand every
     * answer already given, which contradicts the promise made about switching a
     * field off.
     */
    @Column(name = "archived", nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", length = 180)
    private String createdBy;

    public CustomCandidateField(String code, String label, CustomFieldType type) {
        this.code = code;
        this.label = label;
        this.type = type;
    }

    /** The choices as a list; empty for any type that does not have them. */
    public List<String> optionList() {
        if (!type.hasOptions() || options == null || options.isBlank()) {
            return List.of();
        }
        return options.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    /**
     * Turns a label into a stable code: lowercase, words joined by underscores.
     * Anything that collides with a built-in field, or is left with nothing
     * usable, is rejected by the service rather than silently mangled.
     */
    public static String toCode(String label) {
        if (label == null) {
            return "";
        }
        return label.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public static boolean isReserved(String code) {
        return RESERVED.contains(code);
    }
}
