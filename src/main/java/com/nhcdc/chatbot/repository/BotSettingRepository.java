package com.nhcdc.chatbot.repository;

import com.nhcdc.chatbot.entity.BotSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotSettingRepository extends JpaRepository<BotSetting, String> {
}
