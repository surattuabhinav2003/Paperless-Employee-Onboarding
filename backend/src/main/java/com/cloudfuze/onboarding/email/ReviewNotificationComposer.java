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

    private final MailLayout layout;

    public ReviewNotificationComposer(EmailProperties properties, MailLayout layout) {
        this.properties = properties;
        this.layout = layout;
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

        String html = layout.page(
                "Nothing more to upload - your offer letter is the next step.",
                "Document review",
                "Your documents are approved",
                layout.p("Hi " + layout.escape(candidate.getName()) + ",")
                        + layout.p("HR has reviewed everything you sent and approved it. There is nothing "
                                + "more for you to upload.")
                        + layout.p("Your offer letter is the next step. We will email you as soon as it is "
                                + "ready to read and sign.")
                        + layout.button(portalUrl, "Open my portal")
                        + layout.fallbackLink(portalUrl)
                        + layout.note("Any questions? Reply to this email or contact "
                                + layout.escape(properties.getSupportContact()) + "."));

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

        String htmlList = layout.list(rejected.stream()
                .map(d -> layout.strong(d.getDocumentType().getLabel())
                        + (d.getRejectReason() == null ? ""
                        : "<br /><span style=\"color:#8b95a8\">" + layout.escape(d.getRejectReason()) + "</span>"))
                .toArray(String[]::new));

        String html = layout.page(
                n == 1 ? "One document needs a fresh copy before onboarding can continue."
                        : n + " documents need a fresh copy before onboarding can continue.",
                "Document review",
                n == 1 ? "One document needs re-uploading" : n + " documents need re-uploading",
                layout.p("Hi " + layout.escape(candidate.getName()) + ",")
                        + layout.p("HR has reviewed your documents. These ones need a fresh copy before "
                                + "your onboarding can continue:")
                        + layout.panel(htmlList)
                        + layout.p("Open your portal to upload a new copy. It goes straight back to HR - "
                                + "there is nothing else to submit.")
                        + layout.button(portalUrl, n == 1 ? "Re-upload my document" : "Re-upload my documents")
                        + layout.fallbackLink(portalUrl)
                        + layout.note("Any questions? Reply to this email or contact "
                                + layout.escape(properties.getSupportContact()) + "."));

        return new EmailMessage(candidate.getEmail(), candidate.getName(), subject, text, html);
    }

    /**
     * HR has released the offer letter, and the candidate can sign it.
     *
     * <p>The link goes straight to the signing page rather than the portal front
     * door: this mail exists to get one thing done, and every extra click
     * between it and the signature is a chance to put it off. The token in the
     * link is the same credential the rest of the portal uses, so nothing new is
     * exposed by pointing deeper into it.
     */
    public EmailMessage offerReady(Candidate candidate, String offerUrl) {
        String subject = "Your offer letter is ready to sign";

        String text = """
                Hi %s,

                Your offer letter is ready. You can read it and sign it here:

                %s

                The letter opens in your onboarding portal, where you can sign by drawing,
                typing or uploading your signature. A signed copy is saved to your record
                as soon as you are done.

                If you have any questions before signing, reply to this email or contact %s.

                Neutara People Operations
                """.formatted(candidate.getName(), offerUrl, properties.getSupportContact());

        String html = layout.page(
                "Read it, sign it, and a signed copy is filed for you.",
                "Offer letter",
                "Your offer letter is ready",
                layout.p("Hi " + layout.escape(candidate.getName()) + ",")
                        + layout.p("Your offer letter is ready to read and sign. You can sign by drawing, "
                                + "typing or uploading your signature, and a signed copy is saved to your "
                                + "record as soon as you are done.")
                        + layout.button(offerUrl, "Read and sign my offer")
                        + layout.fallbackLink(offerUrl)
                        + layout.note("Any questions before you sign? Reply to this email or contact "
                                + layout.escape(properties.getSupportContact()) + "."));

        return new EmailMessage(candidate.getEmail(), candidate.getName(), subject, text, html);
    }
}
