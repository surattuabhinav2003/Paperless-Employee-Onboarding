package com.cloudfuze.onboarding.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
@Getter
@Setter
public class EmailProperties {

    /** log | smtp. log writes the rendered email to the application log. */
    private String provider = "log";

    private String fromAddress = "onboarding@neutara.com";
    private String fromName = "Neutara Onboarding";
    private String replyTo = "Aditya.Rompella@neutara.com";
    private String supportContact = "Aditya.Rompella@neutara.com";

    /**
     * Where HR event notifications go - a candidate submitting their pack, an
     * offer or an NDA + NOC being signed. Blank turns them off entirely.
     */
    private String hrNotifyAddress = "Aditya.Rompella@neutara.com";

    private String hrNotifyName = "Neutara HR";

    /**
     * Blind-copied on every email the portal sends, whoever it is addressed to.
     *
     * <p>So that one person holds the whole correspondence - invitations, review
     * outcomes, offer letters, NDA + NOC requests and the HR notifications alike
     * - rather than having to reconstruct it from the console. Blank turns it
     * off, which is the default.
     *
     * <p>Blind, not carbon: a candidate reading their offer letter should not
     * find a second address on it, and should certainly not be able to reply to
     * all. The archive copy is for the company's records, not part of the
     * conversation.
     */
    private String archiveAddress = "";

    /**
     * The mailbox Graph sends as, when the provider is {@code graph}.
     *
     * <p>Its Sent Items is where the copies land, so this is whoever should own
     * the correspondence. Blank falls back to the from-address.
     */
    private String graphSender = "";

    public boolean hrNotificationsEnabled() {
        return hrNotifyAddress != null && !hrNotifyAddress.isBlank();
    }

    /** Whether a copy of every message should be filed with someone. */
    public boolean archiveEnabled() {
        return archiveAddress != null && !archiveAddress.isBlank();
    }

    /** The mailbox to send as, falling back to whoever the mail claims to be from. */
    public String resolvedGraphSender() {
        return graphSender == null || graphSender.isBlank() ? fromAddress : graphSender.trim();
    }
}
