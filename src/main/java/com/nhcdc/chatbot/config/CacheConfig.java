package com.nhcdc.chatbot.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-memory cache for the read-mostly content the bot shows on every message.
 * Entries expire {@code cache.content.ttl-minutes} after being written, so an
 * admin edit shows up within that window. Set {@code cache.content.enabled=false}
 * to turn caching off entirely.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CENTRES = "centres";
    public static final String SERVICES = "services";
    public static final String BOT_SETTINGS = "botSettings";

    @Bean
    public CacheManager cacheManager(
            @Value("${cache.content.enabled:true}") boolean enabled,
            @Value("${cache.content.ttl-minutes:10}") long ttlMinutes,
            @Value("${cache.content.max-entries:1000}") long maxEntries) {
        if (!enabled) {
            return new NoOpCacheManager();
        }
        CaffeineCacheManager manager = new CaffeineCacheManager(CENTRES, SERVICES, BOT_SETTINGS);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .maximumSize(maxEntries));
        return manager;
    }
}
