package com.cloudfuze.onboarding.email;

/** A rendered outbound email, independent of the transport used to deliver it. */
public record EmailMessage(
        String toAddress,
        String toName,
        String subject,
        String textBody,
        String htmlBody
) {
}
