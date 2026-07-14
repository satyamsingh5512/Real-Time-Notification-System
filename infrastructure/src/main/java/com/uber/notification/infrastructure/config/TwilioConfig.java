package com.uber.notification.infrastructure.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/** Initializes the static Twilio SDK client with account credentials from config/secrets. */
@Configuration
public class TwilioConfig {

    @Value("${notification.sms.twilio-account-sid:}")
    private String accountSid;

    @Value("${notification.sms.twilio-auth-token:}")
    private String authToken;

    @PostConstruct
    public void init() {
        if (!accountSid.isBlank() && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
        }
    }
}
