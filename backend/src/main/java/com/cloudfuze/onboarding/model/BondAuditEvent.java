package com.cloudfuze.onboarding.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A single entry of the signature provider audit trail, stored with the bond. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class BondAuditEvent {

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "actor", length = 180)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(name = "detail", length = 600)
    private String detail;

    public BondAuditEvent(String eventType, String actor, Instant occurredAt, String ipAddress, String detail) {
        this.eventType = eventType;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.ipAddress = ipAddress;
        this.detail = detail;
    }
}
