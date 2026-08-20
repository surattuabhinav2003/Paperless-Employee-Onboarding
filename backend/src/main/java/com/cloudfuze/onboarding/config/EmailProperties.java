package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
@Getter
@Setter
public class EmailProperties {

    /** log | smtp. log writes the rendered email to the application log. */
    private String provider = "log";

    private String fromAddress = "onboarding@cloudfuze.com";
    private String fromName = "CloudFuze Onboarding";
    private String replyTo = "hr@cloudfuze.com";
    private String supportContact = "hr@cloudfuze.com";
}
