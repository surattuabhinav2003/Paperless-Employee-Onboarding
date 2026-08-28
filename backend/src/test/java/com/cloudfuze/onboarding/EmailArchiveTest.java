package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.email.EmailArchive;
import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.SmtpEmailService;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Filing a copy of every message with one person.
 *
 * <p>Invitations, review outcomes, offer letters, NDA + NOC requests and the HR
 * notifications all go to different addresses, so nobody held the full thread.
 * A blind copy gives one mailbox the lot.
 *
 * <p>Tested at the transport rather than through the flow, because the thing
 * that can quietly break is the envelope - who the message is actually
 * addressed to - and that never appears in the rendered body. A bcc that
 * silently stopped being attached would look identical from the outside.
 */
class EmailArchiveTest {

    private static final String ARCHIVE = "aditya.rompella@neutara.com";

    private JavaMailSender mailSender;
    private EmailProperties properties;
    private SmtpEmailService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        // A real MimeMessage, so the helper genuinely builds an envelope.
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new JavaMailSenderImpl().createMimeMessage());
        doNothing().when(mailSender).send(any(MimeMessage.class));

        properties = new EmailProperties();
        properties.setFromAddress("onboarding@cloudfuze.com");
        properties.setFromName("Neutara Onboarding");
        properties.setArchiveAddress(ARCHIVE);

        service = new SmtpEmailService(mailSender, properties, new EmailArchive(properties));
    }

    private MimeMessage sendTo(String recipient) throws Exception {
        service.send(new EmailMessage(recipient, "Priya Sharma", "Your offer letter is ready",
                "plain text", "<p>html</p>"));
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    private static String[] recipients(MimeMessage mime, Message.RecipientType type) throws Exception {
        var addresses = mime.getRecipients(type);
        return addresses == null ? new String[0]
                : Arrays.stream(addresses).map(Object::toString).toArray(String[]::new);
    }

    @Test
    @DisplayName("Every message is blind-copied to the archive address")
    void everyMessageIsArchived() throws Exception {
        MimeMessage mime = sendTo("priya.sharma@example.com");

        assertThat(recipients(mime, Message.RecipientType.TO)).containsExactly("priya.sharma@example.com");
        assertThat(recipients(mime, Message.RecipientType.BCC)).containsExactly(ARCHIVE);
    }

    @Test
    @DisplayName("The copy is blind, so a candidate never sees it and cannot reply to it")
    void theCopyIsBlind() throws Exception {
        MimeMessage mime = sendTo("priya.sharma@example.com");

        /*
         * On the offer letter of someone joining the company, a second visible
         * address is both a privacy leak and an invitation to reply-all. Being
         * on the Bcc list rather than To or Cc is what makes it invisible - the
         * SMTP transport drops the Bcc header on the way out, so it reaches the
         * archive without ever appearing on the candidate's copy.
         */
        assertThat(recipients(mime, Message.RecipientType.CC)).isEmpty();
        assertThat(recipients(mime, Message.RecipientType.TO)).doesNotContain(ARCHIVE);
    }

    @Test
    @DisplayName("An HR notification already addressed to them arrives once, not twice")
    void noDuplicateWhenTheyAreTheAddressee() throws Exception {
        MimeMessage mime = sendTo(ARCHIVE);

        assertThat(recipients(mime, Message.RecipientType.TO)).containsExactly(ARCHIVE);
        assertThat(recipients(mime, Message.RecipientType.BCC)).isEmpty();
    }

    @Test
    @DisplayName("Matching is case-insensitive, so a capitalised address is not copied twice")
    void addressComparisonIgnoresCase() throws Exception {
        MimeMessage mime = sendTo("Aditya.Rompella@Neutara.com");

        assertThat(recipients(mime, Message.RecipientType.BCC)).isEmpty();
    }

    @Test
    @DisplayName("A verification code is never copied, however the archive is set")
    void verificationCodesAreNeverArchived() throws Exception {
        service.send(new EmailMessage("priya.sharma@example.com", "Priya Sharma",
                "418206 is your Neutara onboarding code", "text", "<p>418206</p>").notArchivable());
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());

        /*
         * The code proves the candidate controls their own inbox, and the portal
         * link is in another email the archive recipient does receive. Copying
         * this one as well would hand them a working key to anyone's documents.
         */
        assertThat(recipients(sent.getValue(), Message.RecipientType.BCC)).isEmpty();
    }

    @Test
    @DisplayName("With no archive configured, nothing is copied anywhere")
    void archiveIsOptional() throws Exception {
        properties.setArchiveAddress("");

        MimeMessage mime = sendTo("priya.sharma@example.com");

        assertThat(recipients(mime, Message.RecipientType.BCC)).isEmpty();
    }
}
