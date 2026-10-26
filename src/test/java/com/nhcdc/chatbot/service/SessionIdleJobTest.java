package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.entity.ConversationSession;
import com.nhcdc.chatbot.enums.ConversationStep;
import com.nhcdc.chatbot.repository.ConversationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionIdleJobTest {

    @Mock ConversationSessionRepository repository;
    @Mock ConversationFlowService flow;

    SessionIdleJob job;

    @BeforeEach
    void setUp() {
        job = new SessionIdleJob(repository, flow);
        ReflectionTestUtils.setField(job, "reminderMinutes", 5L);
        ReflectionTestUtils.setField(job, "timeoutMinutes", 30L);
    }

    private ConversationSession session(String number) {
        return ConversationSession.builder().whatsappNumber(number).build();
    }

    @Test
    void expiresTimedOutSessionsAndRemindsQuietOnes() {
        when(repository.findByCurrentStepNotAndLastActivityAtBefore(eq(ConversationStep.MAIN_MENU), any(Instant.class)))
                .thenReturn(List.of(session("911")));
        when(repository.findByCurrentStepNotAndIdleReminderSentFalseAndLastActivityAtBefore(eq(ConversationStep.MAIN_MENU), any(Instant.class)))
                .thenReturn(List.of(session("922")));

        job.checkIdleSessions();

        verify(flow).expireIfIdle("911");
        verify(flow).remindIfIdle("922");
        verify(flow, never()).remindIfIdle("911");
    }

    @Test
    void oneFailingNumberDoesNotStopTheRest() {
        when(repository.findByCurrentStepNotAndLastActivityAtBefore(eq(ConversationStep.MAIN_MENU), any(Instant.class)))
                .thenReturn(List.of(session("911"), session("933")));
        doThrow(new IllegalStateException("boom")).when(flow).expireIfIdle("911");

        job.checkIdleSessions();

        verify(flow).expireIfIdle("933");
    }
}
