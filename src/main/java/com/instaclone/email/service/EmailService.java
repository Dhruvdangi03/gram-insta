package com.instaclone.email.service;

/** One interface, two implementations (ResendEmailService / LoggingEmailService) selected by
 * EmailConfig based on whether a real API key is configured — the rest of the app never knows
 * which one it's talking to. */
public interface EmailService {

    void send(String toEmail, String subject, String textBody);
}
