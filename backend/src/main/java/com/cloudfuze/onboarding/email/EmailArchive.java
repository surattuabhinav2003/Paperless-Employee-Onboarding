package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Who, if anyone, gets a blind copy of a message.
 *
 * <p>The rule lived in three places - the SMTP transport, the Graph request
 * builder, and the development log - and the log had only part of it, so it
 * printed a Bcc on messages that were never actually copied. Anyone reading a
 * dev log to check who receives what was being told the wrong thing.
 *
 * <p>One definition now, and every transport asks it the same question.
 */
@Component
public class EmailArchive {

    private final EmailProperties properties;

    public EmailArchive(EmailProperties properties) {
        this.properties = properties;
    }

    /**
     * The address to blind-copy, or empty when nobody should be.
     *
     * <p>Three reasons to skip: no archive is configured; the message opted out
     * (a verification code, which must not be posted to a third mailbox); or
     * they are already the addressee, in which case a copy would just deliver
     * the same mail twice.
     */
    public Optional<String> recipientFor(EmailMessage message) {
        if (!properties.archiveEnabled() || message == null || !message.archivable()) {
            return Optional.empty();
        }
        String archive = properties.getArchiveAddress().trim();
        String addressee = String.valueOf(message.toAddress()).trim();
        return archive.equalsIgnoreCase(addressee) ? Optional.empty() : Optional.of(archive);
    }

    /** How the log should describe the copy, so dev output matches what is sent. */
    public String describeFor(EmailMessage message) {
        return recipientFor(message).orElse("(none)");
    }
}
