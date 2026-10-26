package com.nhcdc.chatbot.service.sheet;

import com.nhcdc.chatbot.entity.Lead;
import com.nhcdc.chatbot.repository.LeadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LeadSheetSyncJobTest {

    @Mock LeadRepository leadRepository;
    @Mock LeadSheetSyncService sheetSyncService;

    LeadSheetSyncJob job;

    @BeforeEach
    void setUp() {
        job = new LeadSheetSyncJob(leadRepository, sheetSyncService);
    }

    private Lead pendingLead(String code) {
        return Lead.builder().leadCode(code).whatsappNumber("919000000001").build();
    }

    @Test
    void successfulSyncMarksLeadSyncedAndCountsTheAttempt() {
        Lead lead = pendingLead("NHCDC-00001");
        when(leadRepository.findTop50BySheetSyncedFalseOrderByCreatedAtAsc()).thenReturn(List.of(lead));

        job.syncPendingLeads();

        assertTrue(lead.isSheetSynced());
        assertEquals(1, lead.getSheetSyncAttempts());
        verify(leadRepository).save(lead);
    }

    @Test
    void failedSyncKeepsLeadPendingForRetryAndNeverThrows() {
        Lead lead = pendingLead("NHCDC-00002");
        when(leadRepository.findTop50BySheetSyncedFalseOrderByCreatedAtAsc()).thenReturn(List.of(lead));
        doThrow(new SheetSyncException("quota exceeded", null)).when(sheetSyncService).syncLead(any());

        job.syncPendingLeads();

        assertFalse(lead.isSheetSynced());
        assertEquals(1, lead.getSheetSyncAttempts());
        verify(leadRepository).save(lead);
    }

    @Test
    void oneFailingLeadDoesNotBlockTheOthers() {
        Lead bad = pendingLead("NHCDC-00003");
        Lead good = pendingLead("NHCDC-00004");
        when(leadRepository.findTop50BySheetSyncedFalseOrderByCreatedAtAsc()).thenReturn(List.of(bad, good));
        doThrow(new SheetSyncException("bad row", null)).when(sheetSyncService).syncLead(bad);

        job.syncPendingLeads();

        assertFalse(bad.isSheetSynced());
        assertTrue(good.isSheetSynced());
    }

    @Test
    void databaseErrorWhileLoadingIsContained() {
        when(leadRepository.findTop50BySheetSyncedFalseOrderByCreatedAtAsc()).thenThrow(new IllegalStateException("db down"));

        job.syncPendingLeads();

        verify(sheetSyncService, never()).syncLead(any());
    }
}
