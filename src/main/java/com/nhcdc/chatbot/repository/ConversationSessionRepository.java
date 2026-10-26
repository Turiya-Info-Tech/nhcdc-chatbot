package com.nhcdc.chatbot.repository;

import com.nhcdc.chatbot.entity.ConversationSession;
import com.nhcdc.chatbot.enums.ConversationStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {

    Optional<ConversationSession> findByWhatsappNumber(String whatsappNumber);

    /** Conversations that are mid-way through something and have been quiet since before the cutoff. */
    List<ConversationSession> findByCurrentStepNotAndLastActivityAtBefore(ConversationStep step, Instant cutoff);

    /** Same, but only those that have not been sent the idle reminder yet. */
    List<ConversationSession> findByCurrentStepNotAndIdleReminderSentFalseAndLastActivityAtBefore(
            ConversationStep step, Instant cutoff);
}
