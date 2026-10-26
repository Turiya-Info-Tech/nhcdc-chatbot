package com.nhcdc.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whatsapp.meta")
@Getter
@Setter
public class WhatsAppMetaProperties {
    private String graphApiBaseUrl;
    private String phoneNumberId;
    private String accessToken;
    private String webhookVerifyToken;

    /**
     * DEV-ONLY escape hatch for corporate SSL-inspection proxies that
     * break the JVM's default trust store. Defaults to false; never
     * enable outside a local machine - it disables certificate and
     * hostname validation for all outbound Meta API calls.
     */
    private boolean trustAllCerts;
}
