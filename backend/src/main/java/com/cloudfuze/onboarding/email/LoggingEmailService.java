package com.cloudfuze.onboarding.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.cloudfuze.onboarding.config.EmailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Development transport: writes the rendered email to the application log so the
 * onboarding flow is fully testable without SMTP credentials.
 */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "log", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    private final EmailProperties properties;

    public LoggingEmailService(EmailProperties properties) {
        this.properties = properties;
    }

    /* Shows the blind copy as well, so development does not give a misleading
       picture of who actually receives a message. */
    @Override
    public void send(EmailMessage message) {
        log.info("""

                        ==================== OUTBOUND EMAIL (dev transport) ====================
                        To      : {} <{}>
                        Bcc     : {}
                        Subject : {}
                        ------------------------------------------------------------------------
                        {}
                        ========================================================================""",
                message.toName(), message.toAddress(),
                properties.archiveEnabled() ? properties.getArchiveAddress() : "(none)",
                message.subject(), message.textBody());
    }

    @Override
    public String providerName() {
        return "log";
    }
}
