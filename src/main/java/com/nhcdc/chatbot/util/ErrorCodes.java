package com.nhcdc.chatbot.util;

/**
 * Searchable prefixes for every error the app logs on purpose.
 * Grep the log for "[E_" to list problems, or for one code to see only that kind.
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    /** Meta rejected or could not receive an outbound WhatsApp message. */
    public static final String SEND_FAILED = "[E_SEND_FAILED]";
    /** Meta reported that a message we sent could not be delivered to the user. */
    public static final String DELIVERY_FAILED = "[E_DELIVERY_FAILED]";
    /** Unexpected exception while handling an incoming message. */
    public static final String FLOW_FAILED = "[E_FLOW_FAILED]";
    /** Incoming webhook body could not be read or was missing required fields. */
    public static final String WEBHOOK_PARSE = "[E_WEBHOOK_PARSE]";
    /** Redis session cache unreadable/unwritable (the DB is used instead). */
    public static final String SESSION_CACHE = "[E_SESSION_CACHE]";
    /** A saved form draft could not be read back. */
    public static final String DRAFT_CORRUPT = "[E_DRAFT_CORRUPT]";
    /** A lead could not be appended to the Google Sheet (it is retried automatically). */
    public static final String SHEET_SYNC = "[E_SHEET_SYNC]";
    /** Missing or suspicious configuration detected at startup. */
    public static final String CONFIG = "[E_CONFIG]";
}
