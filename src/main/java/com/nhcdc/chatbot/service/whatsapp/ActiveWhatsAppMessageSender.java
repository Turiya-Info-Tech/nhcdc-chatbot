package com.nhcdc.chatbot.service.whatsapp;

import com.nhcdc.chatbot.dto.whatsapp.outgoing.ButtonOption;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The sender the rest of the app injects. Delegates to the provider named
 * by whatsapp.provider ("meta" or "turiya"), so switching route is a
 * config change only.
 */
@Component
@Primary
@Slf4j
public class ActiveWhatsAppMessageSender implements WhatsAppMessageSender {

    private final ProviderWhatsAppMessageSender delegate;

    public ActiveWhatsAppMessageSender(List<ProviderWhatsAppMessageSender> senders,
                                       @Value("${whatsapp.provider:meta}") String activeProvider) {
        Map<String, ProviderWhatsAppMessageSender> byProvider = senders.stream()
                .collect(Collectors.toMap(s -> normalize(s.provider()), Function.identity(), (left, right) -> left));
        ProviderWhatsAppMessageSender selected = byProvider.get(normalize(activeProvider));
        if (selected == null) {
            throw new IllegalStateException("Unsupported whatsapp.provider=" + activeProvider
                    + ". Available: " + byProvider.keySet());
        }
        this.delegate = selected;
        log.info("WhatsApp outbound provider: {}", selected.provider());
    }

    @Override
    public void sendText(String to, String body) {
        delegate.sendText(to, body);
    }

    @Override
    public void sendList(String to, String bodyText, String buttonLabel, List<OutgoingInteractiveListMessage.Section> sections) {
        delegate.sendList(to, bodyText, buttonLabel, sections);
    }

    @Override
    public void sendButtons(String to, String bodyText, List<ButtonOption> buttons) {
        delegate.sendButtons(to, bodyText, buttons);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
