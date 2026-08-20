package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
@Getter
@Setter
public class StorageProperties {

    /** local | s3 | azure-blob. Only local ships today; the rest plug into the same interface. */
    private String provider = "local";

    private final Local local = new Local();

    @Getter
    @Setter
    public static class Local {
        /** Root directory for uploaded files. Keep this outside the web root. */
        private String root = "./storage";
    }
}
