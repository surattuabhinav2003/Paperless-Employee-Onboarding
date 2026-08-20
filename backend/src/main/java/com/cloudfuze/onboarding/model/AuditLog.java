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
import java.util.UUID;

/** Append-only audit record. Never updated, never deleted by application code. */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_candidate", columnList = "candidate_id"),
        @Index(name = "idx_audit_event_type", columnList = "event_type"),
        @Index(name = "idx_audit_occurred_at", columnList = "occurred_at")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "candidate_id")
    private UUID candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60,
            columnDefinition = "varchar(60)")
    private AuditEventType eventType;

    @Column(name = "actor", nullable = false, length = 180)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 30,
            columnDefinition = "varchar(30)")
    private ActorType actorType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "document_reference", length = 200)
    private String documentReference;

    /** JSON blob of extra context. Plain varchar so it stays portable across databases. */
    @Column(name = "metadata", length = 4000)
    private String metadata;
}
