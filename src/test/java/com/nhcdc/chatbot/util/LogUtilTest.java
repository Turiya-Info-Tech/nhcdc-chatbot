package com.nhcdc.chatbot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogUtilTest {

    @Test
    void maskPhoneKeepsOnlyTheLastFourDigits() {
        assertEquals("********3210", LogUtil.maskPhone("919876543210"));
        assertEquals("****", LogUtil.maskPhone("123"));
        assertEquals("****", LogUtil.maskPhone(null));
    }

    @Test
    void shortMessageIdKeepsTheTail() {
        assertEquals("ABCDEFGH", LogUtil.shortMessageId("wamid.HBgMOTE5ABCDEFGH"));
        assertEquals("abc", LogUtil.shortMessageId("abc"));
        assertEquals("-", LogUtil.shortMessageId(null));
        assertEquals("-", LogUtil.shortMessageId(" "));
    }
}
