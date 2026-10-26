package com.nhcdc.chatbot.service.sheet;

import com.nhcdc.chatbot.entity.Lead;
import com.nhcdc.chatbot.repository.LeadRepository;
import com.nhcdc.chatbot.util.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pushes leads to the Google Sheet in the background and keeps retrying the
 * ones that fail, so a Sheets outage delays a row but never loses it. The
 * parent's reply never waits on Sheets.
 *
 * To stay readable during a long outage, a failing lead is logged as an
 * error on its first failure and then once every {@value #LOG_EVERY_N_ATTEMPTS}
 * attempts; the attempts in between are only visible at DEBUG.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadSheetSyncJob {

    private static final int LOG_EVERY_N_ATTEMPTS = 20;

    private final LeadRepository leadRepository;
    private final LeadSheetSyncService sheetSyncService;

    @Scheduled(fixedDelayString = "${leads.sheet-sync.interval-ms:15000}",
            initialDelayString = "${leads.sheet-sync.initial-delay-ms:10000}")
    public void syncPendingLeads() {
        List<Lead> pending;
        try {
            pending = leadRepository.findTop50BySheetSyncedFalseOrderByCreatedAtAsc();
        } catch (Exception ex) {
            log.error("{} Could not load leads waiting for the Google Sheet: {}", ErrorCodes.SHEET_SYNC, ex.getMessage());
            return;
        }
        pending.forEach(this::syncOne);
    }

    void syncOne(Lead lead) {
        int attempt = lead.getSheetSyncAttempts() + 1;
        try {
            sheetSyncService.syncLead(lead);
            lead.setSheetSynced(true);
            if (attempt > 1) {
                log.info("Lead {} reached the Google Sheet after {} attempts", lead.getLeadCode(), attempt);
            }
        } catch (Exception ex) {
            if (attempt == 1 || attempt % LOG_EVERY_N_ATTEMPTS == 0) {
                log.error("{} Lead {} is not in the Google Sheet yet (attempt {}): {}. It stays saved in the database and will be retried.",
                        ErrorCodes.SHEET_SYNC, lead.getLeadCode(), attempt, ex.getMessage());
            } else {
                log.debug("Sheet sync attempt {} failed for lead {}: {}", attempt, lead.getLeadCode(), ex.getMessage());
            }
        }
        lead.setSheetSyncAttempts(attempt);
        try {
            leadRepository.save(lead);
        } catch (Exception ex) {
            log.error("{} Could not record the sync result for lead {}: {}", ErrorCodes.SHEET_SYNC, lead.getLeadCode(), ex.getMessage());
        }
    }
}
