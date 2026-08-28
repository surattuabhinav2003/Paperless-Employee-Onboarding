package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an {@link EmailMessage} into the JSON body Graph's sendMail expects.
 *
 * <p>Separate from the transport so the shape of the request can be tested
 * without a tenant, a secret or a network. The parts worth being sure of - that
 * the archive copy is blind, that the logo is inline rather than an attachment
 * the reader has to open, that the message is saved to Sent Items - are all
 * decided here.
 */
@Component
public class GraphMessageFactory {

    private final EmailProperties properties;
    private final EmailArchive archive;

    public GraphMessageFactory(EmailProperties properties, EmailArchive archive) {
        this.properties = properties;
        this.archive = archive;
    }

    /** The full request body for {@code POST /users/{sender}/sendMail}. */
    public Map<String, Object> sendMailBody(EmailMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message(message));
        /*
         * The whole point of sending through Graph rather than a relay. False
         * here and the mail still arrives, but never appears in the sending
         * mailbox's Sent Items - which is exactly the gap this transport exists
         * to close.
         */
        body.put("saveToSentItems", true);
        return body;
    }

    private Map<String, Object> message(EmailMessage message) {
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("subject", message.subject());
        mail.put("body", Map.of("contentType", "HTML", "content", message.htmlBody()));
        mail.put("toRecipients", List.of(recipient(message.toAddress(), message.toName())));

        archive.recipientFor(message).ifPresent(bcc ->
                // Blind: a candidate reading their offer letter should not find
                // a second address on it, nor be able to reply to all.
                mail.put("bccRecipients", List.of(recipient(bcc, null))));

        if (properties.getReplyTo() != null && !properties.getReplyTo().isBlank()) {
            mail.put("replyTo", List.of(recipient(properties.getReplyTo(), null)));
        }

        List<Map<String, Object>> attachments = inlineLogo(message);
        if (!attachments.isEmpty()) {
            mail.put("attachments", attachments);
        }
        return mail;
    }

    /**
     * The logo, as an inline attachment referenced by the HTML.
     *
     * <p>{@code isInline} plus a contentId is what stops it showing as a
     * paperclip the reader has to open, and is how {@code cid:} in the body
     * resolves. Without both it is a file attached to every email.
     */
    private List<Map<String, Object>> inlineLogo(EmailMessage message) {
        if (message.htmlBody() == null || !message.htmlBody().contains("cid:" + MailLayout.LOGO_CID)) {
            return List.of();
        }
        ClassPathResource logo = new ClassPathResource("email/neutara-logo.png");
        if (!logo.exists()) {
            return List.of();
        }
        try (var in = logo.getInputStream()) {
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("@odata.type", "#microsoft.graph.fileAttachment");
            attachment.put("name", "neutara-logo.png");
            attachment.put("contentType", "image/png");
            attachment.put("contentId", MailLayout.LOGO_CID);
            attachment.put("isInline", true);
            attachment.put("contentBytes", Base64.getEncoder().encodeToString(in.readAllBytes()));
            List<Map<String, Object>> attachments = new ArrayList<>();
            attachments.add(attachment);
            return attachments;
        } catch (IOException e) {
            // The alt text already reads "Neutara"; a missing logo is not a
            // reason to fail an offer letter.
            return List.of();
        }
    }

    private static Map<String, Object> recipient(String address, String name) {
        Map<String, Object> emailAddress = new LinkedHashMap<>();
        emailAddress.put("address", address);
        if (name != null && !name.isBlank()) {
            emailAddress.put("name", name);
        }
        return Map.of("emailAddress", emailAddress);
    }
}
