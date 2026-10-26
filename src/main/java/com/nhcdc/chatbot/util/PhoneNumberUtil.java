package com.nhcdc.chatbot.util;

public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
    }

    /**
     * Meta sends the "from" field without a leading '+'. Normalize so the
     * same number always maps to one ConversationSession/Lead record
     * regardless of how it arrives.
     */
    public static String normalize(String rawNumber) {
        if (rawNumber == null) {
            return null;
        }
        return rawNumber.trim().replaceAll("[^0-9]", "");
    }
}
