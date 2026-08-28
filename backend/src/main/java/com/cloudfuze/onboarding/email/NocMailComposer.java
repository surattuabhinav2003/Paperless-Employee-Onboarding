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

    public NocMailComposer(EmailProperties properties) {
        this.properties = properties;
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

        String html = """
                <div style="font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f6fb;padding:32px">
                  <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e4e9f5">
                    <div style="background:#174F96;padding:24px 28px;color:#ffffff">
                      <div style="font-size:20px;font-weight:600;letter-spacing:-0.2px">Neutara</div>
                      <div style="font-size:13px;opacity:0.85;margin-top:2px">People Operations &middot; Documents</div>
                    </div>
                    <div style="padding:26px 28px">
                      <div style="font-size:17px;font-weight:600;color:#174F96;margin-bottom:14px">
                        Please sign your %s
                      </div>
                      <p style="font-size:14px;color:#46536e;line-height:1.6">Hi %s,</p>
                      <p style="font-size:14px;color:#46536e;line-height:1.6">
                        Your NDA and NOC have been <b>combined into a single document</b>, so you only
                        need to go through it once - %d page(s) in total.
                      </p>
                      <p style="margin:22px 0">
                        <a href="%s" style="display:inline-block;background:#174F96;color:#ffffff;
                           text-decoration:none;font-size:14px;font-weight:600;padding:11px 22px;border-radius:8px">Review and sign</a>
                      </p>
                      <p style="font-size:13.5px;color:#46536e;line-height:1.7">
                        Fill in every highlighted field, review what you entered, then submit.
                        Your signed copy is available to download immediately afterwards.
                      </p>
                      <p style="font-size:12.5px;color:#9aa5bd;line-height:1.6;margin-top:22px">
                        This link is personal to you and stays valid until %s. Neutara will never ask
                        for your password or payment details.
                      </p>
                    </div>
                  </div>
                </div>
                """.formatted(escape(what), escape(packet.getRecipientName()), packet.getPageCount(),
                signingUrl, EXPIRY.format(expiresAt));

        return new EmailMessage(packet.getRecipientEmail(), packet.getRecipientName(), subject, text, html);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
