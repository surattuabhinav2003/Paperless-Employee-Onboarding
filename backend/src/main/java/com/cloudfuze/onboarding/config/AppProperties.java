package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Application level configuration. Every value is externalised - nothing hardcoded. */
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    /** Public base URL of the React app, used to build the candidate portal link. */
    private String frontendUrl = "http://localhost:5173";

    /** Path template appended to frontendUrl. {token} is replaced with the raw token. */
    private String portalPath = "/upload/{token}";

    /** How long a candidate portal token stays valid. */
    private Duration portalTokenTtl = Duration.ofDays(14);

    /** Path template for the NDA + NOC signing link. {token} is replaced. */
    private String nocPath = "/noc/{token}";

    /** How long an NDA + NOC signing link stays valid. */
    private Duration nocTokenTtl = Duration.ofDays(14);

    /**
     * Domains an NDA + NOC may be sent to. These documents go to company
     * Microsoft accounts, not personal addresses, so anything outside this list
     * is refused. Empty means no restriction.
     */
    private List<String> nocRecipientDomains = List.of("cloudfuze.com");

    /** Whether the given address may receive an NDA + NOC. */
    public boolean isAllowedNocRecipient(String email) {
        if (nocRecipientDomains == null || nocRecipientDomains.isEmpty()) {
            return true;
        }
        String address = email == null ? "" : email.trim().toLowerCase();
        int at = address.lastIndexOf('@');
        if (at < 0 || at == address.length() - 1) {
            return false;
        }
        String domain = address.substring(at + 1);
        return nocRecipientDomains.stream()
                .map(d -> d.trim().toLowerCase())
                .anyMatch(domain::equals);
    }

    private final Upload upload = new Upload();
    private final Seed seed = new Seed();
    private final Cors cors = new Cors();

    public String buildPortalUrl(String rawToken) {
        return buildFrontendUrl(portalPath, rawToken);
    }

    public String buildNocUrl(String rawToken) {
        return buildFrontendUrl(nocPath, rawToken);
    }

    private String buildFrontendUrl(String pathTemplate, String rawToken) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + pathTemplate.replace("{token}", rawToken);
    }

    @Getter
    @Setter
    public static class Upload {
        private long maxFileSizeBytes = 10L * 1024 * 1024;
        private List<String> allowedContentTypes = List.of(
                "application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp",
                // Word: .docx, plus legacy .doc so the converter can explain itself.
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword");
        private List<String> allowedExtensions = List.of("pdf", "png", "jpg", "jpeg", "webp", "doc", "docx");
    }

    @Getter
    @Setter
    public static class Seed {
        /** Creates the development HR user on startup when true. */
        private boolean enabled = true;
        private String hrEmail = "admin@cloudfuze.com";
        private String hrPassword = "";
        private String hrName = "CloudFuze HR Admin";

        /**
         * Who is an administrator. Applied on startup to existing users and on
         * first Microsoft sign-in for new ones, so an admin does not have to be
         * granted by hand after every provision.
         */
        private List<String> adminEmails = List.of();

        public boolean isAdminEmail(String email) {
            if (adminEmails == null || email == null) {
                return false;
            }
            String normalised = email.trim().toLowerCase();
            return adminEmails.stream().map(e -> e.trim().toLowerCase()).anyMatch(normalised::equals);
        }
        private String hrJobTitle = "HR Operations";
        /** Creates a handful of demo candidates when true. */
        private boolean sampleCandidates = false;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:4173");
    }
}
