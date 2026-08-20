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

    private final Upload upload = new Upload();
    private final Seed seed = new Seed();
    private final Cors cors = new Cors();

    public String buildPortalUrl(String rawToken) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + portalPath.replace("{token}", rawToken);
    }

    @Getter
    @Setter
    public static class Upload {
        private long maxFileSizeBytes = 10L * 1024 * 1024;
        private List<String> allowedContentTypes = List.of(
                "application/pdf", "image/png", "image/jpeg", "image/jpg", "image/webp");
        private List<String> allowedExtensions = List.of("pdf", "png", "jpg", "jpeg", "webp");
    }

    @Getter
    @Setter
    public static class Seed {
        /** Creates the development HR user on startup when true. */
        private boolean enabled = true;
        private String hrEmail = "admin@cloudfuze.com";
        private String hrPassword = "";
        private String hrName = "CloudFuze HR Admin";
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
