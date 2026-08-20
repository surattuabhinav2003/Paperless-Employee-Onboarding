package com.cloudfuze.onboarding.dto;

import com.cloudfuze.onboarding.model.ActorType;
import com.cloudfuze.onboarding.model.AuditEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        AuditEventType eventType,
        String eventLabel,
        String actor,
        ActorType actorType,
        Instant occurredAt,
        String ipAddress,
        String documentReference,
        Map<String, Object> metadata
) {
}
