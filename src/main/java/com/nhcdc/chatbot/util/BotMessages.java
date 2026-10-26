package com.nhcdc.chatbot.util;

/**
 * Every fixed piece of wording the parent sees, in one place. (Wording that
 * NHCDC may want to change without a deployment - welcome, consent, thank
 * you, reminders, ... - lives in the bot_settings table instead.)
 *
 * Style: warm, short, one clear next step, emojis as signposts rather than
 * decoration. *asterisks* are WhatsApp bold, _underscores_ italic.
 *
 * WhatsApp limits to respect: reply-button title 20 chars, list-row title
 * 24, list-row description 72, list button label 20, section title 24,
 * message body with buttons/list 1024.
 */
public final class BotMessages {

    private BotMessages() {
    }

    // ------------------------------------------------------------- buttons
    public static final String BTN_TALK_TO_TEAM = "Contact Us";
    public static final String BTN_BACK_TO_MENU = "Back to Menu";
    public static final String BTN_AGREE = "I Agree";
    public static final String BTN_DECLINE = "No, Cancel";

    // ---------------------------------------------------------- list labels
    public static final String ROW_BACK_TO_MENU = "Back to Menu";
    public static final String ROW_CANCEL = "Cancel";
    public static final String ROW_MORE_CENTRES = "More centres";
    public static final String ROW_PREVIOUS_CENTRES = "Previous centres";

    public static final String LIST_BTN_MENU = "Main Menu";
    public static final String LIST_BTN_CENTRE = "Select Centre";

    public static final String SECTION_MENU = "How can we help?";
    public static final String SECTION_CENTRES = "Our Centres";

    // ------------------------------------------------------------ main menu
    public static final String MENU_PROMPT = "How can we help you today?\nPlease choose an option below";
    public static final String MENU_PROMPT_AFTER = "Is there anything else we can help you with?\nPlease choose an option below";

    public static final String MENU_SERVICES_TITLE = "Services for";
    public static final String MENU_SERVICES_DESC = "Explore our therapies & programs";
    public static final String MENU_CENTRES_TITLE = "Find a Centre";
    public static final String MENU_CENTRES_DESC = "Addresses, timings & contact details";
    public static final String MENU_BOOK_TITLE = "Book a Consultation";
    public static final String MENU_BOOK_DESC = "Share your details, we'll get back to you";
    public static final String MENU_TALK_TITLE = "Talk to Our Team";
    public static final String MENU_TALK_DESC = "Phone number & contact details";

    // ------------------------------------------------------------- services
    public static final String SERVICES_HEADER = "*Services for*\n\nWe support children and families with the following concerns and conditions:\n\n";
    public static final String SERVICE_FOLLOW_UP = "Would you like to speak with our team about this?";

    /** Header plus the services as a numbered list, one per line. */
    public static String servicesText(java.util.List<String> names) {
        StringBuilder sb = new StringBuilder(SERVICES_HEADER);
        for (int i = 0; i < names.size(); i++) {
            sb.append(i + 1).append(". ").append(names.get(i));
            if (i < names.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------- centres
    public static final String CENTRES_HEADER = "*Our Centres*\n\n";
    public static final String CENTRES_FOLLOW_UP = "Would you like our team to get in touch with you?";

    public static String centreBlock(String name, String address, String phone, String mapsLink) {
        StringBuilder sb = new StringBuilder("*").append(name).append("*\n").append(address);
        if (phone != null && !phone.isBlank()) {
            sb.append("\n").append(phone);
        }
        if (mapsLink != null && !mapsLink.isBlank()) {
            sb.append("\n").append(mapsLink);
        }
        return sb.toString();
    }

    public static final String NEXT_PROMPT = "What would you like to do next?";

    // ----------------------------------------------------------- lead form
    public static final String FORM_INTRO = "*Let's book your consultation!*\n\nI'll ask a few quick questions (it takes about 2 minutes). You can type *cancel* at any time to stop.";

    public static final String ASK_CENTRE = "*Which centre would you like to connect with?*\n\nPlease choose from the list";
    /** The centre question; with more centres than fit one WhatsApp list, says which page this is. */
    public static String askCentre(int page, int totalPages) {
        return totalPages <= 1 ? ASK_CENTRE : ASK_CENTRE + "\n\n_Page " + page + " of " + totalPages + " - tap *More centres* to see the rest._";
    }

    public static final String ASK_PATIENT_NAME = "What is the *patient's full name*?";
    public static final String ASK_PATIENT_NUMBER = "What is the *patient's contact number*?\n\n_Please enter a 10-digit mobile number, for example 9XXXXXXXXX._";
    public static final String ASK_DATE_OF_BIRTH = "What is the *patient's date of birth*?\n\n_Please type it as DD-MM-YYYY, for example 14-03-2021._";
    public static final String ASK_CONCERN = "Please *briefly describe your concern* in your own words, so our team knows how best to help.";
    public static final String ASK_REFERRAL_DOCTOR = "Who is the *referring doctor*?\n\n_Type *none* if you weren't referred by a doctor._";

    public static final String RETRY_PATIENT_NAME = "That doesn't look like a valid name. Please enter the patient's full name.";
    public static final String RETRY_PATIENT_NUMBER = "That doesn't look like a valid mobile number. Please enter a 10-digit number, for example 9XXXXXXXXX.";
    public static final String RETRY_DATE_OF_BIRTH = "I couldn't read that date. Please type it as *DD-MM-YYYY*, for example 14-03-2021. The date can't be in the future.";
    public static final String RETRY_CONCERN = "Please describe your concern in a few words (up to 1000 characters).";
    public static final String RETRY_REFERRAL_DOCTOR = "Please enter the referring doctor's name, or type *none*.";

    public static final String CONSENT_QUESTION = "Do you agree to share these details with our team?";

    /** Review card shown right before consent, so a typo can be caught before anything is saved. */
    public static String reviewAndConsent(String patientName, String patientNumber, String dateOfBirth,
                                          String centreName, String concern, String referralDoctor, String consentText) {
        return "*Please review your details*\n\n"
                + "*Patient:* " + patientName + "\n"
                + "*Contact:* " + patientNumber + "\n"
                + "*Date of birth:* " + dateOfBirth + "\n"
                + "*Centre:* " + centreName + "\n"
                + "*Concern:* " + TextUtil.truncate(concern, 200) + "\n"
                + "*Referred by:* " + referralDoctor + "\n\n"
                + "*Consent*\n" + consentText + "\n\n"
                + CONSENT_QUESTION;
    }

    public static final String FORM_CANCELLED = "No problem, I've cancelled this request. Nothing has been saved.";
    public static final String CONSENT_DECLINED = "No problem — we haven't saved any of your details. You're welcome to start again anytime.";
    public static final String FORM_DATA_LOST = "Sorry, something went wrong and I lost some of your details. Let's start again.";

    public static String reference(String leadCode) {
        return "\n\n*Your reference:* " + leadCode;
    }

    // ------------------------------------------------------- errors / idle
    public static final String APOLOGY = "Sorry, something went wrong on our side.\n\nPlease try again in a moment, or type *menu* to start over.";
    public static final String UNAVAILABLE = "Sorry, this information isn't available right now.\n\nOur team will be happy to help — tap *Contact Us* below.";

    public static String youWere(String whereTheyWere) {
        return "\n\nYou were " + whereTheyWere + ".";
    }
}
