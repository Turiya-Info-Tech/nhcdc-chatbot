package com.nhcdc.chatbot.service.whatsapp;

import com.nhcdc.chatbot.dto.whatsapp.outgoing.ButtonOption;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveButtonMessage;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingTextMessage;
import com.nhcdc.chatbot.exception.WhatsAppSendException;
import com.nhcdc.chatbot.util.LogUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends the same Meta-format payloads through the Turiya gateway
 * (POST /REST/directApi/message; the Key and wabaNumber headers are set
 * on the turiyaRestClient bean). Turiya can answer HTTP 200 with an
 * {"error":{...}} body, so the body is checked too. Any failure is thrown
 * as a WhatsAppSendException, same as the Meta sender.
 */
@Service
@Slf4j
public class TuriyaWhatsAppMessageSender implements ProviderWhatsAppMessageSender {

    private static final String PATH = "/REST/directApi/message";

    private static final Pattern TURIYA_ERROR_CODE = Pattern.compile("\"errorCode\"\\s*:\\s*(\\d+)");
    private static final Pattern TURIYA_ERROR_DESCRIPTION = Pattern.compile("\"errorDescription\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient turiyaRestClient;
    private final ObjectMapper objectMapper;

    public TuriyaWhatsAppMessageSender(RestClient turiyaRestClient, ObjectMapper objectMapper) {
        this.turiyaRestClient = turiyaRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "turiya";
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

    private static String turiyaHint(String response) {
        if (response.contains("WABAAccountNotFoundOrExpired")) {
            return "Hint: turiya.waba-number does not match this API key's account, or the account has expired - "
                    + "check TURIYA_WABA_NUMBER and TURIYA_API_KEY.";
        }
        return "";
    }

    private void post(String to, Object body) {
        String kind = body.getClass().getSimpleName();

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new WhatsAppSendException("Could not build the " + kind + " payload for " + LogUtil.maskPhone(to)
                    + ": " + ex.getMessage(), null, null, ex);
        }

        long startNanos = System.nanoTime();
        String response;
        try {
            response = turiyaRestClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            MetaErrorParser.MetaError error = MetaErrorParser.parse(ex.getResponseBodyAsString());
            throw new WhatsAppSendException(String.format("Turiya rejected %s to %s: HTTP %d, error code %s, \"%s\". %s",
                    kind, LogUtil.maskPhone(to), ex.getStatusCode().value(), error.code(), error.message(),
                    MetaErrorParser.hint(error.code())).trim(),
                    error.code(), ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new WhatsAppSendException("Could not reach Turiya to send " + kind + " to "
                    + LogUtil.maskPhone(to) + ": " + ex.getMessage(), null, null, ex);
        }

        // Turiya's own errors look like [{"errorDescription":"...","errorCode":1039}]
        if (response != null && response.contains("\"errorCode\"")) {
            Matcher code = TURIYA_ERROR_CODE.matcher(response);
            Matcher description = TURIYA_ERROR_DESCRIPTION.matcher(response);
            throw new WhatsAppSendException(String.format("Turiya rejected %s to %s: error code %s, \"%s\". %s",
                    kind, LogUtil.maskPhone(to), code.find() ? code.group(1) : "?",
                    description.find() ? description.group(1) : response, turiyaHint(response)).trim(),
                    null, 200, null);
        }
        if (response != null && response.contains("\"error\"")) {
            MetaErrorParser.MetaError error = MetaErrorParser.parse(response);
            throw new WhatsAppSendException(String.format("Turiya returned an error for %s to %s: error code %s, \"%s\". %s",
                    kind, LogUtil.maskPhone(to), error.code(), error.message(), MetaErrorParser.hint(error.code())).trim(),
                    error.code(), 200, null);
        }
        // A normal acceptance carries the message id: {"messages":[{"id":"wamid...."}]}
        if (response == null || !response.contains("wamid")) {
            log.warn("Turiya accepted {} to {} but returned no message id - it was probably not sent. Response: {}",
                    kind, LogUtil.maskPhone(to), response);
        } else {
            log.info("Turiya response for {} to {}: {}", kind, LogUtil.maskPhone(to), response);
        }
        log.info("[TIMING] turiya send {} took {}ms", kind, (System.nanoTime() - startNanos) / 1_000_000);
    }
}
