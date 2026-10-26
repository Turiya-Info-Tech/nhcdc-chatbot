package com.nhcdc.chatbot.service.whatsapp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the error code and message out of a Meta Graph API error body
 * ({"error":{"message":"...","code":190,...}}) and adds a plain-English
 * hint for the codes we are most likely to hit.
 */
public final class MetaErrorParser {

    public record MetaError(Integer code, String message) {
    }

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private MetaErrorParser() {
    }

    public static MetaError parse(String body) {
        if (body == null || body.isBlank()) {
            return new MetaError(null, null);
        }
        Matcher code = CODE.matcher(body);
        Matcher message = MESSAGE.matcher(body);
        return new MetaError(code.find() ? Integer.valueOf(code.group(1)) : null, message.find() ? message.group(1) : null);
    }

    /** @return advice for a known Meta error code, or an empty string. */
    public static String hint(Integer code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case 190 -> "Hint: the access token is invalid or expired - use a permanent system-user token.";
            case 131030 -> "Hint: recipient is not in the allowed list - the phone number ID is probably still a Meta test number.";
            case 131047 -> "Hint: more than 24 hours since the user's last message - only a template message can be sent.";
            case 131026 -> "Hint: message undeliverable - the user may not be on WhatsApp or has not accepted the latest terms.";
            case 131056, 130429, 80007 -> "Hint: rate limit reached - slow down or retry later.";
            case 133010 -> "Hint: the sending phone number is not registered on the WhatsApp Business Platform.";
            case 100 -> "Hint: invalid parameter - check the message payload (row/button limits, empty fields).";
            default -> "";
        };
    }
}
