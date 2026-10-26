package com.nhcdc.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.sheets")
@Getter
@Setter
public class GoogleSheetsProperties {
    private String spreadsheetId;
    private String sheetName;
    private String credentialsPath;
}
