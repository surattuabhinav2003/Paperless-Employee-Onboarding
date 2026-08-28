package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * The personal details a candidate can be asked for, in the order the form
 * presents them.
 *
 * <p>The set is fixed - each one is a real column on {@link CandidateProfile},
 * so a new field means a schema change. What an administrator controls is
 * whether each is <em>asked for</em> and whether it is <em>required</em>, which
 * is the part that differs between companies and over time.
 *
 * <p>{@code group} matches the headings on the form so the admin screen reads
 * in the same order the candidate sees.
 */
public enum CandidateField {

    FULL_NAME_AS_PER_AADHAAR("full_name_as_per_aadhaar", "Full name (as per Aadhaar)", Group.PERSONAL, true),
    PERSONAL_EMAIL("personal_email", "Personal email ID", Group.PERSONAL, true),
    CONTACT_NUMBER("contact_number", "Contact number", Group.PERSONAL, true),
    ALTERNATE_CONTACT_NUMBER("alternate_contact_number", "Alternate contact number", Group.PERSONAL, true),
    DATE_OF_BIRTH("date_of_birth", "Date of birth", Group.PERSONAL, true),
    GENDER("gender", "Gender", Group.PERSONAL, true),
    FATHERS_NAME("fathers_name", "Father's name", Group.PERSONAL, true),
    PERMANENT_ADDRESS("permanent_address", "Permanent address", Group.PERSONAL, true),
    BLOOD_GROUP("blood_group", "Blood group", Group.PERSONAL, true),

    AADHAAR_NUMBER("aadhaar_number", "Aadhaar number", Group.IDENTITY, true),
    PAN_NUMBER("pan_number", "PAN number", Group.IDENTITY, true),

    EMERGENCY_CONTACT_NAME("emergency_contact_name", "Emergency contact name", Group.EMERGENCY, true),
    EMERGENCY_CONTACT_RELATION("emergency_contact_relation", "Emergency contact relation", Group.EMERGENCY, true),
    EMERGENCY_CONTACT_NUMBER("emergency_contact_number", "Emergency contact number", Group.EMERGENCY, true);

    /** Form sections, so the admin screen groups fields the way candidates see them. */
    public enum Group {
        PERSONAL("Personal information"),
        IDENTITY("Identity numbers"),
        EMERGENCY("Emergency contact"),
        /* Where admin-created fields land unless one of the sections above
           fits better. No built-in field uses it, so the heading only appears
           once there is something to put under it. */
        ADDITIONAL("Additional details");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        /* The API talks about groups in lowercase, the way every other enum
           here is coded. Without this, a group sent back on a custom field is
           rejected for not matching the constant name exactly. */
        @JsonValue
        public String getCode() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        @JsonCreator
        public static Group fromCode(String code) {
            return Arrays.stream(values())
                    .filter(g -> g.name().equalsIgnoreCase(code))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown field group: " + code));
        }
    }

    private final String code;
    private final String label;
    private final Group group;
    /** Whether it is asked for, and required, unless an admin says otherwise. */
    private final boolean requiredByDefault;

    CandidateField(String code, String label, Group group, boolean requiredByDefault) {
        this.code = code;
        this.label = label;
        this.group = group;
        this.requiredByDefault = requiredByDefault;
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

    public boolean isRequiredByDefault() {
        return requiredByDefault;
    }

    @JsonCreator
    public static CandidateField fromCode(String code) {
        return Arrays.stream(values())
                .filter(f -> f.code.equalsIgnoreCase(code) || f.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown candidate field: " + code));
    }
}
