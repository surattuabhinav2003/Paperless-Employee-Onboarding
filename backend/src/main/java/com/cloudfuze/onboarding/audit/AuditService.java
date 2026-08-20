package com.cloudfuze.onboarding.audit;

import com.cloudfuze.onboarding.dto.AuditLogDto;
import com.cloudfuze.onboarding.model.ActorType;
import com.cloudfuze.onboarding.model.AuditEventType;
import com.cloudfuze.onboarding.model.AuditLog;
import com.cloudfuze.onboarding.repository.AuditLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit trail. Every state change in the workflow routes through
 * here so HR can reconstruct exactly what happened, when, and from where.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_METADATA_LENGTH = 4000;

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog record(UUID candidateId, AuditEventType eventType, String actor, ActorType actorType,
                           String ipAddress, String documentReference, Map<String, Object> metadata) {
        AuditLog entry = new AuditLog();
        entry.setCandidateId(candidateId);
        entry.setEventType(eventType);
        entry.setActor(actor == null ? "system" : actor);
        entry.setActorType(actorType);
        entry.setIpAddress(ipAddress);
        entry.setDocumentReference(documentReference);
        entry.setMetadata(serialize(metadata));
        return repository.save(entry);
    }

    @Transactional
    public AuditLog recordHrEvent(UUID candidateId, AuditEventType eventType, String hrActor, String ipAddress,
                                  String documentReference, Map<String, Object> metadata) {
        return record(candidateId, eventType, hrActor, ActorType.HR, ipAddress, documentReference, metadata);
    }

    @Transactional
    public AuditLog recordCandidateEvent(UUID candidateId, AuditEventType eventType, String candidateEmail,
                                         String ipAddress, String documentReference,
                                         Map<String, Object> metadata) {
        return record(candidateId, eventType, candidateEmail, ActorType.CANDIDATE, ipAddress, documentReference,
                metadata);
    }

    @Transactional
    public AuditLog recordSystemEvent(UUID candidateId, AuditEventType eventType, Map<String, Object> metadata) {
        return record(candidateId, eventType, "system", ActorType.SYSTEM, null, null, metadata);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> forCandidate(UUID candidateId) {
        return repository.findByCandidateIdOrderByOccurredAtDesc(candidateId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> recent(int limit) {
        return repository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(this::toDto)
                .toList();
    }

    public AuditLogDto toDto(AuditLog entry) {
        return new AuditLogDto(entry.getId(), entry.getEventType(), entry.getEventType().getLabel(),
                entry.getActor(), entry.getActorType(), entry.getOccurredAt(), entry.getIpAddress(),
                entry.getDocumentReference(), deserialize(entry.getMetadata()));
    }

    private String serialize(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(metadata);
            return json.length() > MAX_METADATA_LENGTH ? json.substring(0, MAX_METADATA_LENGTH) : json;
        } catch (Exception e) {
            log.warn("Could not serialise audit metadata: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> deserialize(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of("raw", metadata);
        }
    }
}
