package com.nhcdc.chatbot.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import java.io.InputStream;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class GoogleSheetsClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final GoogleSheetsProperties properties;

    @Bean
    public Sheets sheetsClient() throws Exception {
        try (InputStream credentialsStream = new FileSystemResource(properties.getCredentialsPath()).getInputStream()) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS));
            HttpCredentialsAdapter credentialsAdapter = new HttpCredentialsAdapter(credentials);

            // Without timeouts a stuck Sheets call would hang the background sync job.
            HttpRequestInitializer withTimeouts = request -> {
                credentialsAdapter.initialize(request);
                request.setConnectTimeout(CONNECT_TIMEOUT_MS);
                request.setReadTimeout(READ_TIMEOUT_MS);
            };

            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    withTimeouts)
                    .setApplicationName("nhcdc-chatbot")
                    .build();
        }
    }
}
