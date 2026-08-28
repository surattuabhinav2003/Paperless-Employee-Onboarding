package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.model.NocPacket;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The one email a recipient gets for their NDA + NOC.
 *
 * <p>It deliberately says "one document": the two files were combined before
 * sending, so promising a single signature pass is accurate.
 */
@Component
public class NocMailComposer {

    private static final DateTimeFormatter EXPIRY =
            DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm").withZone(ZoneId.systemDefault());

    private final EmailProperties properties;

    private final MailLayout layout;

    public NocMailComposer(EmailProperties properties, MailLayout layout) {
        this.properties = properties;
        this.layout = layout;
    }

    public EmailMessage compose(NocPacket packet, String signingUrl, Instant expiresAt) {
        String what = packet.getTitle() == null ? "NDA and NOC" : packet.getTitle();
        String subject = "Please sign your " + what;

        String text = """
                Hi %s,

                Your NDA and NOC are ready to sign. They have been combined into a single
                document, so you only need to go through it once.

                Open the document and sign it here:

                %s

                What to expect
                  - The whole document is %d page(s); scroll through all of it before signing.
                  - Fill in every highlighted field, then review and submit.
                  - You can download your signed copy straight afterwards.

                A few things to know
                  - This link is personal to you. Please do not forward it.
                  - The link stays valid until %s (local time).
                  - Neutara will never ask for your password or payment details.

                Need help? Reply to this email or contact %s.

                Warm regards,
                Neutara People Operations
                """.formatted(packet.getRecipientName(), signingUrl, packet.getPageCount(),
                EXPIRY.format(expiresAt), properties.getSupportContact());

        String html = layout.page(
                "Your NDA and NOC are combined into one document - sign both in one pass.",
                "For signature",
                "Please sign your " + what,
                layout.p("Hi " + layout.escape(packet.getRecipientName()) + ",")
                        + layout.p("Your NDA and NOC have been combined into "
                                + layout.strong("a single document") + ", so you only need to go through "
                                + "it once - " + packet.getPageCount() + " page"
                                + (packet.getPageCount() == 1 ? "" : "s") + " in total.")
                        + layout.button(signingUrl, "Review and sign")
                        + layout.fallbackLink(signingUrl)
                        + layout.p("Fill in every highlighted field, review what you entered, then submit. "
                                + "Your signed copy is available to download straight afterwards.")
                        + layout.note("This link is personal to you and stays valid until "
                                + layout.escape(EXPIRY.format(expiresAt)) + "."));

        return new EmailMessage(packet.getRecipientEmail(), packet.getRecipientName(), subject, text, html);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
