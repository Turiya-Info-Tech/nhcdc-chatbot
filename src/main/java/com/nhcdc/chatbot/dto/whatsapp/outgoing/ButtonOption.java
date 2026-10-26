package com.nhcdc.chatbot.dto.whatsapp.outgoing;

/**
 * Meta caps reply button titles at 20 characters and allows at most 3
 * per message - callers must keep within that (see WhatsAppMessageSender).
 */
public record ButtonOption(String id, String title) {
}
