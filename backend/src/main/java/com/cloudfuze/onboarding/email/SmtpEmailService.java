package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** Production transport. Credentials come from spring.mail.* environment values. */
@Service
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    private final EmailArchive archive;

    public SmtpEmailService(JavaMailSender mailSender, EmailProperties properties, EmailArchive archive) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.archive = archive;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.getFromAddress(), properties.getFromName());
            if (properties.getReplyTo() != null && !properties.getReplyTo().isBlank()) {
                helper.setReplyTo(properties.getReplyTo());
            }
            helper.setTo(message.toAddress());
            archive.recipientFor(message).ifPresent(bcc -> {
                try {
                    helper.setBcc(bcc);
                } catch (jakarta.mail.MessagingException e) {
                    // The archive copy is a convenience; the addressed recipient
                    // still gets their mail.
                    log.warn("Could not blind-copy {}: {}", bcc, e.getMessage());
                }
            });
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            attachLogo(helper, message);
            mailSender.send(mime);
            log.info("Email '{}' delivered to {}", message.subject(), message.toAddress());
        } catch (UnsupportedEncodingException | org.springframework.mail.MailException | jakarta.mail.MessagingException e) {
            // Delivery failure must not roll back the onboarding record; HR can resend.
            log.error("Failed to send email to {}: {}", message.toAddress(), e.getMessage(), e);
            throw new com.cloudfuze.onboarding.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED",
                    "The invitation email could not be delivered. Please retry or resend from the pipeline.");
        }
    }

    /**
     * Attaches the logo the layout referenced, if it referenced one.
     *
     * <p>Inline rather than hotlinked, so the mail needs no publicly reachable
     * host and opening it does not call home. Added after setText because
     * MimeMessageHelper builds the multipart in that order and an inline part
     * added first is dropped.
     *
     * <p>A missing logo file must not stop the mail: the alt text already reads
     * "Neutara", so the message is merely plainer, not lost.
     */
    private void attachLogo(MimeMessageHelper helper, EmailMessage message) {
        if (message.htmlBody() == null || !message.htmlBody().contains("cid:" + MailLayout.LOGO_CID)) {
            return;
        }
        ClassPathResource logo = new ClassPathResource("email/neutara-logo.png");
        if (!logo.exists()) {
            log.warn("Email logo email/neutara-logo.png is missing; sending without it");
            return;
        }
        try {
            helper.addInline(MailLayout.LOGO_CID, logo, "image/png");
        } catch (jakarta.mail.MessagingException e) {
            log.warn("Could not attach the email logo: {}", e.getMessage());
        }
    }

    @Override
    public String providerName() {
        return "smtp";
    }
}
