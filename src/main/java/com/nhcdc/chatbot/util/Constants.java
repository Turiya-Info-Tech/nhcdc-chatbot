package com.nhcdc.chatbot.util;

import java.util.Set;

public final class Constants {

    private Constants() {
    }

    /** Typed words that bring a user back to the main menu outside the form. */
    public static final Set<String> GREETING_KEYWORDS = Set.of("hi", "hello", "hey", "start", "menu", "help");

    /** Typed words that abandon the lead form (so "hi"/"help" can be real answers). */
    public static final Set<String> FORM_EXIT_KEYWORDS = Set.of("menu", "cancel");

    public static final String MENU_SERVICES = "menu_services";
    public static final String MENU_CENTRES = "menu_centres";
    public static final String MENU_BOOK_CONSULTATION = "menu_book_consultation";
    public static final String MENU_TALK_TO_TEAM = "menu_talk_to_team";

    public static final String SERVICE_ROW_PREFIX = "service_";
    public static final String CENTRE_ROW_PREFIX = "centre_";
    /** Row id of the "More centres" / "Previous centres" rows in the paged centre picker. */
    public static final String CENTRE_PAGE_PREFIX = "centrepage_";

    public static final String BACK_TO_MENU = "back_to_menu";
    public static final String TALK_TO_TEAM_ACTION = "go_talk_to_team";

    public static final String CONSENT_YES = "consent_yes";
    public static final String CONSENT_NO = "consent_no";

    // WhatsApp Cloud API hard limits.
    public static final int MAX_LIST_ROWS = 10;
    public static final int MAX_BUTTON_BODY_LENGTH = 1024;
    public static final int MAX_TEXT_LENGTH = 4096;
    public static final int MAX_ROW_TITLE_LENGTH = 24;
}
