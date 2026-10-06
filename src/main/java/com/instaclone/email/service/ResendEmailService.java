package com.instaclone.email.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instaclone.config.properties.EmailProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Talks to Resend's plain HTTP REST API (no SDK dependency needed — one POST, JSON in, JSON out)
 * rather than SMTP, which would need spring-boot-starter-mail plus real SMTP credentials instead
 * of a single API key. */
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);
    private static final URI RESEND_ENDPOINT = URI.create("https://api.resend.com/emails");

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmailProperties emailProperties;

    public ResendEmailService(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    @Override
    public void send(String toEmail, String subject, String textBody) {
        try {
            Map<String, Object> payload = Map.of(
                    "from", emailProperties.fromAddress(), "to", List.of(toEmail), "subject", subject, "text", textBody);
            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder(RESEND_ENDPOINT)
                    .header("Authorization", "Bearer " + emailProperties.resendApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                // A failed reset email shouldn't 500 the request that triggered it (the caller
                // already behaves identically whether or not the address exists, to avoid leaking
                // which emails are registered) — log and move on rather than throwing.
                log.warn("Resend API returned {} sending to {}: {}", response.statusCode(), toEmail, response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to send email via Resend to {}", toEmail, e);
        }
    }
}
