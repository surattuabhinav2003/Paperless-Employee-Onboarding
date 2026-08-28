package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.AzureAdProperties;
import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Sends as a mailbox in the Microsoft 365 tenant, through Graph.
 *
 * <p>The reason this exists rather than SMTP: a relay takes the message and
 * delivers it, and the sending mailbox never learns it happened. Nothing
 * appears in Sent Items, so the person whose name is on the correspondence
 * cannot see their own half of it in Outlook. Graph's sendMail writes the copy
 * as it sends, which is the only way to close that gap properly - filing a copy
 * afterwards over IMAP would be a second thing to go wrong.
 *
 * <h3>What the tenant has to allow</h3>
 * The app registration needs the <b>application</b> permission
 * {@code Mail.Send} with admin consent granted. That is a deliberate,
 * administrator-only step: it lets this service send as any mailbox in the
 * tenant. Scoping it down to the one mailbox it should use is worth doing, via
 * an application access policy in Exchange Online.
 *
 * <p>Called through a plain REST client rather than the Graph SDK, which would
 * add a large dependency tree for one endpoint.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "graph")
public class GraphEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(GraphEmailService.class);
    private static final String GRAPH = "https://graph.microsoft.com/v1.0";

    /** Refreshed early, so a token never expires mid-flight. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(2);

    private final EmailProperties properties;
    private final AzureAdProperties azure;
    private final GraphMessageFactory messageFactory;
    private final RestClient http;

    private volatile String cachedToken;
    private volatile Instant cachedUntil = Instant.EPOCH;

    public GraphEmailService(EmailProperties properties, AzureAdProperties azure,
                             GraphMessageFactory messageFactory, RestClient.Builder builder) {
        this.properties = properties;
        this.azure = azure;
        this.messageFactory = messageFactory;
        this.http = builder.build();

        if (azure.getClientSecret() == null || azure.getClientSecret().isBlank()) {
            throw new IllegalStateException(
                    "app.email.provider=graph needs security.azure.client-secret (AZURE_CLIENT_SECRET). "
                            + "Sending as a mailbox is the application acting on its own behalf, which "
                            + "cannot be done without it.");
        }
        log.info("Graph email transport sending as {}", properties.resolvedGraphSender());
    }

    @Override
    public void send(EmailMessage message) {
        String sender = properties.resolvedGraphSender();
        try {
            http.post()
                    .uri(GRAPH + "/users/" + URLEncoder.encode(sender, StandardCharsets.UTF_8) + "/sendMail")
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(messageFactory.sendMailBody(message))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email '{}' sent as {} to {}", message.subject(), sender, message.toAddress());
        } catch (RuntimeException e) {
            log.error("Graph could not send '{}' to {}: {}", message.subject(), message.toAddress(),
                    e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED",
                    "The email could not be delivered. Please retry.");
        }
    }

    /**
     * A client-credentials token for Graph, cached until shortly before it
     * expires.
     *
     * <p>Tokens last an hour; fetching one per email would add a round trip to
     * every send and hammer the token endpoint for no benefit.
     */
    private String accessToken() {
        String token = cachedToken;
        if (token != null && Instant.now().isBefore(cachedUntil)) {
            return token;
        }
        synchronized (this) {
            if (cachedToken != null && Instant.now().isBefore(cachedUntil)) {
                return cachedToken;
            }
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", azure.getClientId());
            form.add("client_secret", azure.getClientSecret());
            form.add("grant_type", "client_credentials");
            form.add("scope", "https://graph.microsoft.com/.default");

            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.post()
                    .uri(azure.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);

            if (response == null || response.get("access_token") == null) {
                throw new IllegalStateException("Microsoft returned no access token for Graph");
            }
            cachedToken = String.valueOf(response.get("access_token"));
            long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
            cachedUntil = Instant.now().plusSeconds(expiresIn).minus(EXPIRY_MARGIN);
            return cachedToken;
        }
    }

    @Override
    public String providerName() {
        return "graph";
    }
}
