package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.NocPacket;
import org.springframework.stereotype.Component;

/**
 * The emails HR gets, as opposed to the ones candidates get.
 *
 * <p>Deliberately only three moments: a candidate hands over their pack, an
 * offer is signed, an NDA + NOC is signed. Each one either needs HR to act or
 * closes something out - anything more would be noise, and HR already has the
 * console for browsing.
 *
 * <p>Every message links straight to the record rather than describing it, so
 * the mail is a prompt to act rather than a report to read.
 */
@Component
public class HrNotificationComposer {

    private final EmailProperties properties;
    private final AppProperties appProperties;

    public HrNotificationComposer(EmailProperties properties, AppProperties appProperties) {
        this.properties = properties;
        this.appProperties = appProperties;
    }

    /** A candidate has submitted their documents and details for review. */
    public EmailMessage candidateSubmitted(Candidate candidate, int documentCount) {
        String subject = candidate.getName() + " submitted their onboarding documents";
        String link = consoleUrl("/candidates/" + candidate.getId());

        String text = """
                %s has submitted their onboarding pack for review.

                  Candidate  : %s <%s>
                  Role       : %s, %s
                  Documents  : %d uploaded

                Review them here:
                %s

                Neutara Onboarding
                """.formatted(candidate.getName(), candidate.getName(), candidate.getEmail(),
                candidate.getRole(), candidate.getDepartment(), documentCount, link);

        String html = shell("Ready for your review", "#1d63b8", """
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  <b>%s</b> has submitted their onboarding pack.
                </p>
                %s
                %s
                """.formatted(escape(candidate.getName()),
                facts(candidate, documentCount + " uploaded"),
                button(link, "Review documents")));

        return to(subject, text, html);
    }

    /** The candidate signed their offer letter - onboarding is complete. */
    public EmailMessage offerSigned(Candidate candidate, String signedByName) {
        String subject = candidate.getName() + " signed their offer letter";
        String link = consoleUrl("/offers/" + candidate.getId());

        String text = """
                %s has signed their offer letter. Their onboarding is now complete.

                  Candidate  : %s <%s>
                  Role       : %s, %s
                  Signed by  : %s

                The signed copy is on their record:
                %s

                Neutara Onboarding
                """.formatted(candidate.getName(), candidate.getName(), candidate.getEmail(),
                candidate.getRole(), candidate.getDepartment(), signedByName, link);

        String html = shell("Offer signed", "#1c8a5c", """
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  <b>%s</b> has signed their offer letter. Their onboarding is complete.
                </p>
                %s
                %s
                """.formatted(escape(candidate.getName()),
                facts(candidate, "signed by " + escape(signedByName)),
                button(link, "Open the signed offer")));

        return to(subject, text, html);
    }

    /** A combined NDA + NOC came back signed. */
    public EmailMessage nocSigned(NocPacket packet) {
        String what = packet.getTitle() == null ? "NDA and NOC" : packet.getTitle();
        String subject = packet.getRecipientName() + " signed the " + what;
        String link = consoleUrl("/noc/record/" + packet.getId());

        String text = """
                %s has signed the %s.

                  Recipient  : %s <%s>
                  Document   : %s + %s (%d pages)
                  Signed by  : %s

                Download the signed copy:
                %s

                Neutara Onboarding
                """.formatted(packet.getRecipientName(), what, packet.getRecipientName(),
                packet.getRecipientEmail(), packet.getNdaFilename(), packet.getNocFilename(),
                packet.getPageCount(), packet.getSignedByName(), link);

        String html = shell("Document signed", "#1c8a5c", """
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  <b>%s</b> has signed the %s.
                </p>
                <table style="font-size:13px;color:#46536e;border-collapse:collapse;margin:14px 0">
                  <tr><td style="padding:3px 14px 3px 0;color:#9aa5bd">Recipient</td><td>%s</td></tr>
                  <tr><td style="padding:3px 14px 3px 0;color:#9aa5bd">Document</td><td>%d pages</td></tr>
                  <tr><td style="padding:3px 14px 3px 0;color:#9aa5bd">Signed by</td><td>%s</td></tr>
                </table>
                %s
                """.formatted(escape(packet.getRecipientName()), escape(what),
                escape(packet.getRecipientEmail()), packet.getPageCount(),
                escape(packet.getSignedByName()), button(link, "Download signed copy")));

        return to(subject, text, html);
    }

    // ------------------------------------------------------------- internals

    private EmailMessage to(String subject, String text, String html) {
        return new EmailMessage(properties.getHrNotifyAddress(), properties.getHrNotifyName(),
                subject, text, html);
    }

    /** A link into the HR console, which lives on the same host as the portal. */
    private String consoleUrl(String path) {
        String base = appProperties.getFrontendUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path;
    }

    private String facts(Candidate candidate, String extra) {
        return """
                <table style="font-size:13px;color:#46536e;border-collapse:collapse;margin:14px 0">
                  <tr><td style="padding:3px 14px 3px 0;color:#9aa5bd">Candidate</td><td>%s</td></tr>
                  <tr><td style="padding:3px 14px 3px 0;color:#9aa5bd">Role</td><td>%s, %s</td></tr>
                  <tr><td style="padding:3px 14px 3px 0;color:#9aa5bd">Documents</td><td>%s</td></tr>
                </table>
                """.formatted(escape(candidate.getEmail()), escape(candidate.getRole()),
                escape(candidate.getDepartment()), extra);
    }

    private String shell(String heading, String accent, String body) {
        return """
                <div style="font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f6fb;padding:32px">
                  <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e4e9f5">
                    <div style="background:#174F96;padding:22px 26px;color:#ffffff">
                      <div style="font-size:19px;font-weight:600;letter-spacing:-0.2px">Neutara</div>
                      <div style="font-size:13px;opacity:0.85;margin-top:2px">People Operations &middot; HR console</div>
                    </div>
                    <div style="padding:24px 26px">
                      <div style="font-size:17px;font-weight:600;color:%s;margin-bottom:12px">%s</div>
                      %s
                    </div>
                  </div>
                </div>
                """.formatted(accent, heading, body);
    }

    private String button(String url, String label) {
        return """
                <p style="margin:20px 0">
                  <a href="%s" style="display:inline-block;background:#174F96;color:#ffffff;
                     text-decoration:none;font-size:14px;font-weight:600;padding:11px 22px;border-radius:8px">%s</a>
                </p>
                """.formatted(url, label);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
