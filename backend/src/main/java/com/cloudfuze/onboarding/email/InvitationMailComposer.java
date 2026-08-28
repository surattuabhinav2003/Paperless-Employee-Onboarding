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
 * template for the whole journey - documents and offer both live behind the
 * same portal link.
 */
@Component
public class InvitationMailComposer {

    public enum Kind {
        INITIAL("Welcome to Neutara - complete your onboarding"),
        RESEND("Reminder: complete your Neutara onboarding"),
        REGENERATED("Your new Neutara onboarding link");

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

    private final MailLayout layout;

    public InvitationMailComposer(EmailProperties properties, MailLayout layout) {
        this.properties = properties;
        this.layout = layout;
    }

    public EmailMessage compose(Candidate candidate, String portalUrl, Instant expiresAt, Kind kind) {
        String expiry = EXPIRY_FORMAT.format(expiresAt);
        String documentList = candidate.getRequiredDocuments().stream()
                .map(rd -> "  - " + rd.displayName() + (rd.isMandatory() ? " (required)" : " (optional)"))
                .collect(Collectors.joining("\n"));

        String text = """
                Hi %s,

                Welcome to Neutara. Your onboarding for the %s role in %s is ready.

                Everything happens in one secure portal, in three steps:
                  1. Upload your documents
                  2. Review and accept your offer letter

                Open your onboarding portal:
                %s

                Documents we need from you:
                %s

                A few things to know
                  - This link is personal to you. Please do not forward it.
                  - The link stays valid until %s (local time).
                  - Steps 2 and 3 unlock automatically as HR reviews your submissions.
                  - Neutara will never ask for your password or payment details during onboarding.

                Need help? Reply to this email or contact %s.

                Warm regards,
                Neutara People Operations
                """.formatted(candidate.getName(), candidate.getRole(), candidate.getDepartment(),
                portalUrl, documentList.isBlank() ? "  - (none listed)" : documentList,
                expiry, properties.getSupportContact());

        String documentRows = layout.list(candidate.getRequiredDocuments().stream()
                .map(this::htmlDocumentRow)
                .toArray(String[]::new));

        String html = layout.page(
                "Upload your documents and sign your offer, all in one secure portal.",
                "Onboarding",
                "Welcome to Neutara, " + candidate.getName().split(" ")[0],
                layout.p("Your onboarding for the " + layout.strong(candidate.getRole()) + " role in "
                        + layout.strong(candidate.getDepartment()) + " is ready. Everything happens in "
                        + "one secure portal - upload what we need, then read and sign your offer "
                        + "letter when it arrives.")
                        + layout.button(portalUrl, "Open my onboarding portal")
                        + layout.fallbackLink(portalUrl)
                        + layout.p("<strong style=\"font-weight:600;color:#0f1b33\">Documents we need "
                                + "from you</strong>")
                        + layout.panel(documentRows)
                        + layout.note("This link is personal to you - please do not forward it. It stays "
                                + "valid until " + layout.escape(expiry) + ". Need help? Contact "
                                + layout.escape(properties.getSupportContact()) + "."));

        return new EmailMessage(candidate.getEmail(), candidate.getName(), kind.subject(), text, html);
    }

    /** Optional is worth saying; required is the default and needs no label. */
    private String htmlDocumentRow(RequiredDocument rd) {
        return layout.escape(rd.displayName())
                + (rd.isMandatory() ? "" : " <span style=\"color:#8b95a8\">(optional)</span>");
    }
}
