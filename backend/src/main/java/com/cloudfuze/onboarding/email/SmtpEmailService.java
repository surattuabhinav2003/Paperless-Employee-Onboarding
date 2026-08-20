package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

    public SmtpEmailService(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
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
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mailSender.send(mime);
            log.info("Invitation email delivered to {}", message.toAddress());
        } catch (UnsupportedEncodingException | org.springframework.mail.MailException | jakarta.mail.MessagingException e) {
            // Delivery failure must not roll back the onboarding record; HR can resend.
            log.error("Failed to send email to {}: {}", message.toAddress(), e.getMessage(), e);
            throw new com.cloudfuze.onboarding.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED",
                    "The invitation email could not be delivered. Please retry or resend from the pipeline.");
        }
    }

    @Override
    public String providerName() {
        return "smtp";
    }
}
