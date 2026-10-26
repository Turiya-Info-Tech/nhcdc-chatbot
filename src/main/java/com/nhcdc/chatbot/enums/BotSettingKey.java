package com.nhcdc.chatbot.enums;

import lombok.Getter;

/**
 * Keys of the bot_settings table, each with a code-level fallback so the
 * bot still answers sensibly if a row is missing or edited away. The
 * fallbacks match the starter rows in V1__init_schema.sql.
 */
@Getter
public enum BotSettingKey {
    WELCOME_MESSAGE("welcome_message",
            "*Welcome to New Horizons Child Development Centre!*\n\nWe're delighted you reached out. Our team supports children and their families with care and expertise.\n\nHow can we help you and your child today?"),
    THANK_YOU_MESSAGE("thank_you_message",
            "*Thank you!* Your request has been received.\n\nOur team will contact you shortly."),
    DUPLICATE_MESSAGE("duplicate_message",
            "*We already have your recent request.*\n\nOur team will contact you shortly."),
    FALLBACK_MESSAGE("fallback_message",
            "Sorry, I didn't quite get that — I can only help with the options in the menu.\n\nPlease tap an option below, or talk to our team and we'll be happy to help."),
    CONSENT_TEXT("consent_text",
            "I confirm that I am the parent/legal guardian of the patient. I agree that New Horizons Child Development Centre may store the details I have shared and contact me on phone or WhatsApp about this enquiry. My details will be used only for this purpose."),
    IDLE_REMINDER_MESSAGE("idle_reminder_message",
            "*Are you still there?*\nWe haven't heard from you for a few minutes.\n\nYou can carry on from where you left off below, or type *menu* to start over."),
    SESSION_EXPIRED_MESSAGE("session_expired_message",
            "*Your session has ended* because we didn't hear back for a while, and the details you had entered were not saved.\n\nNo worries — please start fresh from the menu below"),
    CONTACT_MESSAGE("contact_message",
            "*Contact Our Team*\n\nWe'd love to hear from you. Reach us at:\n\nPhone / WhatsApp: +91 8976964693\nEmail: info@enablemychild.org\n\nTo request a callback instead, choose *Book a Consultation* from the menu.");

    private final String key;
    private final String defaultValue;

    BotSettingKey(String key, String defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }
}
