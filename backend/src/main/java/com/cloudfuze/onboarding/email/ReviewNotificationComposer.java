package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.CandidateDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the single email HR sends when they finish reviewing a candidate's
 * documents.
 *
 * <p>One email, not one per document: HR reviews everything at their own pace and
 * then notifies once, so the candidate gets a complete picture instead of a
 * stream of separate messages. There are two shapes - a list of documents that
 * need re-uploading, or an all-approved note.
 */
@Component
public class ReviewNotificationComposer {

    private final EmailProperties properties;

    public ReviewNotificationComposer(EmailProperties properties) {
        this.properties = properties;
    }

    /** Everything approved - the candidate can move on to their offer letter. */
    public EmailMessage approved(Candidate candidate, String portalUrl) {
        String subject = "Your Neutara documents are approved";
        String text = """
                Hi %s,

                Good news - HR has reviewed and approved all of your documents.

                There is nothing more you need to upload. Your offer letter is the next
                step, and you will be able to review and accept it from your onboarding
                portal:

                %s

                We will email you as soon as your offer letter is ready.

                Need help? Reply to this email or contact %s.

                Warm regards,
                Neutara People Operations
                """.formatted(candidate.getName(), portalUrl, properties.getSupportContact());

        String html = shell(
                "Your documents are approved",
                "#1c8a5c",
                """
                <p style="font-size:14px;color:#46536e;line-height:1.6">Hi %s,</p>
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  Good news - HR has reviewed and <b style="color:#1c8a5c">approved all of your
                  documents</b>. There is nothing more for you to upload.
                </p>
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  Your offer letter is the next step. We will email you as soon as it is ready,
                  and you will be able to review and accept it from your onboarding portal.
                </p>
                %s
                """.formatted(candidate.getName(), button(portalUrl, "Open my portal")));

        return new EmailMessage(candidate.getEmail(), candidate.getName(), subject, text, html);
    }

    /**
     * One or more documents need a fresh copy. Lists each one and HR's reason so
     * the candidate knows exactly what to fix.
     */
    public EmailMessage reupload(Candidate candidate, List<CandidateDocument> rejected, String portalUrl) {
        int n = rejected.size();
        String subject = n == 1
                ? "Action needed: re-upload 1 Neutara document"
                : "Action needed: re-upload " + n + " Neutara documents";

        String textList = rejected.stream()
                .map(d -> "  - " + d.getDocumentType().getLabel()
                        + (d.getRejectReason() == null ? "" : "\n      Reason: " + d.getRejectReason()))
                .collect(Collectors.joining("\n"));

        String text = """
                Hi %s,

                HR has reviewed your documents and needs %s re-uploaded before your
                onboarding can continue:

                %s

                Open your onboarding portal to upload a new copy of the document%s above:
                %s

                Once you re-upload, it goes straight back to HR - there is nothing else to
                submit. We will email you when the next step is ready.

                Need help? Reply to this email or contact %s.

                Warm regards,
                Neutara People Operations
                """.formatted(candidate.getName(), n == 1 ? "1 document" : n + " documents",
                textList, n == 1 ? "" : "s", portalUrl, properties.getSupportContact());

        String htmlList = rejected.stream()
                .map(d -> """
                        <li style="margin-bottom:10px">
                          <span style="font-weight:600;color:#0b1533">%s</span>%s
                        </li>
                        """.formatted(
                        d.getDocumentType().getLabel(),
                        d.getRejectReason() == null ? ""
                                : "<div style=\"font-size:13px;color:#8a5a12;margin-top:2px\">Reason: "
                                  + escape(d.getRejectReason()) + "</div>"))
                .collect(Collectors.joining());

        String html = shell(
                n == 1 ? "1 document needs re-uploading" : n + " documents need re-uploading",
                "#b57912",
                """
                <p style="font-size:14px;color:#46536e;line-height:1.6">Hi %s,</p>
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  HR has reviewed your documents and needs the following re-uploaded before
                  your onboarding can continue:
                </p>
                <ul style="font-size:14px;color:#46536e;line-height:1.6;padding-left:18px">%s</ul>
                <p style="font-size:14px;color:#46536e;line-height:1.6">
                  Open your portal to upload a new copy. Once you do, it goes straight back to
                  HR - there is nothing else to submit.
                </p>
                %s
                """.formatted(candidate.getName(), htmlList, button(portalUrl, "Re-upload my document"
                        + (n == 1 ? "" : "s"))));

        return new EmailMessage(candidate.getEmail(), candidate.getName(), subject, text, html);
    }

    private String shell(String heading, String accent, String body) {
        return """
                <div style="font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;background:#f4f6fb;padding:32px">
                  <div style="max-width:600px;margin:0 auto;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e4e9f5">
                    <div style="background:#174F96;padding:24px 28px;color:#ffffff">
                      <div style="font-size:20px;font-weight:600;letter-spacing:-0.2px">Neutara</div>
                      <div style="font-size:13px;opacity:0.85;margin-top:2px">People Operations &middot; Onboarding</div>
                    </div>
                    <div style="padding:26px 28px">
                      <div style="font-size:17px;font-weight:600;color:%s;margin-bottom:14px">%s</div>
                      %s
                      <p style="font-size:12.5px;color:#9aa5bd;line-height:1.6;margin-top:22px">
                        This link is personal to you. Neutara will never ask for your password or
                        payment details during onboarding.
                      </p>
                    </div>
                  </div>
                </div>
                """.formatted(accent, heading, body);
    }

    private String button(String url, String label) {
        return """
                <p style="margin:22px 0">
                  <a href="%s" style="display:inline-block;background:#174F96;color:#ffffff;
                     text-decoration:none;font-size:14px;font-weight:600;padding:11px 22px;border-radius:8px">%s</a>
                </p>
                """.formatted(url, label);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
