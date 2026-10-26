package com.nhcdc.chatbot.dto.whatsapp.outgoing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutgoingInteractiveButtonMessage {

    @Builder.Default
    private String messagingProduct = "whatsapp";
    private String to;
    @Builder.Default
    private String type = "interactive";
    private Interactive interactive;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Interactive {
        @Builder.Default
        private String type = "button";
        private Body body;
        private Action action;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private String text;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Action {
        private List<Button> buttons;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Button {
        @Builder.Default
        private String type = "reply";
        private Reply reply;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Reply {
        private String id;
        private String title;
    }
}
