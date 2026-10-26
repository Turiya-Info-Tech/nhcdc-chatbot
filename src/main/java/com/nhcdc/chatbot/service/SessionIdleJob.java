package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.entity.ConversationSession;
import com.nhcdc.chatbot.enums.ConversationStep;
import com.nhcdc.chatbot.repository.ConversationSessionRepository;
import com.nhcdc.chatbot.util.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Looks for conversations that went quiet part-way through a menu or the
 * lead form. After the reminder time the parent gets a one-time "we haven't
 * heard from you" with the step they were on; after the session timeout the
 * conversation is ended and they are told to start fresh. The decisions
 * themselves are made in ConversationFlowService, under the same lock as
 * normal messages.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionIdleJob {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationFlowService conversationFlowService;

    @Value("${conversation.idle-reminder-minutes:5}")
    private long reminderMinutes;

    @Value("${conversation.idle-timeout-minutes:30}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${conversation.idle-check-interval-ms:30000}",
            initialDelayString = "${conversation.idle-check-initial-delay-ms:20000}")
    public void checkIdleSessions() {
        Instant now = Instant.now();
        // Expire first so a conversation past the timeout is not also sent a reminder.
        forEachNumber(sessionRepository.findByCurrentStepNotAndLastActivityAtBefore(
                ConversationStep.MAIN_MENU, now.minus(Duration.ofMinutes(timeoutMinutes))),
                conversationFlowService::expireIfIdle);
        forEachNumber(sessionRepository.findByCurrentStepNotAndIdleReminderSentFalseAndLastActivityAtBefore(
                ConversationStep.MAIN_MENU, now.minus(Duration.ofMinutes(reminderMinutes))),
                conversationFlowService::remindIfIdle);
    }

    private void forEachNumber(List<ConversationSession> sessions, java.util.function.Consumer<String> action) {
        for (ConversationSession session : sessions) {
            try {
                action.accept(session.getWhatsappNumber());
            } catch (Exception ex) {
                log.error("{} Idle-session check failed", ErrorCodes.FLOW_FAILED, ex);
            }
        }
    }
}
