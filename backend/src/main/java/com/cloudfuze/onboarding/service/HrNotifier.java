package com.cloudfuze.onboarding.service;

import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.email.EmailService;
import com.cloudfuze.onboarding.email.HrNotificationComposer;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.NocPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends HR the three notifications worth interrupting them for.
 *
 * <p>Every method is fire-and-forget: a notification that fails must never
 * break the thing it is reporting on. A candidate who has signed their offer
 * has signed it, whether or not HR's copy of that news got through.
 */
@Service
public class HrNotifier {

    private static final Logger log = LoggerFactory.getLogger(HrNotifier.class);

    private final EmailService emailService;
    private final EmailProperties properties;
    private final HrNotificationComposer composer;

    public HrNotifier(EmailService emailService, EmailProperties properties,
                      HrNotificationComposer composer) {
        this.emailService = emailService;
        this.properties = properties;
        this.composer = composer;
    }

    @Async
    public void candidateSubmitted(Candidate candidate, int documentCount) {
        send(() -> composer.candidateSubmitted(candidate, documentCount),
                "candidate submitted", candidate.getEmail());
    }

    @Async
    public void offerSigned(Candidate candidate, String signedByName) {
        send(() -> composer.offerSigned(candidate, signedByName), "offer signed", candidate.getEmail());
    }

    @Async
    public void nocSigned(NocPacket packet) {
        send(() -> composer.nocSigned(packet), "NDA + NOC signed", packet.getRecipientEmail());
    }

    private void send(java.util.function.Supplier<com.cloudfuze.onboarding.email.EmailMessage> build,
                      String what, String about) {
        if (!properties.hrNotificationsEnabled()) {
            return;
        }
        try {
            emailService.send(build.get());
            log.info("Notified HR: {} ({})", what, about);
        } catch (RuntimeException e) {
            log.error("Could not notify HR of '{}' for {}: {}", what, about, e.getMessage());
        }
    }
}
