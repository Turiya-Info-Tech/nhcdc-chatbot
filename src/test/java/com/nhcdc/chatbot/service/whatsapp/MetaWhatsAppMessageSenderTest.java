package com.nhcdc.chatbot.service.whatsapp;

import com.nhcdc.chatbot.config.WhatsAppMetaProperties;
import com.nhcdc.chatbot.exception.WhatsAppSendException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MetaWhatsAppMessageSenderTest {

    private static final String BASE = "https://graph.test";

    private MockRestServiceServer server;
    private MetaWhatsAppMessageSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();

        WhatsAppMetaProperties properties = new WhatsAppMetaProperties();
        properties.setPhoneNumberId("12345");
        sender = new MetaWhatsAppMessageSender(builder.build(), properties, JsonMapper.builder().build());
    }

    @Test
    void successfulSendDoesNotThrow() {
        server.expect(requestTo(BASE + "/12345/messages")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        sender.sendText("919876543210", "hello");

        server.verify();
    }

    @Test
    void metaRejectionBecomesExceptionWithCodeHintAndMaskedNumber() {
        server.expect(requestTo(BASE + "/12345/messages")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"(#131030) Recipient phone number not in allowed list\",\"type\":\"OAuthException\",\"code\":131030}}"));

        WhatsAppSendException ex = assertThrows(WhatsAppSendException.class,
                () -> sender.sendText("919876543210", "hello"));

        assertEquals(131030, ex.getMetaErrorCode());
        assertEquals(400, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("131030"));
        assertTrue(ex.getMessage().contains("test number"));
        assertTrue(ex.getMessage().contains("********3210"));
        assertFalse(ex.getMessage().contains("919876543210"), "full phone number must not appear in the message");
    }

    @Test
    void metaOutageWithoutBodyStillBecomesException() {
        server.expect(requestTo(BASE + "/12345/messages")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        WhatsAppSendException ex = assertThrows(WhatsAppSendException.class,
                () -> sender.sendText("919876543210", "hello"));

        assertEquals(502, ex.getHttpStatus());
        assertNull(ex.getMetaErrorCode());
    }
}
