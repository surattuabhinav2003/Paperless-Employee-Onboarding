package com.cloudfuze.onboarding;

import com.cloudfuze.onboarding.email.EmailMessage;
import com.cloudfuze.onboarding.email.HrNotificationComposer;
import com.cloudfuze.onboarding.email.InvitationMailComposer;
import com.cloudfuze.onboarding.email.ReviewNotificationComposer;
import com.cloudfuze.onboarding.model.Candidate;
import com.cloudfuze.onboarding.model.DocumentType;
import com.cloudfuze.onboarding.model.RequiredDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every email, rendered.
 *
 * <p>Two jobs. It asserts the properties that decide whether a mail survives
 * real inboxes - a preheader, table-based structure, no webfont, a plain-text
 * fallback for the button - which are exactly the things that are invisible
 * until they break in someone's Outlook.
 *
 * <p>And it writes each one to target/email-preview so the design can be looked
 * at in a browser rather than imagined from Java string literals. Open
 * target/email-preview/index.html after running this.
 */
@SpringBootTest
@ActiveProfiles("test")
class MailLayoutRenderTest {

    private static final Path PREVIEW_DIR = Path.of("target", "email-preview");

    @Autowired private InvitationMailComposer invitationComposer;
    @Autowired private ReviewNotificationComposer reviewComposer;
    @Autowired private HrNotificationComposer hrComposer;

    private static Candidate candidate() {
        Candidate candidate = new Candidate();
        candidate.setName("Priya Sharma");
        candidate.setEmail("priya.sharma@example.com");
        candidate.setRole("Software Engineer");
        candidate.setDepartment("Engineering");
        candidate.setTokenExpiresAt(Instant.parse("2026-09-11T17:00:00Z"));
        candidate.getRequiredDocuments().add(
                new RequiredDocument(DocumentType.AADHAAR_ID, true, "Aadhaar Card"));
        candidate.getRequiredDocuments().add(
                new RequiredDocument(DocumentType.PAN_CARD, true, "PAN Card"));
        candidate.getRequiredDocuments().add(
                new RequiredDocument(DocumentType.PASSPORT_PHOTO, false, "Passport-size Photo"));
        return candidate;
    }

    @Test
    @DisplayName("Every email carries what an inbox needs to render it properly")
    void everyEmailIsBuiltForRealInboxes() throws IOException {
        String portal = "https://onboarding.neutara.com/upload/tok3n";

        record Sample(String name, EmailMessage message) { }
        var samples = new Sample[] {
                new Sample("invitation", invitationComposer.compose(candidate(), portal,
                        Instant.parse("2026-09-11T17:00:00Z"), InvitationMailComposer.Kind.INITIAL)),
                new Sample("documents-approved", reviewComposer.approved(candidate(), portal)),
                new Sample("offer-ready", reviewComposer.offerReady(candidate(), portal + "/offer")),
                new Sample("hr-submitted", hrComposer.candidateSubmitted(candidate(), 6)),
                new Sample("hr-offer-signed", hrComposer.offerSigned(candidate(), "Priya Sharma")),
        };

        Files.createDirectories(PREVIEW_DIR);
        try (var logo = getClass().getResourceAsStream("/email/neutara-logo.png")) {
            assertThat(logo).as("the logo is on the classpath for the mailer to attach").isNotNull();
            Files.write(PREVIEW_DIR.resolve("neutara-logo.png"), logo.readAllBytes());
        }
        StringBuilder index = new StringBuilder("<h1>Email preview</h1><ul>");

        for (Sample sample : samples) {
            String html = sample.message().htmlBody();

            // The grey line the inbox shows beside the subject. Left unset it
            // fills with "Hi Priya", wasting the only other line anyone reads.
            assertThat(html).as("%s has a preheader", sample.name()).contains("max-height:0");

            // Outlook renders through Word, which ignores max-width on a div.
            assertThat(html).as("%s is table-based", sample.name()).contains("role=\"presentation\"");

            // Gmail strips @font-face, so a brand webfont would show for some
            // readers and not others - worse than one good stack for everyone.
            assertThat(html).as("%s uses no webfont", sample.name()).doesNotContain("@font-face");
            assertThat(html).as("%s uses no remote CSS", sample.name()).doesNotContain("<link");

            // A gateway that rewrites the button leaves the reader with nothing
            // unless the URL is also there in full.
            if (html.contains("display:inline-block")) {
                assertThat(html).as("%s repeats its link as text", sample.name())
                        .contains("paste this into your browser");
            }

            // Plain text is not an afterthought: some readers only ever see it.
            assertThat(sample.message().textBody()).as("%s has a text part", sample.name()).isNotBlank();

            // The logo travels with the message rather than being fetched, and
            // reads as "Neutara" for the many clients that block images.
            assertThat(html).as("%s carries the logo", sample.name()).contains("cid:neutara-logo");
            assertThat(html).as("%s names the brand when images are off", sample.name())
                    .contains("alt=\"Neutara\"");

            /* A browser cannot resolve cid:, so the preview points at the file
               sitting beside it. The sent mail is unchanged. */
            Files.writeString(PREVIEW_DIR.resolve(sample.name() + ".html"),
                    html.replace("cid:neutara-logo", "neutara-logo.png"));
            index.append("<li><a href=\"").append(sample.name()).append(".html\">")
                    .append(sample.name()).append("</a> &mdash; ")
                    .append(sample.message().subject()).append("</li>");
        }

        Files.writeString(PREVIEW_DIR.resolve("index.html"), index.append("</ul>").toString());
    }

    @Test
    @DisplayName("A name with markup in it cannot break out into the message")
    void namesAreEscaped() {
        Candidate hostile = candidate();
        hostile.setName("<script>alert(1)</script>");

        String html = reviewComposer.approved(hostile, "https://example.com/p/t").htmlBody();

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }
}
