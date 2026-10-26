package com.nhcdc.chatbot.service.whatsapp;

import com.nhcdc.chatbot.dto.whatsapp.outgoing.ButtonOption;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage;

import java.util.List;

/**
 * Boundary for sending outbound WhatsApp messages. Kept behind an
 * interface so the Meta Cloud API implementation can be swapped for a
 * BSP later without touching ConversationFlowService.
 */
public interface WhatsAppMessageSender {

    void sendText(String to, String body);

    void sendList(String to, String bodyText, String buttonLabel, List<OutgoingInteractiveListMessage.Section> sections);

    /**
     * Up to 3 reply buttons rendered inline under bodyText, in the same
     * message - use this instead of sendText()+sendList() whenever there
     * are 3 or fewer options, so detail content and its actions arrive
     * as one message instead of two.
     */
    void sendButtons(String to, String bodyText, List<ButtonOption> buttons);
}
