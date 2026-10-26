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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationSessionCacheServiceTest {

    private static final String WA = "919000000001";

    @Mock ConversationSessionRepository repository;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    ConversationSessionCacheService service;
    ConversationSession dbSession;

    @BeforeEach
    void setUp() {
        service = new ConversationSessionCacheService(repository, redis, JsonMapper.builder().build());
        ReflectionTestUtils.setField(service, "ttlMinutes", 30L);
        when(redis.opsForValue()).thenReturn(valueOps);

        dbSession = ConversationSession.builder().id(7L).whatsappNumber(WA).currentStep(ConversationStep.SERVICES_LIST).build();
        when(repository.findByWhatsappNumber(WA)).thenReturn(Optional.of(dbSession));
    }

    @Test
    void redisDownFallsBackToDatabaseInsteadOfFailing() {
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis is down"));
        doThrow(new RedisConnectionFailureException("Redis is down")).when(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));
        when(repository.save(dbSession)).thenReturn(dbSession);

        ConversationSession loaded = assertDoesNotThrow(() -> service.getOrCreate(WA));
        assertEquals(ConversationStep.SERVICES_LIST, loaded.getCurrentStep());
        assertDoesNotThrow(() -> service.save(loaded));
        verify(repository).save(dbSession);
    }

    @Test
    void corruptCachedJsonIsEvictedAndDatabaseIsUsed() {
        when(valueOps.get("conversation-session-v2:" + WA)).thenReturn("{not valid json");

        ConversationSession loaded = service.getOrCreate(WA);

        assertEquals(7L, loaded.getId());
        verify(redis).delete("conversation-session-v2:" + WA);
    }

    @Test
    void unknownNumberGetsAFreshUnsavedSession() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(repository.findByWhatsappNumber("919111111111")).thenReturn(Optional.empty());

        ConversationSession fresh = service.getOrCreate("919111111111");

        assertEquals(null, fresh.getId());
        assertEquals(ConversationStep.MAIN_MENU, fresh.getCurrentStep());
    }
}
