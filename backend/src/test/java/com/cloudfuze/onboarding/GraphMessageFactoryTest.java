package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.GraphMessageFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request Graph is asked to send.
 *
 * <p>Worth pinning because every one of these is invisible once the mail has
 * gone: whether a copy was filed in Sent Items, whether the archive recipient
 * was blind or visible, whether the logo rides inline or as a paperclip. None
 * of it shows in the rendered body, and a live tenant is a slow and expensive
 * place to discover you got it wrong.
 */
class GraphMessageFactoryTest {

    private static final String ARCHIVE = "aditya.rompella@neutara.com";

    private EmailProperties properties;
    private GraphMessageFactory factory;

    @BeforeEach
    void setUp() {
        properties = new EmailProperties();
        properties.setFromAddress("onboarding@cloudfuze.com");
        properties.setReplyTo("aditya.rompella@neutara.com");
        properties.setArchiveAddress(ARCHIVE);
        factory = new GraphMessageFactory(properties);
    }

    private Map<String, Object> bodyFor(String to, String html) {
        return factory.sendMailBody(new EmailMessage(to, "Priya Sharma",
                "Your offer letter is ready", "plain text", html));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> message(Map<String, Object> body) {
        return (Map<String, Object>) body.get("message");
    }

    @SuppressWarnings("unchecked")
    private static List<String> addresses(Map<String, Object> message, String field) {
        List<Map<String, Map<String, String>>> list =
                (List<Map<String, Map<String, String>>>) message.get(field);
        return list == null ? List.of()
                : list.stream().map(entry -> entry.get("emailAddress").get("address")).toList();
    }

    @Test
    @DisplayName("Every message is saved to the sending mailbox's Sent Items")
    void savesToSentItems() {
        // The whole reason this transport exists rather than SMTP.
        assertThat(bodyFor("priya@example.com", "<p>hi</p>")).containsEntry("saveToSentItems", true);
    }

    @Test
    @DisplayName("The archive copy is blind, never a visible recipient")
    void archiveIsBlindCopied() {
        Map<String, Object> message = message(bodyFor("priya@example.com", "<p>hi</p>"));

        assertThat(addresses(message, "toRecipients")).containsExactly("priya@example.com");
        assertThat(addresses(message, "bccRecipients")).containsExactly(ARCHIVE);
        assertThat(addresses(message, "ccRecipients")).isEmpty();
    }

    @Test
    @DisplayName("Someone already addressed is not also blind-copied")
    void noDuplicateForTheAddressee() {
        Map<String, Object> message = message(bodyFor("Aditya.Rompella@Neutara.com", "<p>hi</p>"));

        assertThat(addresses(message, "bccRecipients")).isEmpty();
    }

    @Test
    @DisplayName("A verification code is never copied, however the archive is set")
    void verificationCodesAreNeverArchived() {
        Map<String, Object> message = message(factory.sendMailBody(
                new EmailMessage("priya@example.com", "Priya Sharma",
                        "418206 is your Neutara onboarding code", "text", "<p>418206</p>")
                        .notArchivable()));

        // The second factor must not be posted to a third mailbox.
        assertThat(message).doesNotContainKey("bccRecipients");
    }

    @Test
    @DisplayName("With no archive configured, nobody is copied")
    void archiveIsOptional() {
        properties.setArchiveAddress("");

        assertThat(message(bodyFor("priya@example.com", "<p>hi</p>"))).doesNotContainKey("bccRecipients");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("The logo rides inline, not as a paperclip the reader has to open")
    void logoIsInline() {
        Map<String, Object> message = message(
                bodyFor("priya@example.com", "<img src=\"cid:neutara-logo\" alt=\"Neutara\" />"));

        List<Map<String, Object>> attachments = (List<Map<String, Object>>) message.get("attachments");
        assertThat(attachments).hasSize(1);

        Map<String, Object> logo = attachments.get(0);
        // isInline and a contentId together are what make cid: resolve; without
        // both it is a file attached to every email the portal sends.
        assertThat(logo).containsEntry("isInline", true)
                .containsEntry("contentId", "neutara-logo")
                .containsEntry("contentType", "image/png");
        assertThat((String) logo.get("contentBytes")).isNotBlank();
    }

    @Test
    @DisplayName("A message that does not reference the logo carries no attachment")
    void noAttachmentWhenUnreferenced() {
        assertThat(message(bodyFor("priya@example.com", "<p>no logo here</p>")))
                .doesNotContainKey("attachments");
    }

    @Test
    @DisplayName("Replies go to the support address, not to the sending mailbox")
    void replyToIsSet() {
        Map<String, Object> message = message(bodyFor("priya@example.com", "<p>hi</p>"));

        assertThat(addresses(message, "replyTo")).containsExactly("aditya.rompella@neutara.com");
    }
}
