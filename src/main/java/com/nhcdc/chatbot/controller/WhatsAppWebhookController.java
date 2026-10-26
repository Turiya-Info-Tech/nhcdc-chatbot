package com.nhcdc.chatbot.controller;

import com.nhcdc.chatbot.config.WhatsAppMetaProperties;
import com.nhcdc.chatbot.dto.conversation.IncomingUserMessage;
import com.nhcdc.chatbot.dto.whatsapp.incoming.WhatsAppWebhookPayload;
import com.nhcdc.chatbot.service.ConversationFlowService;
import com.nhcdc.chatbot.service.whatsapp.MetaErrorParser;
import com.nhcdc.chatbot.util.ErrorCodes;
import com.nhcdc.chatbot.util.LogUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Always answers 200 to Meta once the payload was readable: a message that
 * fails is logged (with an error code) instead of being retried by Meta
 * for days, and one bad message never blocks the others in the same batch.
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppWebhookController {

    private final WhatsAppMetaProperties properties;
    private final ConversationFlowService conversationFlowService;

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && properties.getWebhookVerifyToken().equals(verifyToken)) {
            log.info("WhatsApp webhook verification succeeded");
            return ResponseEntity.ok(challenge);
        }
        log.warn("WhatsApp webhook verification failed - mode={}", mode);
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody WhatsAppWebhookPayload payload) {
        logFailedDeliveries(payload);

        List<IncomingUserMessage> messages;
        try {
            messages = extractMessages(payload);
        } catch (Exception ex) {
            log.error("{} Could not read the incoming webhook payload", ErrorCodes.WEBHOOK_PARSE, ex);
            return ResponseEntity.ok().build();
        }

        if (payload.getEntry() == null) {
            messages = extractVendorMessage(payload);
        }

        if (messages.isEmpty()) {
            // Meta also posts delivery/read status callbacks to this same
            // webhook - they carry no "messages" array, so there's nothing
            // to process. Expected traffic, not worth INFO-level noise.
            log.debug("Received WhatsApp webhook payload with no messages (likely a status callback)");
        } else {
            log.info("Received WhatsApp webhook payload with {} message(s)", messages.size());
        }

        for (IncomingUserMessage message : messages) {
            try {
                conversationFlowService.handleIncomingMessage(message);
            } catch (Exception ex) {
                // handleIncomingMessage already contains its own failures; this is the last safety net.
                log.error("{} Message handler escaped its own error handling", ErrorCodes.FLOW_FAILED, ex);
            }
        }
        return ResponseEntity.ok().build();
    }

    /** Meta reports undeliverable messages only through status callbacks, so surface them in the log. */
    private void logFailedDeliveries(WhatsAppWebhookPayload payload) {
        try {
            if (payload.getEntry() == null) {
                return;
            }
            payload.getEntry().stream()
                    .filter(entry -> entry.getChanges() != null)
                    .flatMap(entry -> entry.getChanges().stream())
                    .map(WhatsAppWebhookPayload.Change::getValue)
                    .filter(value -> value != null && value.getStatuses() != null)
                    .flatMap(value -> value.getStatuses().stream())
                    .filter(status -> "failed".equals(status.getStatus()))
                    .forEach(status -> {
                        if (status.getErrors() == null || status.getErrors().isEmpty()) {
                            log.warn("{} Message {} to {} failed (no error details from Meta)",
                                    ErrorCodes.DELIVERY_FAILED, LogUtil.shortMessageId(status.getId()),
                                    LogUtil.maskPhone(status.getRecipientId()));
                            return;
                        }
                        status.getErrors().forEach(error -> log.warn("{} Message {} to {} failed: code {}, \"{}\" {}",
                                ErrorCodes.DELIVERY_FAILED, LogUtil.shortMessageId(status.getId()),
                                LogUtil.maskPhone(status.getRecipientId()), error.getCode(),
                                error.getTitle() != null ? error.getTitle() : error.getMessage(),
                                MetaErrorParser.hint(error.getCode())));
                    });
        } catch (Exception ex) {
            log.warn("{} Could not inspect status callbacks: {}", ErrorCodes.WEBHOOK_PARSE, ex.getMessage());
        }
    }

    private List<IncomingUserMessage> extractMessages(WhatsAppWebhookPayload payload) {
        if (payload.getEntry() == null) {
            return List.of();
        }
        return payload.getEntry().stream()
                .filter(entry -> entry.getChanges() != null)
                .flatMap(entry -> entry.getChanges().stream())
                .map(WhatsAppWebhookPayload.Change::getValue)
                .filter(value -> value != null && value.getMessages() != null)
                .flatMap(value -> value.getMessages().stream())
                .map(this::toIncomingUserMessage)
                .toList();
    }

    /**
     * Turiya may post a flat {from|phone|mobile|sender, message|text|body, message_id}
     * object (optionally nested under "data") instead of Meta's entry/changes shape.
     */
    private List<IncomingUserMessage> extractVendorMessage(WhatsAppWebhookPayload payload) {
        Map<String, Object> data = payload.getData() == null ? Map.of() : payload.getData();
        String from = firstNonBlank(payload.getFrom(), payload.getPhone(), payload.getMobile(), payload.getSender(),
                str(data.get("from")), str(data.get("phone")), str(data.get("mobile")), str(data.get("sender")));
        String text = firstNonBlank(payload.getMessage(), payload.getText(), payload.getBody(),
                str(data.get("message")), str(data.get("text")), str(data.get("body")));
        if (from == null || text == null) {
            return List.of();
        }
        String id = firstNonBlank(payload.getMessageId(), str(data.get("message_id")), str(data.get("messageId")));
        return List.of(IncomingUserMessage.builder()
                .messageId(id == null ? null : "vendor:" + id)
                .from(from)
                .text(text)
                .build());
    }

    private String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private IncomingUserMessage toIncomingUserMessage(WhatsAppWebhookPayload.Message message) {
        String selectedOptionId = null;
        if (message.getInteractive() != null) {
            if (message.getInteractive().getListReply() != null) {
                selectedOptionId = message.getInteractive().getListReply().getId();
            } else if (message.getInteractive().getButtonReply() != null) {
                selectedOptionId = message.getInteractive().getButtonReply().getId();
            }
        }
        String text = message.getText() != null ? message.getText().getBody() : null;
        return IncomingUserMessage.builder()
                .messageId(message.getId())
                .from(message.getFrom())
                .text(text)
                .selectedOptionId(selectedOptionId)
                .build();
    }
}
