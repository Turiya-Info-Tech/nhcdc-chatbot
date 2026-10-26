package com.nhcdc.chatbot.service.sheet;

/** A lead could not be appended to the Google Sheet; the sync job decides how loudly to log and when to retry. */
public class SheetSyncException extends RuntimeException {

    public SheetSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
