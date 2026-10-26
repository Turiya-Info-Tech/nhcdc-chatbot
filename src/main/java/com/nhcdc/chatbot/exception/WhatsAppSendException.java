package com.nhcdc.chatbot.exception;

import lombok.Getter;

/**
 * An outbound WhatsApp message was not accepted by Meta (rejected, or Meta
 * could not be reached). The message already explains what went wrong, so
 * callers log it once instead of re-describing it.
 */
@Getter
public class WhatsAppSendException extends RuntimeException {

    private final Integer metaErrorCode;
    private final Integer httpStatus;

    public WhatsAppSendException(String message, Integer metaErrorCode, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.metaErrorCode = metaErrorCode;
        this.httpStatus = httpStatus;
    }
}
