package com.cloudfuze.onboarding.email;

import com.cloudfuze.onboarding.config.AppProperties;
import com.cloudfuze.onboarding.config.EmailProperties;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.NocPacket;
import org.springframework.stereotype.Component;

/**
 * The emails HR gets, as opposed to the ones candidates get.
 *
 * <p>Deliberately few moments: a candidate hands over their pack, they return
 * what was sent back, an offer is signed, an NDA + NOC is signed. Each one
 * either needs HR to act or closes something out - anything more would be
 * noise, and HR already has the console for browsing.
 *
 * <p>Every message links straight to the record rather than describing it, so
 * the mail is a prompt to act rather than a report to read.
 */
@Component
public class HrNotificationComposer {

    private final EmailProperties properties;
    private final AppProperties appProperties;

    private final MailLayout layout;

    public HrNotificationComposer(EmailProperties properties, AppProperties appProperties,
                                  MailLayout layout) {
        this.layout = layout;
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

        String html = layout.page(
                candidate.getName() + " has handed over their onboarding pack.",
                "Ready for review",
                candidate.getName() + " submitted their pack",
                layout.p(layout.strong(candidate.getName()) + " has submitted their onboarding pack "
                        + "for review.")
                        + candidateFacts(candidate, "Documents", documentCount + " uploaded")
                        + layout.button(link, "Review documents")
                        + layout.fallbackLink(link));

        return to(subject, text, html);
    }

    /**
     * The candidate has returned every document HR sent back.
     *
     * <p>Rejecting a document emails the candidate, and until now the journey
     * back was silent: HR had no way to know a replacement had arrived short of
     * opening the record and looking. This closes that loop.
     *
     * @param awaitingReview how many of their documents are now sitting in HR's
     *                       queue, so the mail says what is waiting rather than
     *                       making HR open the record to find out
     */
    public EmailMessage candidateResubmitted(Candidate candidate, int awaitingReview) {
        String noun = awaitingReview == 1 ? "document" : "documents";
        String what = awaitingReview + " " + noun;
        String subject = candidate.getName() + " re-sent documents for review";
        String link = consoleUrl("/candidates/" + candidate.getId());

        String text = """
                %s has replaced everything you sent back. Their pack is ready for review again.

                  Candidate  : %s <%s>
                  Role       : %s, %s
                  Waiting    : %s

                Review them here:
                %s

                Neutara Onboarding
                """.formatted(candidate.getName(), candidate.getName(), candidate.getEmail(),
                candidate.getRole(), candidate.getDepartment(), what, link);

        String html = layout.page(
                candidate.getName() + " has replaced everything you sent back.",
                "Back with you",
                candidate.getName() + " re-sent their documents",
                layout.p(layout.strong(candidate.getName()) + " has replaced everything you sent back, "
                        + "and nothing else is outstanding.")
                        + candidateFacts(candidate, "Waiting", what)
                        + layout.button(link, "Review documents")
                        + layout.fallbackLink(link));

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

        String html = layout.page(
                candidate.getName() + " has signed. Their onboarding is complete.",
                "Offer letter",
                candidate.getName() + " signed their offer",
                layout.p(layout.strong(candidate.getName()) + " has signed their offer letter. Their "
                        + "onboarding is now complete.")
                        + candidateFacts(candidate, "Signed by", signedByName)
                        + layout.button(link, "Open the signed offer")
                        + layout.fallbackLink(link));

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

        String html = layout.page(
                packet.getRecipientName() + " has signed and returned the document.",
                "NDA + NOC",
                packet.getRecipientName() + " signed the " + what,
                layout.p(layout.strong(packet.getRecipientName()) + " has signed the "
                        + layout.escape(what) + ".")
                        + layout.facts(
                                "Recipient", packet.getRecipientName() + " <" + packet.getRecipientEmail() + ">",
                                "Document", packet.getPageCount() + " pages",
                                "Signed by", String.valueOf(packet.getSignedByName()))
                        + layout.button(link, "Download the signed copy")
                        + layout.fallbackLink(link));

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

    /** The three facts every candidate email repeats, plus one for the occasion. */
    private String candidateFacts(Candidate candidate, String extraLabel, String extraValue) {
        return layout.facts(
                "Candidate", candidate.getName() + " <" + candidate.getEmail() + ">",
                "Role", candidate.getRole() + ", " + candidate.getDepartment(),
                extraLabel, extraValue);
    }
}
