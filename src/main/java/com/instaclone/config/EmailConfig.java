package com.instaclone.config;

import com.instaclone.config.properties.EmailProperties;
import com.instaclone.email.service.EmailService;
import com.instaclone.email.service.LoggingEmailService;
import com.instaclone.email.service.ResendEmailService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailConfig {

    @Bean
    public EmailService emailService(EmailProperties emailProperties) {
        if (emailProperties.resendApiKey() == null || emailProperties.resendApiKey().isBlank()) {
            return new LoggingEmailService();
        }
        return new ResendEmailService(emailProperties);
    }
}
