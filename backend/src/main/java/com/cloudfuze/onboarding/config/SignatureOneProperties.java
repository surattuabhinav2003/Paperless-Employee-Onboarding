package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "signatureone")
@Getter
@Setter
public class SignatureOneProperties {

    /** mock | http. Use http once real SignatureOne credentials are available. */
    private String provider = "mock";

    private String baseUrl = "";
    private String apiKey = "";
    private String accountId = "";
    private String webhookSecret = "";
    private Duration requestTimeout = Duration.ofSeconds(20);

    /** Callback the provider redirects the signer back to after signing. */
    private String returnUrl = "";

    private final Mock mock = new Mock();

    @Getter
    @Setter
    public static class Mock {
        /** Base URL used to fabricate a signing session link in development. */
        private String signingBaseUrl = "https://sandbox.signatureone.local/sign";
        /** When true the mock completes signing synchronously on submit. */
        private boolean completeOnSubmit = true;
    }
}
