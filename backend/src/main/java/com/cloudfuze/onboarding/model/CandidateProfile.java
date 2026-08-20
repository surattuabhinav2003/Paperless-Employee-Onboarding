package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
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

    @Column(name = "full_name_as_per_aadhaar", nullable = false, length = 160)
    private String fullNameAsPerAadhaar;

    @Column(name = "personal_email", nullable = false, length = 180)
    private String personalEmail;

    @Column(name = "contact_number", nullable = false, length = 25)
    private String contactNumber;

    @Column(name = "alternate_contact_number", length = 25)
    private String alternateContactNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 30,
            columnDefinition = "varchar(30)")
    private Gender gender;

    @Column(name = "fathers_name", nullable = false, length = 160)
    private String fathersName;

    @Column(name = "permanent_address", nullable = false, length = 600)
    private String permanentAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false, length = 20,
            columnDefinition = "varchar(20)")
    private BloodGroup bloodGroup;

    // ---- bookkeeping ----

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
