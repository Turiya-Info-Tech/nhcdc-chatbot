package com.nhcdc.chatbot.service.sheet;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.nhcdc.chatbot.config.GoogleSheetsProperties;
import com.nhcdc.chatbot.entity.Lead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Appends a row per Lead to the NHCDC ops Google Sheet. Failures are
 * reported as SheetSyncException; LeadSheetSyncJob owns the retrying and
 * the logging, so an outage does not produce a stack trace per attempt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleSheetsLeadSyncService implements LeadSheetSyncService {

    private static final DateTimeFormatter IST_DATE_TIME = DateTimeFormatter
            .ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH)
            .withZone(ZoneId.of("Asia/Kolkata"));

    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private final Sheets sheetsClient;
    private final GoogleSheetsProperties properties;

    @Override
    public void syncLead(Lead lead) {
        try {
            appendRow(lead);
        } catch (Exception ex) {
            throw new SheetSyncException("Google Sheets append failed for lead " + lead.getLeadCode()
                    + " (" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + ")", ex);
        }
    }

    private void appendRow(Lead lead) throws IOException {
        List<Object> row = Arrays.asList(
                lead.getLeadCode(),
                formatIst(lead.getCreatedAt()),
                lead.getWhatsappNumber(),
                nullToEmpty(lead.getPatientName()),
                nullToEmpty(lead.getPatientNumber()),
                lead.getDateOfBirth() == null ? "" : DOB_FORMAT.format(lead.getDateOfBirth()),
                nullToEmpty(lead.getConcern()),
                lead.getCentre() != null ? lead.getCentre().getName() : "",
                lead.getServiceOffering() != null ? lead.getServiceOffering().getName() : "",
                nullToEmpty(lead.getReferralDoctor()),
                lead.isConsentGiven() ? "Yes" : "No",
                lead.getStatus().name(),
                lead.getSource()
        );

        ValueRange body = new ValueRange().setValues(List.of(row));
        String range = properties.getSheetName() + "!A1";

        // RAW stores every value exactly as sent. USER_ENTERED would let Google re-parse "02-10-2026 01:47 PM"
        // into its own date style, and would run anything starting with "=" (e.g. a parent's concern) as a formula.
        sheetsClient.spreadsheets().values()
                .append(properties.getSpreadsheetId(), range, body)
                .setValueInputOption("RAW")
                .execute();
        log.debug("Synced lead {} to Google Sheet", lead.getLeadCode());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatIst(Instant instant) {
        return instant == null ? "" : IST_DATE_TIME.format(instant);
    }
}
