package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.BondAuditEvent;

import java.time.Instant;

public record BondAuditEventDto(
        String eventType,
        String actor,
        Instant occurredAt,
        String ipAddress,
        String detail
) {
    public static BondAuditEventDto from(BondAuditEvent event) {
        return new BondAuditEventDto(event.getEventType(), event.getActor(), event.getOccurredAt(),
                event.getIpAddress(), event.getDetail());
    }
}
