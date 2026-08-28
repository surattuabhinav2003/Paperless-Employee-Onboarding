package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The personal and education details a candidate submits alongside their
 * documents. Separate from {@link Candidate} because HR owns the candidate
 * record while the candidate owns this one.
 */
@Entity
@Table(name = "candidate_profiles", indexes = {
        @Index(name = "idx_profiles_candidate", columnList = "candidate_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class CandidateProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_profiles_candidate"))
    private Candidate candidate;

    // ---- personal ----

    @Column(name = "full_name_as_per_aadhaar", length = 160)
    private String fullNameAsPerAadhaar;

    @Column(name = "personal_email", length = 180)
    private String personalEmail;

    @Column(name = "contact_number", length = 25)
    private String contactNumber;

    @Column(name = "alternate_contact_number", length = 25)
    private String alternateContactNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30,
            columnDefinition = "varchar(30)")
    private Gender gender;

    @Column(name = "fathers_name", length = 160)
    private String fathersName;

    @Column(name = "permanent_address", length = 600)
    private String permanentAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", length = 20,
            columnDefinition = "varchar(20)")
    private BloodGroup bloodGroup;

    // ---- identity numbers ----

    /*
     * Added after this table already had rows (Abhinav's own test candidate
     * among them), so these stay nullable at the database level even though the
     * request DTO requires them for every new save - ddl-auto=update cannot add
     * a NOT NULL column to a populated table. A profile saved before this
     * feature existed just shows "Not provided" until the candidate edits it
     * again.
     */
    @Column(name = "aadhaar_number", length = 20)
    private String aadhaarNumber;

    @Column(name = "pan_number", length = 10)
    private String panNumber;

    // ---- emergency contact ----

    @Column(name = "emergency_contact_name", length = 160)
    private String emergencyContactName;

    @Enumerated(EnumType.STRING)
    @Column(name = "emergency_contact_relation", length = 30, columnDefinition = "varchar(30)")
    private EmergencyContactRelation emergencyContactRelation;

    @Column(name = "emergency_contact_number", length = 25)
    private String emergencyContactNumber;

    // ---- bookkeeping ----

    /**
     * Answers to the fields an admin created, keyed by the field's code.
     *
     * <p>Built-in details are columns above; these cannot be, because the fields
     * they belong to are invented at runtime. Eagerly fetched because every
     * caller that reads a profile renders it whole - the candidate's form, HR's
     * detail panel, the review dialog.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "candidate_profile_custom_values",
            joinColumns = @JoinColumn(name = "profile_id"),
            indexes = @Index(name = "idx_profile_custom_values", columnList = "profile_id"))
    @MapKeyColumn(name = "field_code", length = 60)
    @Column(name = "field_value", length = 2000)
    private Map<String, String> customValues = new LinkedHashMap<>();

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "submitted_from_ip", length = 60)
    private String submittedFromIp;

    @Column(name = "revision", nullable = false)
    private int revision = 1;

    public CandidateProfile(Candidate candidate) {
        this.candidate = candidate;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
