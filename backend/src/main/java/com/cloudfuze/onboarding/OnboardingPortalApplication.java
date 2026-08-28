package com.cloudfuze.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableTransactionManagement
@org.springframework.scheduling.annotation.EnableAsync
public class OnboardingPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnboardingPortalApplication.class, args);
    }
}
