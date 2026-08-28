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

    private String fromAddress = "onboarding@neutara.com";
    private String fromName = "Neutara Onboarding";
    private String replyTo = "Aditya.Rompella@neutara.com";
    private String supportContact = "Aditya.Rompella@neutara.com";

    /**
     * Where HR event notifications go - a candidate submitting their pack, an
     * offer or an NDA + NOC being signed. Blank turns them off entirely.
     */
    private String hrNotifyAddress = "Aditya.Rompella@neutara.com";

    private String hrNotifyName = "Neutara HR";

    public boolean hrNotificationsEnabled() {
        return hrNotifyAddress != null && !hrNotifyAddress.isBlank();
    }
}
