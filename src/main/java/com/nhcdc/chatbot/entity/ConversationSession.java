package com.nhcdc.chatbot.entity;

import com.nhcdc.chatbot.enums.ConversationStep;
import com.nhcdc.chatbot.enums.LeadFieldStep;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Per-WhatsApp-number conversation state. Tracks where the user is in
 * the menu tree and any lead fields collected so far but not yet saved
 * as a Lead (draftDataJson, keyed by LeadFieldStep name).
 */
@Entity
@Table(name = "conversation_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "whatsapp_number", nullable = false, unique = true)
    private String whatsappNumber;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "current_step", nullable = false)
    private ConversationStep currentStep = ConversationStep.MAIN_MENU;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "pending_lead_field")
    private LeadFieldStep pendingLeadField = LeadFieldStep.NONE;

    @Column(name = "tagged_centre_id")
    private Long taggedCentreId;

    @Column(name = "tagged_service_id")
    private Long taggedServiceId;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    /** True once the "we haven't heard from you" reminder was sent for the current quiet period; cleared by the next user message. */
    @Builder.Default
    @Column(name = "idle_reminder_sent", nullable = false)
    private boolean idleReminderSent = false;

    @Column(name = "draft_data_json", columnDefinition = "TEXT")
    private String draftDataJson;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
