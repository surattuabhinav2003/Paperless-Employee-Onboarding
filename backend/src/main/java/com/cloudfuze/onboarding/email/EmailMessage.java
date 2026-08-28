package com.cloudfuze.onboarding.email;

/**
 * A rendered outbound email, independent of the transport used to deliver it.
 *
 * @param archivable whether a copy may be filed with the archive recipient.
 *                   Almost everything may; a one-time verification code may
 *                   not. That code is what proves a candidate controls their
 *                   own inbox, and the portal link is in another email the same
 *                   person is copied on - together they are enough to open
 *                   anyone's portal and read their identity documents. Copying
 *                   the second factor to a third party is not an archive, it is
 *                   a spare key.
 */
public record EmailMessage(
        String toAddress,
        String toName,
        String subject,
        String textBody,
        String htmlBody,
        boolean archivable
) {

    /** Archivable, which is the right answer for ordinary correspondence. */
    public EmailMessage(String toAddress, String toName, String subject, String textBody, String htmlBody) {
        this(toAddress, toName, subject, textBody, htmlBody, true);
    }

    /** A copy of this message, marked as never to be blind-copied to anyone. */
    public EmailMessage notArchivable() {
        return new EmailMessage(toAddress, toName, subject, textBody, htmlBody, false);
    }
}
