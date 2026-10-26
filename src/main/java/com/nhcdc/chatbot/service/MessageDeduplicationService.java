package com.nhcdc.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Meta redelivers a webhook event if it does not get a timely 200, which
 * would otherwise double-process the same message (and double-create
 * leads). Remembers each WhatsApp message id for a day. If Redis is
 * unavailable we fail open and process the message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageDeduplicationService {

    private static final String KEY_PREFIX = "wa-message:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /** @return true if this is the first time the id is seen (process it), false for a redelivery. */
    public boolean markIfNew(String messageId) {
        if (messageId == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + messageId, "1", TTL));
        } catch (Exception ex) {
            log.warn("Message dedupe unavailable, processing anyway: {}", ex.getMessage());
            return true;
        }
    }
}
