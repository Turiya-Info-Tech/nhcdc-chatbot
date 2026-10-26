package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.entity.BotSetting;
import com.nhcdc.chatbot.enums.BotSettingKey;
import com.nhcdc.chatbot.repository.BotSettingRepository;
import com.nhcdc.chatbot.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** Reads admin-editable bot text/config from the bot_settings table, falling back to code defaults. */
@Service
@RequiredArgsConstructor
public class BotSettingsService {

    private final BotSettingRepository repository;

    @Cacheable(value = CacheConfig.BOT_SETTINGS, key = "#key.key")
    public String get(BotSettingKey key) {
        return repository.findById(key.getKey())
                .map(BotSetting::getValue)
                .filter(v -> !v.isBlank())
                .orElse(key.getDefaultValue());
    }
}
