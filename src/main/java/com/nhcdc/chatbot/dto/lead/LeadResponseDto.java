package com.nhcdc.chatbot.dto.lead;

import com.nhcdc.chatbot.enums.LeadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeadResponseDto {
    private Long id;
    private String leadCode;
    private String whatsappNumber;
    private String patientName;
    private String patientNumber;
    private LocalDate dateOfBirth;
    private String concern;
    private String centreName;
    private String serviceName;
    private String referralDoctor;
    private boolean consentGiven;
    private Instant consentAt;
    private LeadStatus status;
    private String source;
    private boolean sheetSynced;
    private Instant createdAt;
    private Instant updatedAt;
}
