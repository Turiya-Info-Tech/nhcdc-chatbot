package com.nhcdc.chatbot.dto.lead;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Input to LeadService once the form is complete and consent is given.
 * Built by ConversationFlowService from the session's draft data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewLeadCommand {
    private String whatsappNumber;
    private String patientName;
    private String patientNumber;
    private LocalDate dateOfBirth;
    private String concern;
    private String referralDoctor;
    private Long centreId;
    private Long serviceId;
    private String consentText;
}
