package com.nhcdc.chatbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Admin-editable text/config the bot reads at runtime (see BotSettingKey). */
@Entity
@Table(name = "bot_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BotSetting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "description")
    private String description;
}
