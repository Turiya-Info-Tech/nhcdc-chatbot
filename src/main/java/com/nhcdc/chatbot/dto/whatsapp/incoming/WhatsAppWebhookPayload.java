package com.nhcdc.chatbot.dto.whatsapp.incoming;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Mirrors the Meta WhatsApp Cloud API webhook JSON shape. Jackson is
 * configured with SNAKE_CASE property naming (see application.yaml),
 * so wa_id -> waId, list_reply -> listReply etc. resolve automatically.
 */
@Getter
@Setter
public class WhatsAppWebhookPayload {

    private String object;
    private List<Entry> entry;

    // Turiya's flat (non-Meta) callback shape, used only when "entry" is absent.
    private String from;
    private String phone;
    private String mobile;
    private String sender;
    private String message;
    private String text;
    private String body;
    private String messageId;
    private java.util.Map<String, Object> data;

    @Getter
    @Setter
    public static class Entry {
        private String id;
        private List<Change> changes;
    }

    @Getter
    @Setter
    public static class Change {
        private Value value;
        private String field;
    }

    @Getter
    @Setter
    public static class Value {
        private String messagingProduct;
        private List<Contact> contacts;
        private List<Message> messages;
        private List<Status> statuses;
    }

    /** Delivery callback for a message we sent; "failed" ones carry the reason in errors. */
    @Getter
    @Setter
    public static class Status {
        private String id;
        private String status;
        private String recipientId;
        private List<StatusError> errors;
    }

    @Getter
    @Setter
    public static class StatusError {
        private Integer code;
        private String title;
        private String message;
    }

    @Getter
    @Setter
    public static class Contact {
        private Profile profile;
        private String waId;
    }

    @Getter
    @Setter
    public static class Profile {
        private String name;
    }

    @Getter
    @Setter
    public static class Message {
        private String from;
        private String id;
        private String timestamp;
        private String type;
        private TextBody text;
        private Interactive interactive;
    }

    @Getter
    @Setter
    public static class TextBody {
        private String body;
    }

    @Getter
    @Setter
    public static class Interactive {
        private String type;
        private Reply listReply;
        private Reply buttonReply;
    }

    @Getter
    @Setter
    public static class Reply {
        private String id;
        private String title;
    }
}
