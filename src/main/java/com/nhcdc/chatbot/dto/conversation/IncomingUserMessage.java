package com.nhcdc.chatbot.dto.conversation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Normalized representation of one inbound WhatsApp message, built by
 * WhatsAppWebhookController from the raw Meta payload so the rest of
 * the app never depends on Meta's exact JSON shape. messageId is the
 * Meta wamid, used to ignore redelivered webhook events.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomingUserMessage {
    private String messageId;
    private String from;
    private String text;
    private String selectedOptionId;
}
