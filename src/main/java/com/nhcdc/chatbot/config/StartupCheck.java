package com.nhcdc.chatbot.config;

import com.nhcdc.chatbot.repository.LeadRepository;
import com.nhcdc.chatbot.service.whatsapp.MetaErrorParser;
import com.nhcdc.chatbot.util.ErrorCodes;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;

/**
 * Surfaces configuration problems at startup instead of on the first
 * parent's message: missing WhatsApp settings stop the app with a clear
 * message, and a live call to Meta reports which number the token is
 * actually connected to (or why Meta refuses it).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupCheck {

    private static final String DEFAULT_ADMIN_PASSWORD = "changeme";

    private final WhatsAppMetaProperties meta;
    private final TuriyaProperties turiya;
    private final RestClient whatsAppRestClient;
    private final LeadRepository leadRepository;

    @Value("${admin.api.password}")
    private String adminPassword;

    @Value("${whatsapp.provider:meta}")
    private String provider;

    @Value("${whatsapp.meta.verify-on-startup:true}")
    private boolean verifyOnStartup;

    @PostConstruct
    void failFastOnMissingConfig() {
        List<String> missing = new ArrayList<>();
        if (isTuriya()) {
            if (isBlank(turiya.getBaseUrl())) {
                missing.add("turiya.base-url (TURIYA_BASE_URL)");
            }
            if (isBlank(turiya.getApiKey())) {
                missing.add("turiya.api-key (TURIYA_API_KEY)");
            }
            if (isBlank(turiya.getWabaNumber())) {
                missing.add("turiya.waba-number (TURIYA_WABA_NUMBER)");
            }
        } else {
            if (isBlank(meta.getPhoneNumberId())) {
                missing.add("whatsapp.meta.phone-number-id (WHATSAPP_PHONE_NUMBER_ID)");
            }
            if (isBlank(meta.getAccessToken())) {
                missing.add("whatsapp.meta.access-token (WHATSAPP_ACCESS_TOKEN)");
            }
        }
        // the Meta-style GET verification handshake is answered the same way on both providers
        if (isBlank(meta.getWebhookVerifyToken())) {
            missing.add("whatsapp.meta.webhook-verify-token (WHATSAPP_WEBHOOK_VERIFY_TOKEN)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(ErrorCodes.CONFIG + " Missing required configuration: " + String.join(", ", missing));
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void checkAfterStartup() {
        if (DEFAULT_ADMIN_PASSWORD.equals(adminPassword)) {
            log.warn("{} admin.api.password is still the default - change ADMIN_API_PASSWORD before going live", ErrorCodes.CONFIG);
        }
        reportUnsyncedLeads();
        if (verifyOnStartup && !isTuriya()) {
            verifyMetaConnection();
        }
    }

    private void reportUnsyncedLeads() {
        try {
            long unsynced = leadRepository.countBySheetSyncedFalse();
            if (unsynced > 0) {
                log.warn("{} leads are not in the Google Sheet yet; the background job will keep retrying", unsynced);
            }
        } catch (Exception ex) {
            log.warn("Could not count leads waiting for the Google Sheet: {}", ex.getMessage());
        }
    }

    private void verifyMetaConnection() {
        try {
            String details = whatsAppRestClient.get()
                    .uri("/{id}?fields=display_phone_number,verified_name,quality_rating", meta.getPhoneNumberId())
                    .retrieve()
                    .body(String.class);
            log.info("WhatsApp connection OK - Meta reports: {}", details);
        } catch (RestClientResponseException ex) {
            MetaErrorParser.MetaError error = MetaErrorParser.parse(ex.getResponseBodyAsString());
            log.error("{} Meta refused phone number id {}: HTTP {}, error code {}, \"{}\". {}", ErrorCodes.CONFIG,
                    meta.getPhoneNumberId(), ex.getStatusCode().value(), error.code(), error.message(),
                    MetaErrorParser.hint(error.code()));
        } catch (RestClientException ex) {
            log.error("{} Could not reach the Meta Graph API at startup: {}", ErrorCodes.CONFIG, ex.getMessage());
        }
    }

    private boolean isTuriya() {
        return provider != null && "turiya".equalsIgnoreCase(provider.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
