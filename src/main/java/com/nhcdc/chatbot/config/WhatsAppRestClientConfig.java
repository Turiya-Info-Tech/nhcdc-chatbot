package com.nhcdc.chatbot.config;

import com.nhcdc.chatbot.util.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class WhatsAppRestClientConfig {

    /** A hung Meta call would otherwise block that user's thread (and their per-number lock) indefinitely. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final WhatsAppMetaProperties properties;
    private final TuriyaProperties turiyaProperties;

    @Bean
    public RestClient whatsAppRestClient() {
        return RestClient.builder()
                .baseUrl(properties.getGraphApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken())
                .requestFactory(requestFactory("whatsapp.meta.trust-all-certs", properties.isTrustAllCerts()))
                .build();
    }

    /** Turiya authenticates with Key + wabaNumber headers instead of a Bearer token. */
    @Bean
    public RestClient turiyaRestClient() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(turiyaProperties.getBaseUrl())
                .requestFactory(requestFactory("turiya.trust-all-certs", turiyaProperties.isTrustAllCerts()));
        // unset when provider=meta; header values must not be null
        if (turiyaProperties.getApiKey() != null) {
            builder.defaultHeader("Key", turiyaProperties.getApiKey());
        }
        if (turiyaProperties.getWabaNumber() != null) {
            builder.defaultHeader("wabaNumber", turiyaProperties.getWabaNumber());
        }
        return builder.build();
    }

    private JdkClientHttpRequestFactory requestFactory(String settingName, boolean trustAll) {
        HttpClient.Builder httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT);

        if (trustAll) {
            log.warn("{} {} is enabled - TLS certificate and hostname validation is DISABLED for these " +
                    "outbound calls. This must never be enabled outside a local dev machine.",
                    ErrorCodes.CONFIG, settingName);
            trustAllCertificates(httpClient);
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient.build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    private void trustAllCertificates(HttpClient.Builder httpClient) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");

            httpClient.sslContext(sslContext).sslParameters(sslParameters);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build trust-all HttpClient for local dev use", ex);
        }
    }
}
