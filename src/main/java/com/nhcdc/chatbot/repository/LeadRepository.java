package com.nhcdc.chatbot.repository;

import com.nhcdc.chatbot.entity.Lead;
import com.nhcdc.chatbot.enums.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findAllByOrderByCreatedAtDesc();

    List<Lead> findByCentreIdOrderByCreatedAtDesc(Long centreId);

    List<Lead> findTop50BySheetSyncedFalseOrderByCreatedAtAsc();

    long countBySheetSyncedFalse();

    Optional<Lead> findFirstByWhatsappNumberAndCentreIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            String whatsappNumber, Long centreId, LeadStatus status, Instant createdAfter);
}
