package com.nhcdc.chatbot.service.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaErrorParserTest {

    @Test
    void parsesCodeAndMessageFromMetaErrorBody() {
        MetaErrorParser.MetaError error = MetaErrorParser.parse(
                "{\"error\":{\"message\":\"Invalid OAuth access token.\",\"type\":\"OAuthException\",\"code\":190,\"fbtrace_id\":\"abc\"}}");

        assertEquals(190, error.code());
        assertEquals("Invalid OAuth access token.", error.message());
    }

    @Test
    void handlesEscapedQuotesInMessage() {
        MetaErrorParser.MetaError error = MetaErrorParser.parse("{\"error\":{\"message\":\"Bad \\\"row\\\" id\",\"code\":100}}");

        assertEquals(100, error.code());
        assertEquals("Bad \\\"row\\\" id", error.message());
    }

    @Test
    void emptyOrNonJsonBodyGivesNulls() {
        assertNull(MetaErrorParser.parse(null).code());
        assertNull(MetaErrorParser.parse("").code());
        assertNull(MetaErrorParser.parse("<html>Bad gateway</html>").message());
    }

    @Test
    void knownCodesHaveHintsAndUnknownOnesDoNot() {
        assertTrue(MetaErrorParser.hint(190).contains("token"));
        assertTrue(MetaErrorParser.hint(131030).contains("allowed list"));
        assertEquals("", MetaErrorParser.hint(999999));
        assertEquals("", MetaErrorParser.hint(null));
    }
}
