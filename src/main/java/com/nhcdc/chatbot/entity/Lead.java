package com.nhcdc.chatbot.entity;

import com.nhcdc.chatbot.enums.LeadStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "leads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lead_code", nullable = false, unique = true)
    private String leadCode;

    @Column(name = "whatsapp_number", nullable = false)
    private String whatsappNumber;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_number")
    private String patientNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 2000)
    private String concern;

    @ManyToOne
    @JoinColumn(name = "centre_id")
    private Centre centre;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private ServiceOffering serviceOffering;

    @Column(name = "referral_doctor")
    private String referralDoctor;

    @Builder.Default
    @Column(name = "consent_given", nullable = false)
    private boolean consentGiven = false;

    @Column(name = "consent_at")
    private Instant consentAt;

    @Column(name = "consent_text_snapshot", length = 2000)
    private String consentTextSnapshot;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private LeadStatus status = LeadStatus.NEW;

    @Builder.Default
    @Column(nullable = false)
    private String source = "WhatsApp Chatbot";

    /** False until the lead has been appended to the Google Sheet; LeadSheetSyncJob retries until it is. */
    @Builder.Default
    @Column(name = "sheet_synced", nullable = false)
    private boolean sheetSynced = false;

    @Builder.Default
    @Column(name = "sheet_sync_attempts", nullable = false)
    private int sheetSyncAttempts = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
