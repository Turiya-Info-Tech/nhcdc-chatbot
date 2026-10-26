package com.nhcdc.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Turiya Info Tech WhatsApp gateway, used when whatsapp.provider=turiya. */
@Component
@ConfigurationProperties(prefix = "turiya")
@Getter
@Setter
public class TuriyaProperties {
    private String baseUrl;
    private String apiKey;
    private String wabaNumber;

    /** DEV-ONLY, same meaning as whatsapp.meta.trust-all-certs. */
    private boolean trustAllCerts;
}
