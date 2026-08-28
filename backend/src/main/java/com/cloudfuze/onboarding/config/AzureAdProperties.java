package com.cloudfuze.onboarding.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * Settings for "Sign in with Microsoft". Microsoft login is considered active
 * only when a tenant id is present, so a deployment without Entra configured
 * simply keeps the email/password path and nothing else.
 */
@ConfigurationProperties(prefix = "security.azure")
public class AzureAdProperties {

    private String tenantId = "";
    private String clientId = "";

    /**
     * Only needed to send mail as a mailbox in the tenant, via Graph.
     *
     * <p>Sign-in does not use it and never should: the browser proves who it is
     * with PKCE, and this server verifies the resulting token against the
     * tenant's public keys. A secret is required here because sending as a
     * mailbox is the application acting on its own behalf, with no user present.
     */
    private String clientSecret = "";
    private List<String> allowedHrEmails = List.of();
    private boolean autoProvision = true;
    private String defaultJobTitle = "HR";

    public boolean isEnabled() {
        return tenantId != null && !tenantId.isBlank()
                && clientId != null && !clientId.isBlank();
    }

    /** v2.0 issuer for this tenant - the only issuer we accept tokens from. */
    public String issuer() {
        return "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    }

    /** Where Microsoft publishes the signing keys for this tenant. */
    public String jwkSetUri() {
        return "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
    }

    /**
     * Whether this address is allowed to sign in. An empty allow-list trusts any
     * account the tenant itself authenticated; a non-empty one is an explicit
     * roster of HR addresses.
     */
    public boolean allows(String email) {
        if (allowedHrEmails == null || allowedHrEmails.isEmpty()) {
            return true;
        }
        String normalised = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return allowedHrEmails.stream()
                .map(a -> a.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalised::equals);
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /** The token endpoint for this tenant, used for client-credentials flow. */
    public String tokenUri() {
        return "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
    }

    public List<String> getAllowedHrEmails() {
        return allowedHrEmails;
    }

    public void setAllowedHrEmails(List<String> allowedHrEmails) {
        this.allowedHrEmails = allowedHrEmails;
    }

    public boolean isAutoProvision() {
        return autoProvision;
    }

    public void setAutoProvision(boolean autoProvision) {
        this.autoProvision = autoProvision;
    }

    public String getDefaultJobTitle() {
        return defaultJobTitle;
    }

    public void setDefaultJobTitle(String defaultJobTitle) {
        this.defaultJobTitle = defaultJobTitle;
    }
}
