package com.nhcdc.chatbot.util;

public final class TextUtil {

    private TextUtil() {
    }

    /**
     * WhatsApp list rows have separate hard caps - title 24 chars,
     * description 72 chars - and Meta rejects the whole message if any
     * single row exceeds either. Used for admin-entered content
     * (service/centre name and text) whose length isn't otherwise
     * controlled at data-entry time.
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1).stripTrailing() + "…";
    }
}
