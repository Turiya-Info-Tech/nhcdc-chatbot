package com.nhcdc.chatbot.util;

import com.nhcdc.chatbot.enums.BotSettingKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WhatsApp rejects the WHOLE message when any title/label/body is over its
 * limit, so a wording edit that is a few characters too long would silently
 * stop that screen from sending. Lengths are measured in UTF-16 units, which
 * is stricter than Meta's own counting (emojis count as 2 here).
 */
class BotMessagesTest {

    private static void assertMax(int max, String... values) {
        for (String value : values) {
            assertTrue(value.length() <= max, "'" + value + "' is " + value.length() + " chars, limit " + max);
        }
    }

    @Test
    void replyButtonTitlesFitTwentyCharacters() {
        assertMax(20, BotMessages.BTN_TALK_TO_TEAM, BotMessages.BTN_BACK_TO_MENU,
                BotMessages.BTN_AGREE, BotMessages.BTN_DECLINE);
    }

    @Test
    void listRowTitlesFitTwentyFourCharacters() {
        assertMax(24, BotMessages.ROW_BACK_TO_MENU, BotMessages.ROW_CANCEL,
                BotMessages.ROW_MORE_CENTRES, BotMessages.ROW_PREVIOUS_CENTRES,
                BotMessages.MENU_SERVICES_TITLE, BotMessages.MENU_CENTRES_TITLE,
                BotMessages.MENU_BOOK_TITLE, BotMessages.MENU_TALK_TITLE);
    }

    @Test
    void listRowDescriptionsFitSeventyTwoCharacters() {
        assertMax(72, BotMessages.MENU_SERVICES_DESC, BotMessages.MENU_CENTRES_DESC,
                BotMessages.MENU_BOOK_DESC, BotMessages.MENU_TALK_DESC);
    }

    @Test
    void listButtonLabelsFitTwentyCharactersAndSectionTitlesTwentyFour() {
        assertMax(20, BotMessages.LIST_BTN_MENU, BotMessages.LIST_BTN_CENTRE);
        assertMax(24, BotMessages.SECTION_MENU, BotMessages.SECTION_CENTRES);
    }

    @Test
    void fixedBodiesAndSettingDefaultsFitTheMessageBodyLimit() {
        List<String> bodies = List.of(
                BotMessages.MENU_PROMPT, BotMessages.MENU_PROMPT_AFTER, BotMessages.SERVICES_HEADER,
                BotMessages.SERVICE_FOLLOW_UP, BotMessages.CENTRES_FOLLOW_UP,
                BotMessages.NEXT_PROMPT, BotMessages.ASK_CENTRE, BotMessages.UNAVAILABLE, BotMessages.APOLOGY,
                BotMessages.CONSENT_QUESTION);
        bodies.forEach(body -> assertMax(Constants.MAX_BUTTON_BODY_LENGTH, body));

        // Shown as the body of the main-menu list or a button message.
        for (BotSettingKey key : List.of(BotSettingKey.WELCOME_MESSAGE, BotSettingKey.FALLBACK_MESSAGE,
                BotSettingKey.SESSION_EXPIRED_MESSAGE)) {
            assertMax(Constants.MAX_BUTTON_BODY_LENGTH, key.getDefaultValue());
        }
    }

    @Test
    void reviewCardWithTypicalAnswersFitsInOneMessage() {
        String card = BotMessages.reviewAndConsent("Aarav Sharma", "9876543210", "14-03-2021", "NHCDC - Andheri",
                "x".repeat(300), "Dr. Mehta", BotSettingKey.CONSENT_TEXT.getDefaultValue());

        assertMax(Constants.MAX_BUTTON_BODY_LENGTH, card);
    }
}
