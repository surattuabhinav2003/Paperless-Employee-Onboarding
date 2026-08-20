package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** An HR user who signs in with email + password and receives a JWT. */
@Entity
@Table(name = "hr_users", indexes = {
        @Index(name = "idx_hr_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class HrUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public HrUser(String email, String passwordHash, String fullName, String jobTitle) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.jobTitle = jobTitle;
    }
}
