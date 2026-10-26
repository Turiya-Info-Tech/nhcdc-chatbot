package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.entity.ConversationSession;
import com.nhcdc.chatbot.repository.ConversationSessionRepository;
import com.nhcdc.chatbot.util.ErrorCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * Cache-aside + write-through in front of ConversationSessionRepository.
 * Postgres remains the source of truth; Redis just avoids a DB round
 * trip for the common case of an already-active conversation. If the
 * Redis entry is missing, expired, unreadable, or Redis itself is down,
 * we transparently fall back to the DB - callers never need to know which
 * path was taken, and a cache problem never stops the bot.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationSessionCacheService {

    private static final String KEY_PREFIX = "conversation-session-v2:";

    private final ConversationSessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${conversation.session-cache-ttl-minutes}")
    private long ttlMinutes;

    public ConversationSession getOrCreate(String whatsappNumber) {
        ConversationSession cached = readFromCache(whatsappNumber);
        if (cached != null) {
            return cached;
        }

        log.debug("Session cache miss - falling back to DB");
        ConversationSession session = sessionRepository.findByWhatsappNumber(whatsappNumber)
                .orElseGet(() -> ConversationSession.builder().whatsappNumber(whatsappNumber).build());

        if (session.getId() != null) {
            writeToCache(session);
        }
        return session;
    }

    public void save(ConversationSession session) {
        ConversationSession saved;
        try {
            saved = sessionRepository.save(session);
        } catch (ObjectOptimisticLockingFailureException ex) {
            // Cached session pointed at a DB row that no longer exists
            // (e.g. the DB was reset/recreated while Redis kept serving
            // a stale entry off its TTL). Self-heal by re-inserting as a
            // brand new row instead of leaving the user stuck forever.
            log.warn("Session (id={}) not found in DB on save - recreating as a new row", session.getId());
            session.setId(null);
            saved = sessionRepository.save(session);
        }
        writeToCache(saved);
    }

    private ConversationSession readFromCache(String whatsappNumber) {
        String key = cacheKey(whatsappNumber);
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, ConversationSession.class);
        } catch (Exception ex) {
            log.warn("{} Could not read the cached session ({}: {}) - using the database instead",
                    ErrorCodes.SESSION_CACHE, ex.getClass().getSimpleName(), ex.getMessage());
            evictQuietly(key);
            return null;
        }
    }

    private void writeToCache(ConversationSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(cacheKey(session.getWhatsappNumber()), json, Duration.ofMinutes(ttlMinutes));
        } catch (Exception ex) {
            log.warn("{} Could not cache the session ({}: {}) - the database copy is still saved",
                    ErrorCodes.SESSION_CACHE, ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    /** Removes an unreadable entry so it stops failing every message until its TTL ends. */
    private void evictQuietly(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            log.debug("Could not evict cache key {}: {}", key, ex.getMessage());
        }
    }

    private String cacheKey(String whatsappNumber) {
        return KEY_PREFIX + whatsappNumber;
    }
}
