package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.RequiredDocument;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Renders the single onboarding invitation email. There is exactly one email
 * template for the whole journey - documents, offer and bond all live behind the
 * same portal link.
 */
@Component
public class InvitationMailComposer {

    public enum Kind {
        INITIAL("Welcome to CloudFuze - complete your onboarding"),
        RESEND("Reminder: complete your CloudFuze onboarding"),
        REGENERATED("Your new CloudFuze onboarding link");

        private final String subject;

        Kind(String subject) {
            this.subject = subject;
        }

        public String subject() {
            return subject;
        }
    }

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final EmailProperties properties;

    public InvitationMailComposer(EmailProperties properties) {
        this.properties = properties;
    }

    public EmailMessage compose(Candidate candidate, String portalUrl, Instant expiresAt, Kind kind) {
        String expiry = EXPIRY_FORMAT.format(expiresAt);
        String documentList = candidate.getRequiredDocuments().stream()
                .map(rd -> "  - " + rd.displayName() + (rd.isMandatory() ? " (required)" : " (optional)"))
                .collect(Collectors.joining("\n"));

        String text = """
                Hi %s,

                Welcome to CloudFuze. Your onboarding for the %s role in %s is ready.

                Everything happens in one secure portal, in three steps:
                  1. Upload your documents
                  2. Review and accept your offer letter
                  3. Sign your employment bond

                Open your onboarding portal:
                %s

                Documents we need from you:
                %s

                A few things to know
                  - This link is personal to you. Please do not forward it.
                  - The link stays valid until %s (local time).
                  - Steps 2 and 3 unlock automatically as HR reviews your submissions.
                  - CloudFuze will never ask for your password or payment details during onboarding.

                Need help? Reply to this email or contact %s.

                Warm regards,
                CloudFuze People Operations
                """.formatted(candidate.getName(), candidate.getRole(), candidate.getDepartment(),
                portalUrl, documentList.isBlank() ? "  - (none listed)" : documentList,
                expiry, properties.getSupportContact());

        String documentRows = candidate.getRequiredDocuments().stream()
                .map(this::htmlDocumentRow)
                .collect(Collectors.joining());

        String html = """
                <div style="font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f6fb;padding:32px">
                  <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e4e9f5">
                    <div style="background:#0129AC;padding:24px 28px;color:#ffffff">
                      <div style="font-size:20px;font-weight:600;letter-spacing:-0.2px">CloudFuze</div>
                      <div style="font-size:13px;opacity:0.85;margin-top:2px">People Operations &middot; Onboarding</div>
                    </div>
                    <div style="padding:28px">
                      <p style="margin:0 0 14px;font-size:16px;color:#0f172a">Hi %s,</p>
                      <p style="margin:0 0 18px;font-size:14px;line-height:22px;color:#42506b">
                        Welcome to CloudFuze. Your onboarding for the <strong>%s</strong> role in
                        <strong>%s</strong> is ready. Everything happens in one secure portal.
                      </p>
                      <table style="width:100%%;border-collapse:collapse;margin:0 0 22px">
                        <tr>
                          <td style="font-size:13px;color:#42506b;padding:6px 0">1. Upload your documents</td>
                        </tr>
                        <tr>
                          <td style="font-size:13px;color:#42506b;padding:6px 0">2. Review and accept your offer letter</td>
                        </tr>
                        <tr>
                          <td style="font-size:13px;color:#42506b;padding:6px 0">3. Sign your employment bond</td>
                        </tr>
                      </table>
                      <a href="%s" style="display:inline-block;background:#0129AC;color:#ffffff;text-decoration:none;
                         padding:13px 26px;border-radius:8px;font-size:14px;font-weight:600">Open my onboarding portal</a>
                      <p style="margin:22px 0 8px;font-size:13px;font-weight:600;color:#0f172a">Documents we need</p>
                      <table style="width:100%%;border-collapse:collapse;font-size:13px;color:#42506b">%s</table>
                      <div style="margin-top:24px;padding:14px 16px;background:#f4f6fb;border-radius:10px;font-size:12px;color:#5b6883;line-height:19px">
                        This link is personal to you - please do not forward it. It stays valid until
                        <strong>%s</strong>. CloudFuze will never ask for your password or payment details
                        during onboarding. Need help? Contact %s.
                      </div>
                    </div>
                  </div>
                </div>
                """.formatted(candidate.getName(), candidate.getRole(), candidate.getDepartment(), portalUrl,
                documentRows, expiry, properties.getSupportContact());

        return new EmailMessage(candidate.getEmail(), candidate.getName(), kind.subject(), text, html);
    }

    private String htmlDocumentRow(RequiredDocument rd) {
        String badge = rd.isMandatory()
                ? "<span style=\"color:#0129AC;font-weight:600\">required</span>"
                : "<span style=\"color:#8792a8\">optional</span>";
        return "<tr><td style=\"padding:5px 0\">" + rd.displayName() + "</td>"
                + "<td style=\"padding:5px 0;text-align:right\">" + badge + "</td></tr>";
    }
}
