package com.nhcdc.chatbot.service.whatsapp;

/** One concrete WhatsApp delivery route (Meta Cloud API directly, or the Turiya gateway). */
public interface ProviderWhatsAppMessageSender extends WhatsAppMessageSender {

    /** Name matched (case-insensitively) against whatsapp.provider. */
    String provider();
}
