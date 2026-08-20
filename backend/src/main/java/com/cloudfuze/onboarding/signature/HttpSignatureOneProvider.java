package com.cloudfuze.onboarding.signature;

import com.cloudfuze.onboarding.config.SignatureOneProperties;
import com.cloudfuze.onboarding.exception.SignatureProviderException;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureAuditEntry;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureRequestHandle;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureState;
import com.cloudfuze.onboarding.signature.SignatureModels.SignatureStatusResult;
import com.cloudfuze.onboarding.signature.SignatureModels.SignedDocumentPayload;
import com.cloudfuze.onboarding.signature.SignatureModels.SignerConsent;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningRequestSpec;
import com.cloudfuze.onboarding.signature.SignatureModels.SigningSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real SignatureOne client, enabled with {@code signatureone.provider=http}.
 * <p>
 * The request/response field names below follow the documented SignatureOne REST
 * conventions and are the single place to adjust once CloudFuze supplies the
 * production API contract and credentials. Nothing else in the application needs
 * to change.
 */
@Service
@ConditionalOnProperty(name = "signatureone.provider", havingValue = "http")
public class HttpSignatureOneProvider implements SignatureOneService {

    private static final Logger log = LoggerFactory.getLogger(HttpSignatureOneProvider.class);

    private final SignatureOneProperties properties;
    private final RestClient client;

    public HttpSignatureOneProvider(SignatureOneProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        if (properties.getBaseUrl().isBlank() || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("signatureone.base-url and signatureone.api-key are required "
                    + "when signatureone.provider=http");
        }
        this.client = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("X-SignatureOne-Account", properties.getAccountId())
                .build();
        log.info("SignatureOne HTTP provider initialised for account {}", properties.getAccountId());
    }

    @Override
    public SignatureRequestHandle createSigningRequest(SigningRequestSpec spec) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalReference", spec.externalReference());
        body.put("documentName", spec.documentName());
        body.put("documentVersion", spec.documentVersion());
        body.put("documentContentType", spec.documentContentType());
        body.put("documentBase64", Base64.getEncoder().encodeToString(spec.documentContent()));
        body.put("signer", Map.of(
                "name", spec.candidateName(),
                "email", spec.candidateEmail(),
                "externalId", String.valueOf(spec.candidateId())));

        Map<?, ?> response = post("/v1/signature-requests", body);
        return new SignatureRequestHandle(text(response, "id"), state(text(response, "status")),
                instant(response, "createdAt"));
    }

    @Override
    public SigningSession createSigningSession(String requestId, String returnUrl) {
        Map<?, ?> response = post("/v1/signature-requests/" + requestId + "/sessions",
                Map.of("returnUrl", returnUrl == null ? properties.getReturnUrl() : returnUrl));
        return new SigningSession(requestId, text(response, "signingUrl"), instant(response, "expiresAt"));
    }

    @Override
    public SignatureStatusResult submitSignature(String requestId, SignerConsent consent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signerName", consent.signerName());
        body.put("signerEmail", consent.signerEmail());
        body.put("consentGiven", consent.agreed());
        body.put("ipAddress", consent.ipAddress());
        body.put("userAgent", consent.userAgent());
        Map<?, ?> response = post("/v1/signature-requests/" + requestId + "/signature", body);
        return toStatus(requestId, response);
    }

    @Override
    public SignatureStatusResult getStatus(String requestId) {
        Map<?, ?> response = get("/v1/signature-requests/" + requestId);
        return toStatus(requestId, response);
    }

    @Override
    public SignedDocumentPayload retrieveSignedDocument(String requestId) {
        try {
            byte[] content = client.get()
                    .uri("/v1/signature-requests/{id}/signed-document", requestId)
                    .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_PDF)
                    .retrieve()
                    .body(byte[].class);
            if (content == null || content.length == 0) {
                throw new SignatureProviderException("SignatureOne returned an empty signed document.");
            }
            return new SignedDocumentPayload(requestId, "signed-bond-" + requestId + ".pdf",
                    MediaType.APPLICATION_PDF_VALUE, content, sha256(content));
        } catch (RestClientException e) {
            throw new SignatureProviderException("Could not download the signed document from SignatureOne.", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SignatureAuditEntry> getAuditTrail(String requestId) {
        Map<?, ?> response = get("/v1/signature-requests/" + requestId + "/audit-trail");
        Object events = response.get("events");
        List<SignatureAuditEntry> entries = new ArrayList<>();
        if (events instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    entries.add(new SignatureAuditEntry(
                            text(map, "type"), text(map, "actor"), instant(map, "timestamp"),
                            text(map, "ipAddress"), text(map, "description")));
                }
            }
        }
        return entries;
    }

    @Override
    public String providerName() {
        return "signatureone-http";
    }

    private SignatureStatusResult toStatus(String requestId, Map<?, ?> response) {
        return new SignatureStatusResult(requestId, state(text(response, "status")), instant(response, "updatedAt"),
                text(response, "signatureReference"), text(response, "message"));
    }

    private Map<?, ?> post(String path, Object body) {
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new SignatureProviderException("SignatureOne request failed: " + path, e);
        }
    }

    private Map<?, ?> get(String path) {
        try {
            Map<?, ?> response = client.get().uri(path).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(Map.class);
            if (response == null) {
                throw new SignatureProviderException("SignatureOne returned an empty response for " + path);
            }
            return response;
        } catch (RestClientException e) {
            throw new SignatureProviderException("SignatureOne request failed: " + path, e);
        }
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(Map<?, ?> map, String key) {
        String value = text(map, key);
        try {
            return value == null ? Instant.now() : Instant.parse(value);
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private static SignatureState state(String value) {
        if (value == null) {
            return SignatureState.CREATED;
        }
        return switch (value.toLowerCase()) {
            case "signed", "completed" -> SignatureState.SIGNED;
            case "viewed", "opened" -> SignatureState.VIEWED;
            case "sent", "pending" -> SignatureState.SENT;
            case "declined", "rejected" -> SignatureState.DECLINED;
            case "failed", "error" -> SignatureState.FAILED;
            default -> SignatureState.CREATED;
        };
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureProviderException("SHA-256 unavailable", e);
        }
    }
}
