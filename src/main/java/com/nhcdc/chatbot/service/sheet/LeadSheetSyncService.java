package com.nhcdc.chatbot.service.sheet;

import com.nhcdc.chatbot.entity.Lead;

/**
 * Pushes a copy of a Lead into the Google Sheet that NHCDC staff use to
 * view/track leads day to day. The DB (see LeadRepository) remains the
 * source of truth; LeadSheetSyncJob keeps retrying until a lead is in.
 */
public interface LeadSheetSyncService {

    /** Appends the lead as a new Sheet row. @throws SheetSyncException if it could not be appended. */
    void syncLead(Lead lead);
}
