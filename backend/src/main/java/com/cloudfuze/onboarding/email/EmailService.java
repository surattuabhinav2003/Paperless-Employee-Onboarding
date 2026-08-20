package com.cloudfuze.onboarding.email;

/** Transport abstraction. Swap the implementation, never the calling code. */
public interface EmailService {

    void send(EmailMessage message);

    String providerName();
}
