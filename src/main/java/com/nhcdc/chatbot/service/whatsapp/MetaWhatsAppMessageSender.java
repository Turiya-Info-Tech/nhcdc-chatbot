package com.nhcdc.chatbot.service.whatsapp;

import com.nhcdc.chatbot.config.WhatsAppMetaProperties;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.ButtonOption;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveButtonMessage;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingTextMessage;
import com.nhcdc.chatbot.exception.WhatsAppSendException;
import com.nhcdc.chatbot.util.LogUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Sends messages through the Meta Graph API. Any failure is thrown as a
 * WhatsAppSendException (never swallowed) so the caller does not carry on
 * as if the user had received the message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppMessageSender implements ProviderWhatsAppMessageSender {

    private final RestClient whatsAppRestClient;
    private final WhatsAppMetaProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String provider() {
        return "meta";
    }

    @Override
    public void sendText(String to, String body) {
        post(to, OutgoingTextMessage.of(to, body));
    }

    @Override
    public void sendList(String to, String bodyText, String buttonLabel, List<OutgoingInteractiveListMessage.Section> sections) {
        OutgoingInteractiveListMessage message = OutgoingInteractiveListMessage.builder()
                .to(to)
                .interactive(OutgoingInteractiveListMessage.Interactive.builder()
                        .body(new OutgoingInteractiveListMessage.Body(bodyText))
                        .action(new OutgoingInteractiveListMessage.Action(buttonLabel, sections))
                        .build())
                .build();
        post(to, message);
    }

    @Override
    public void sendButtons(String to, String bodyText, List<ButtonOption> buttons) {
        List<OutgoingInteractiveButtonMessage.Button> replyButtons = buttons.stream()
                .map(b -> OutgoingInteractiveButtonMessage.Button.builder()
                        .reply(new OutgoingInteractiveButtonMessage.Reply(b.id(), b.title()))
                        .build())
                .toList();

        OutgoingInteractiveButtonMessage message = OutgoingInteractiveButtonMessage.builder()
                .to(to)
                .interactive(OutgoingInteractiveButtonMessage.Interactive.builder()
                        .body(new OutgoingInteractiveButtonMessage.Body(bodyText))
                        .action(new OutgoingInteractiveButtonMessage.Action(replyButtons))
                        .build())
                .build();
        post(to, message);
    }

    private void post(String to, Object body) {
        String kind = body.getClass().getSimpleName();
        String path = "/" + properties.getPhoneNumberId() + "/messages";

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new WhatsAppSendException("Could not build the " + kind + " payload for " + LogUtil.maskPhone(to)
                    + ": " + ex.getMessage(), null, null, ex);
        }

        long startNanos = System.nanoTime();
        try {
            whatsAppRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[TIMING] send {} took {}ms", kind, (System.nanoTime() - startNanos) / 1_000_000);
        } catch (RestClientResponseException ex) {
            MetaErrorParser.MetaError error = MetaErrorParser.parse(ex.getResponseBodyAsString());
            String hint = MetaErrorParser.hint(error.code());
            throw new WhatsAppSendException(String.format("Meta rejected %s to %s: HTTP %d, error code %s, \"%s\". %s",
                    kind, LogUtil.maskPhone(to), ex.getStatusCode().value(), error.code(), error.message(), hint).trim(),
                    error.code(), ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new WhatsAppSendException("Could not reach the Meta Graph API to send " + kind + " to "
                    + LogUtil.maskPhone(to) + ": " + ex.getMessage(), null, null, ex);
        }
    }
}
