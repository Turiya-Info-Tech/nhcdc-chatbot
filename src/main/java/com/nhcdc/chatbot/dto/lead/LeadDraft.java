package com.nhcdc.chatbot.dto.lead;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fields collected one WhatsApp message at a time before a Lead can be
 * saved. Serialized as JSON into ConversationSession.draftDataJson.
 * dateOfBirth is stored as an ISO-8601 string (yyyy-MM-dd).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadDraft {
    private String patientName;
    private String patientNumber;
    private String dateOfBirth;
    private String concern;
    private String referralDoctor;
}
