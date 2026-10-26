package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.dto.lead.NewLeadCommand;
import com.nhcdc.chatbot.entity.Centre;
import com.nhcdc.chatbot.entity.Lead;
import com.nhcdc.chatbot.entity.ServiceOffering;
import com.nhcdc.chatbot.enums.LeadStatus;
import com.nhcdc.chatbot.repository.CentreRepository;
import com.nhcdc.chatbot.repository.LeadRepository;
import com.nhcdc.chatbot.repository.ServiceOfferingRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    /** @param duplicate true when an open lead for the same number and centre already existed, so nothing new was saved. */
    public record LeadResult(Lead lead, boolean duplicate) {
    }

    private final LeadRepository leadRepository;
    private final CentreRepository centreRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final EntityManager entityManager;

    @Value("${leads.duplicate-window-hours:24}")
    private long duplicateWindowHours;

    /**
     * Runs in one transaction that first takes a Postgres advisory lock for this number + centre. A second
     * request for the same pair (e.g. a double tap on "I Agree") waits here until the first has committed,
     * so it then finds that lead and is reported as a duplicate instead of creating another one.
     */
    @Transactional
    public LeadResult createLead(NewLeadCommand command) {
        lockForDuplicateCheck(command);
        Optional<Lead> existing = leadRepository
                .findFirstByWhatsappNumberAndCentreIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        command.getWhatsappNumber(), command.getCentreId(), LeadStatus.NEW,
                        Instant.now().minus(Duration.ofHours(duplicateWindowHours)));
        if (existing.isPresent()) {
            log.info("Duplicate enquiry for centre {} - existing lead {} kept",
                    command.getCentreId(), existing.get().getLeadCode());
            return new LeadResult(existing.get(), true);
        }

        Centre centre = command.getCentreId() == null ? null : centreRepository.findById(command.getCentreId()).orElse(null);
        ServiceOffering serviceOffering = command.getServiceId() == null ? null
                : serviceOfferingRepository.findById(command.getServiceId()).orElse(null);

        Lead lead = Lead.builder()
                .leadCode(generateLeadCode())
                .whatsappNumber(command.getWhatsappNumber())
                .patientName(command.getPatientName())
                .patientNumber(command.getPatientNumber())
                .dateOfBirth(command.getDateOfBirth())
                .concern(command.getConcern())
                .centre(centre)
                .serviceOffering(serviceOffering)
                .referralDoctor(command.getReferralDoctor())
                .consentGiven(true)
                .consentAt(Instant.now())
                .consentTextSnapshot(command.getConsentText())
                .build();

        // Saved with sheetSynced=false; LeadSheetSyncJob pushes it to the Sheet in the
        // background so a slow or failing Sheets call never delays the parent's reply.
        Lead saved = leadRepository.save(lead);

        log.info("Lead {} created - centre={}", saved.getLeadCode(),
                saved.getCentre() != null ? saved.getCentre().getName() : "none");
        return new LeadResult(saved, false);
    }

    private void lockForDuplicateCheck(NewLeadCommand command) {
        String key = "lead:" + command.getWhatsappNumber() + ":" + command.getCentreId();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:key))")
                .setParameter("key", key)
                .getSingleResult();
    }

    private String generateLeadCode() {
        Number nextValue = (Number) entityManager.createNativeQuery("SELECT nextval('lead_code_seq')").getSingleResult();
        return String.format("NHCDC-%05d", nextValue.longValue());
    }
}
