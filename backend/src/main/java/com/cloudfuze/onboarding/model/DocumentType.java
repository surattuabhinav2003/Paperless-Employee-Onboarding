package com.cloudfuze.onboarding.model;

import com.cloudfuze.onboarding.model.EducationCourse.Level;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;

/**
 * Document types HR can request, declared in the order a candidate works through
 * them: education from Class 10 upwards, then identity, then employment.
 * <p>
 * Education certificates above Class 10 carry a course selection
 * ({@link #requiresCourse()}), so the candidate states whether their secondary
 * certificate is Intermediate, Diploma or Polytechnic, and which degree their
 * higher-education documents belong to.
 * <p>
 * {@code EDUCATION_CERTIFICATE} and the level-specific types that preceded this
 * layout are retained only so existing candidates still deserialize; they are
 * hidden from the picker via {@link #isSelectable()}.
 */
public enum DocumentType {

    // ---- Education, in order ----
    SSC_CERTIFICATE("ssc_certificate", "Class 10 (SSC) Certificate",
            Group.EDUCATION, true, List.of()),
    SECONDARY_EDUCATION_CERTIFICATE("secondary_education_certificate",
            "Secondary Education Certificate", Group.EDUCATION, true, List.of(Level.SECONDARY)),
    HIGHER_EDUCATION_PROVISIONAL("higher_education_provisional",
            "Higher Education - Provisional Certificate", Group.EDUCATION, true,
            List.of(Level.UNDERGRADUATE, Level.POSTGRADUATE)),
    // The provisional certificate is issued right after results; the original
    // degree certificate follows later from the university at convocation - HR
    // asks for both because they arrive months apart.
    HIGHER_EDUCATION_ORIGINAL_DEGREE("higher_education_original_degree",
            "Higher Education - Original Degree Certificate", Group.EDUCATION, true,
            List.of(Level.UNDERGRADUATE, Level.POSTGRADUATE)),
    HIGHER_EDUCATION_MARKSHEET("higher_education_marksheet",
            "Higher Education - Semester Marksheet", Group.EDUCATION, true,
            List.of(Level.UNDERGRADUATE, Level.POSTGRADUATE)),

    // ---- Identity and personal ----
    AADHAAR_ID("aadhaar_id", "Aadhaar Card", Group.IDENTITY, true, List.of()),
    PAN_CARD("pan_card", "PAN Card", Group.IDENTITY, true, List.of()),
    PASSPORT_PHOTO("passport_photo", "Passport-size Photo", Group.IDENTITY, true, List.of()),
    ADDRESS_PROOF("address_proof", "Address Proof", Group.IDENTITY, true, List.of()),

    // ---- Previous employment ----
    EXPERIENCE_CERTIFICATE("experience_certificate", "Experience Certificate",
            Group.EMPLOYMENT, true, List.of()),
    RELIEVING_LETTER("relieving_letter", "Relieving Letter", Group.EMPLOYMENT, true, List.of()),
    PREVIOUS_OFFER_LETTER("previous_offer_letter", "Previous Employer Offer Letter",
            Group.EMPLOYMENT, true, List.of()),
    // Six separate slots rather than one "recent payslips" upload, so HR gets a
    // full six-month trail and the candidate can see exactly which month is
    // still missing instead of guessing what one combined upload should contain.
    PAYSLIP_MONTH_1("payslip_month_1", "Payslip - Most Recent Month", Group.EMPLOYMENT, true, List.of()),
    PAYSLIP_MONTH_2("payslip_month_2", "Payslip - 2nd Most Recent Month", Group.EMPLOYMENT, true, List.of()),
    PAYSLIP_MONTH_3("payslip_month_3", "Payslip - 3rd Most Recent Month", Group.EMPLOYMENT, true, List.of()),
    PAYSLIP_MONTH_4("payslip_month_4", "Payslip - 4th Most Recent Month", Group.EMPLOYMENT, true, List.of()),
    PAYSLIP_MONTH_5("payslip_month_5", "Payslip - 5th Most Recent Month", Group.EMPLOYMENT, true, List.of()),
    PAYSLIP_MONTH_6("payslip_month_6", "Payslip - 6th Most Recent Month", Group.EMPLOYMENT, true, List.of()),

    // ---- Retired: readable on existing records, never offered again ----
    PAYSLIPS("payslips", "Recent Payslips (retired)", Group.EMPLOYMENT, false, List.of()),
    BANK_DETAILS("bank_details", "Bank Details / Cancelled Cheque (retired)", Group.PAYROLL, false, List.of()),
    OTHER("other", "Other Supporting Document (retired)", Group.OTHER, false, List.of()),
    EDUCATION_CERTIFICATE("education_certificate", "Education Certificate (retired)",
            Group.EDUCATION, false, List.of()),
    INTERMEDIATE_CERTIFICATE("intermediate_certificate", "Class 12 / Intermediate Certificate (retired)",
            Group.EDUCATION, false, List.of()),
    DIPLOMA_CERTIFICATE("diploma_certificate", "Diploma / Polytechnic Certificate (retired)",
            Group.EDUCATION, false, List.of()),
    UG_DEGREE_CERTIFICATE("ug_degree_certificate", "Degree Certificate (retired)",
            Group.EDUCATION, false, List.of()),
    PG_DEGREE_CERTIFICATE("pg_degree_certificate", "Post-Graduate Certificate (retired)",
            Group.EDUCATION, false, List.of());

    public enum Group {
        EDUCATION("Education"),
        IDENTITY("Identity & personal"),
        EMPLOYMENT("Previous employment"),
        PAYROLL("Payroll"),
        OTHER("Other");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /* The API names groups in lowercase, like every other enum here. */
        @JsonValue
        public String getCode() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        @JsonCreator
        public static Group fromCode(String code) {
            return Arrays.stream(values())
                    .filter(g -> g.name().equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown document group: " + code));
        }
    }

    private final String code;
    private final String label;
    private final Group group;
    private final boolean selectable;
    private final List<Level> courseLevels;

    DocumentType(String code, String label, Group group, boolean selectable, List<Level> courseLevels) {
        this.code = code;
        this.label = label;
        this.group = group;
        this.selectable = selectable;
        this.courseLevels = courseLevels;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public Group group() {
        return group;
    }

    /** False for retired types that must stay readable but must not be offered again. */
    public boolean isSelectable() {
        return selectable;
    }

    /** True when the candidate must say which course this certificate belongs to. */
    public boolean requiresCourse() {
        return !courseLevels.isEmpty();
    }

    /** The courses that are valid for this document type, in display order. */
    public List<EducationCourse> courseOptions() {
        return courseLevels.isEmpty() ? List.of() : EducationCourse.ofLevels(courseLevels);
    }

    public boolean accepts(EducationCourse course) {
        return course != null && courseLevels.contains(course.level());
    }

    public static List<DocumentType> selectableValues() {
        return Arrays.stream(values()).filter(DocumentType::isSelectable).toList();
    }

    @JsonCreator
    public static DocumentType fromCode(String code) {
        return fromCodeOrEmpty(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown document type: " + code));
    }

    /**
     * The built-in type for this code, if there is one.
     *
     * <p>Unlike {@link #fromCode}, an unknown code is not an error here - it may
     * simply belong to a type an administrator created, which the document
     * catalogue resolves instead.
     */
    public static java.util.Optional<DocumentType> fromCodeOrEmpty(String code) {
        if (code == null || code.isBlank()) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(values())
                .filter(t -> t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code))
                .findFirst();
    }
}
