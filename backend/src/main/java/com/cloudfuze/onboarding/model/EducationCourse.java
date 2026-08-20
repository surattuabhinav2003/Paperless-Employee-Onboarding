package com.cloudfuze.onboarding.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.List;

/**
 * The course an education certificate belongs to. Picked per document, so a
 * candidate says exactly what they are uploading - "Diploma" for their secondary
 * certificate, "B.Tech" for their degree provisional - rather than declaring one
 * overall qualification.
 *
 * @see DocumentType#requiresCourse()
 */
public enum EducationCourse {

    // Secondary: what follows Class 10.
    INTERMEDIATE("intermediate", "Class 12 / Intermediate", Level.SECONDARY),
    DIPLOMA("diploma", "Diploma", Level.SECONDARY),
    POLYTECHNIC("polytechnic", "Polytechnic", Level.SECONDARY),
    ITI("iti", "ITI", Level.SECONDARY),
    OTHER_SECONDARY("other_secondary", "Other secondary course", Level.SECONDARY),

    // Higher education: undergraduate.
    B_TECH("b_tech", "B.Tech", Level.UNDERGRADUATE),
    B_E("b_e", "B.E.", Level.UNDERGRADUATE),
    B_SC("b_sc", "B.Sc", Level.UNDERGRADUATE),
    B_COM("b_com", "B.Com", Level.UNDERGRADUATE),
    BCA("bca", "BCA", Level.UNDERGRADUATE),
    BBA("bba", "BBA", Level.UNDERGRADUATE),
    BA("ba", "B.A.", Level.UNDERGRADUATE),
    B_PHARM("b_pharm", "B.Pharm", Level.UNDERGRADUATE),
    OTHER_UG("other_ug", "Other bachelor's degree", Level.UNDERGRADUATE),

    // Higher education: postgraduate and above.
    M_TECH("m_tech", "M.Tech", Level.POSTGRADUATE),
    M_E("m_e", "M.E.", Level.POSTGRADUATE),
    M_SC("m_sc", "M.Sc", Level.POSTGRADUATE),
    MCA("mca", "MCA", Level.POSTGRADUATE),
    MBA("mba", "MBA", Level.POSTGRADUATE),
    M_COM("m_com", "M.Com", Level.POSTGRADUATE),
    MA("ma", "M.A.", Level.POSTGRADUATE),
    OTHER_PG("other_pg", "Other master's degree", Level.POSTGRADUATE),
    PHD("phd", "Ph.D.", Level.POSTGRADUATE);

    /** Which stage of education a course belongs to. */
    public enum Level {
        SECONDARY("Secondary education"),
        UNDERGRADUATE("Undergraduate"),
        POSTGRADUATE("Postgraduate");

        private final String label;

        Level(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final String code;
    private final String label;
    private final Level level;

    EducationCourse(String code, String label, Level level) {
        this.code = code;
        this.label = label;
        this.level = level;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public Level level() {
        return level;
    }

    public static List<EducationCourse> ofLevels(List<Level> levels) {
        return Arrays.stream(values()).filter(course -> levels.contains(course.level)).toList();
    }

    @JsonCreator
    public static EducationCourse fromCode(String code) {
        return Arrays.stream(values())
                .filter(course -> course.code.equalsIgnoreCase(code) || course.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown education course: " + code));
    }
}
