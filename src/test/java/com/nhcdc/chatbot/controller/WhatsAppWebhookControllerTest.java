package com.nhcdc.chatbot.controller;

import com.nhcdc.chatbot.config.WhatsAppMetaProperties;
import com.nhcdc.chatbot.dto.conversation.IncomingUserMessage;
import com.nhcdc.chatbot.dto.whatsapp.incoming.WhatsAppWebhookPayload;
import com.nhcdc.chatbot.service.ConversationFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WhatsAppWebhookControllerTest {

    @Mock ConversationFlowService flow;

    WhatsAppWebhookController controller;

    @BeforeEach
    void setUp() {
        WhatsAppMetaProperties properties = new WhatsAppMetaProperties();
        properties.setWebhookVerifyToken("secret");
        controller = new WhatsAppWebhookController(properties, flow);
    }

    private WhatsAppWebhookPayload payloadWithMessages(String... ids) {
        WhatsAppWebhookPayload.Value value = new WhatsAppWebhookPayload.Value();
        value.setMessages(java.util.Arrays.stream(ids).map(id -> {
            WhatsAppWebhookPayload.Message message = new WhatsAppWebhookPayload.Message();
            message.setId(id);
            message.setFrom("919000000001");
            WhatsAppWebhookPayload.TextBody body = new WhatsAppWebhookPayload.TextBody();
            body.setBody("hi");
            message.setText(body);
            return message;
        }).toList());
        return wrap(value);
    }

    private WhatsAppWebhookPayload wrap(WhatsAppWebhookPayload.Value value) {
        WhatsAppWebhookPayload.Change change = new WhatsAppWebhookPayload.Change();
        change.setValue(value);
        WhatsAppWebhookPayload.Entry entry = new WhatsAppWebhookPayload.Entry();
        entry.setChanges(List.of(change));
        WhatsAppWebhookPayload payload = new WhatsAppWebhookPayload();
        payload.setEntry(List.of(entry));
        return payload;
    }

    @Test
    void failingMessageDoesNotStopOthersAndStillReturns200() {
        doThrow(new IllegalStateException("boom")).when(flow).handleIncomingMessage(any());

        ResponseEntity<Void> response = controller.receive(payloadWithMessages("m1", "m2"));

        assertEquals(200, response.getStatusCode().value());
        verify(flow, times(2)).handleIncomingMessage(any(IncomingUserMessage.class));
    }

    @Test
    void failedDeliveryStatusIsHandledWithoutProcessingAMessage() {
        WhatsAppWebhookPayload.StatusError error = new WhatsAppWebhookPayload.StatusError();
        error.setCode(131047);
        error.setTitle("Re-engagement message");
        WhatsAppWebhookPayload.Status status = new WhatsAppWebhookPayload.Status();
        status.setId("wamid.XYZ12345");
        status.setStatus("failed");
        status.setRecipientId("919000000001");
        status.setErrors(List.of(error));
        WhatsAppWebhookPayload.Value value = new WhatsAppWebhookPayload.Value();
        value.setStatuses(List.of(status));

        ResponseEntity<Void> response = controller.receive(wrap(value));

        assertEquals(200, response.getStatusCode().value());
        verify(flow, never()).handleIncomingMessage(any());
    }

    @Test
    void emptyPayloadIsAcknowledged() {
        ResponseEntity<Void> response = controller.receive(new WhatsAppWebhookPayload());

        assertEquals(200, response.getStatusCode().value());
        verify(flow, never()).handleIncomingMessage(any());
    }

    @Test
    void verificationRejectsWrongToken() {
        assertEquals(403, controller.verify("subscribe", "wrong", "challenge").getStatusCode().value());
        assertEquals("challenge", controller.verify("subscribe", "secret", "challenge").getBody());
    }
}
