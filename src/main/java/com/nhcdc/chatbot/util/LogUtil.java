package com.nhcdc.chatbot.util;

/** Helpers that keep personal data out of the logs while still letting one conversation be followed. */
public final class LogUtil {

    /** MDC keys referenced by the logging pattern in application.yaml. */
    public static final String MDC_USER = "user";
    public static final String MDC_MESSAGE = "msg";

    private static final int VISIBLE_PHONE_DIGITS = 4;
    private static final int SHORT_ID_LENGTH = 8;

    private LogUtil() {
    }

    /** "919876543210" -> "********3210". */
    public static String maskPhone(String number) {
        if (number == null || number.length() <= VISIBLE_PHONE_DIGITS) {
            return "****";
        }
        return "*".repeat(number.length() - VISIBLE_PHONE_DIGITS) + number.substring(number.length() - VISIBLE_PHONE_DIGITS);
    }

    /** Meta message ids ("wamid.HBgM...") are long; the tail is enough to correlate log lines. */
    public static String shortMessageId(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return "-";
        }
        return messageId.length() <= SHORT_ID_LENGTH ? messageId : messageId.substring(messageId.length() - SHORT_ID_LENGTH);
    }
}
