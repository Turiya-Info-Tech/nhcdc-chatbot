package com.nhcdc.chatbot.dto.whatsapp.outgoing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutgoingTextMessage {

    @Builder.Default
    private String messagingProduct = "whatsapp";
    private String to;
    @Builder.Default
    private String type = "text";
    private TextBody text;

    public static OutgoingTextMessage of(String to, String body) {
        return OutgoingTextMessage.builder()
                .to(to)
                .text(new TextBody(body))
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextBody {
        private String body;
    }
}
