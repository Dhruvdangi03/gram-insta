package com.instaclone.email.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Active whenever app.email.resend-api-key is unset (the default for local dev, and for anyone
 * who hasn't signed up for Resend yet) — logs the would-be email instead of sending it, so the
 * password-reset flow is fully exercisable without any external dependency. Swapping in real
 * delivery later is a config change only (set RESEND_API_KEY), not a code change. */
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void send(String toEmail, String subject, String textBody) {
        log.info(
                "EmailService (no RESEND_API_KEY configured, logging instead of sending) — to={}, subject={}\n{}",
                toEmail,
                subject,
                textBody);
    }
}
