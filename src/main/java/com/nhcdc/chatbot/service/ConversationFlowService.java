package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.dto.conversation.IncomingUserMessage;
import com.nhcdc.chatbot.dto.lead.LeadDraft;
import com.nhcdc.chatbot.dto.lead.NewLeadCommand;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.ButtonOption;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage.Row;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage.Section;
import com.nhcdc.chatbot.entity.Centre;
import com.nhcdc.chatbot.entity.ConversationSession;
import com.nhcdc.chatbot.entity.ServiceOffering;
import com.nhcdc.chatbot.enums.BotSettingKey;
import com.nhcdc.chatbot.enums.ConversationStep;
import com.nhcdc.chatbot.enums.LeadFieldStep;
import com.nhcdc.chatbot.exception.WhatsAppSendException;
import com.nhcdc.chatbot.service.whatsapp.WhatsAppMessageSender;
import com.nhcdc.chatbot.util.BotMessages;
import com.nhcdc.chatbot.util.Constants;
import com.nhcdc.chatbot.util.ErrorCodes;
import com.nhcdc.chatbot.util.LeadInputValidator;
import com.nhcdc.chatbot.util.LogUtil;
import com.nhcdc.chatbot.util.PhoneNumberUtil;
import com.nhcdc.chatbot.util.TextUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The bot's state machine. One entry point (handleIncomingMessage) reads
 * the caller's ConversationSession, decides what to do next based on
 * currentStep, sends the appropriate WhatsApp response, and persists the
 * updated session.
 *
 * Phase 1 menu: Services, Find a Centre, Talk to Our Team.
 * "Talk to Our Team" (also reachable from service and centre screens)
 * runs ONE lead form: centre, patient name, patient number, date of
 * birth, concern, referral doctor, then a review card with consent.
 *
 * Menu item ids come from Constants; dynamic rows are prefixed and parsed
 * back with their DB id. Fixed wording lives in BotMessages; wording NHCDC
 * owns (welcome, consent, reminders, ...) is read from bot_settings via
 * BotSettingsService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationFlowService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /** Centres per page in the paged picker: leaves 3 rows for "More", "Previous" and "Cancel". */
    private static final int CENTRES_PER_PAGE = Constants.MAX_LIST_ROWS - 3;

    private final ConversationSessionCacheService sessionCacheService;
    private final ContentService contentService;
    private final BotSettingsService settings;
    private final LeadService leadService;
    private final WhatsAppMessageSender messageSender;
    private final MessageDeduplicationService deduplication;
    private final ObjectMapper objectMapper;

    @Value("${conversation.idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    @Value("${conversation.idle-reminder-minutes:5}")
    private long idleReminderMinutes;

    /**
     * Two threads concurrently loading/saving the same ConversationSession
     * corrupts conversation state (and can throw StaleObjectStateException
     * on save) - serializing per number keeps one user's messages strictly
     * ordered without blocking unrelated users. Single-instance-only: a
     * multi-instance deployment would need a distributed lock instead.
     */
    private final ConcurrentHashMap<String, Object> perNumberLocks = new ConcurrentHashMap<>();

    /**
     * Never throws: every failure is logged with a searchable error code
     * and the webhook still gets a 200, so Meta does not keep redelivering
     * a message that can never succeed. The conversation state is only
     * saved after the whole reply succeeded, so a failed message leaves
     * the user on the same step and they can simply try again.
     */
    public void handleIncomingMessage(IncomingUserMessage message) {
        String waNumber = PhoneNumberUtil.normalize(message.getFrom());
        if (waNumber == null || waNumber.isBlank()) {
            log.warn("{} Ignoring webhook message {} without a sender number", ErrorCodes.WEBHOOK_PARSE, message.getMessageId());
            return;
        }
        MDC.put(LogUtil.MDC_USER, LogUtil.maskPhone(waNumber));
        MDC.put(LogUtil.MDC_MESSAGE, LogUtil.shortMessageId(message.getMessageId()));
        try {
            long startNanos = System.nanoTime();
            if (!deduplication.markIfNew(message.getMessageId())) {
                log.info("Ignoring redelivered WhatsApp message");
                return;
            }
            long dedupMs = elapsedMs(startNanos);
            Object lock = perNumberLocks.computeIfAbsent(waNumber, k -> new Object());
            long lockStartNanos = System.nanoTime();
            synchronized (lock) {
                long lockWaitMs = elapsedMs(lockStartNanos);
                processMessage(waNumber, message);
                log.info("[TIMING] total={}ms (dedup={}ms, lockWait={}ms)", elapsedMs(startNanos), dedupMs, lockWaitMs);
            }
        } catch (WhatsAppSendException ex) {
            // Sending itself is broken (token, number, network), so a "sorry" message would fail too.
            log.error("{} {} - conversation state was not advanced", ErrorCodes.SEND_FAILED, ex.getMessage());
        } catch (Exception ex) {
            log.error("{} Unexpected error while handling message - conversation state was not advanced",
                    ErrorCodes.FLOW_FAILED, ex);
            sendApologyQuietly(waNumber);
        } finally {
            MDC.remove(LogUtil.MDC_USER);
            MDC.remove(LogUtil.MDC_MESSAGE);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void sendApologyQuietly(String waNumber) {
        try {
            messageSender.sendText(waNumber, BotMessages.APOLOGY);
        } catch (Exception ex) {
            log.warn("{} Could not send the apology message either: {}", ErrorCodes.SEND_FAILED, ex.getMessage());
        }
    }

    private void processMessage(String waNumber, IncomingUserMessage message) {
        long loadStartNanos = System.nanoTime();
        ConversationSession session = sessionCacheService.getOrCreate(waNumber);
        long loadMs = elapsedMs(loadStartNanos);
        ConversationStep stepBefore = session.getCurrentStep();

        // route() includes the WhatsApp send calls (each logged separately as [TIMING] send).
        long routeStartNanos = System.nanoTime();
        route(session, waNumber, message);
        long routeMs = elapsedMs(routeStartNanos);

        long saveStartNanos = System.nanoTime();
        sessionCacheService.save(session);
        log.info("[TIMING] step={} sessionLoad={}ms route(incl. sends)={}ms sessionSave={}ms",
                stepBefore, loadMs, routeMs, elapsedMs(saveStartNanos));
        if (stepBefore != session.getCurrentStep()) {
            log.info("Conversation step {} -> {}", stepBefore, session.getCurrentStep());
        }
    }

    private void route(ConversationSession session, String waNumber, IncomingUserMessage message) {
        boolean newSession = session.getId() == null;

        String text = message.getText() == null ? null : message.getText().trim();
        String choiceId = message.getSelectedOptionId();
        ConversationStep step = session.getCurrentStep();

        // Typed text is never logged - it can contain a child's health details.
        log.debug("Incoming message - step={}, choiceId={}, textLength={}", step, choiceId, text == null ? 0 : text.length());

        Instant now = Instant.now();
        Instant lastActivity = session.getLastActivityAt();
        boolean timedOut = step != ConversationStep.MAIN_MENU && lastActivity != null
                && Duration.between(lastActivity, now).toMinutes() >= idleTimeoutMinutes;
        session.setLastActivityAt(now);
        session.setIdleReminderSent(false);

        if (timedOut) {
            // Normally SessionIdleJob has already expired the session; this covers the job being late or down.
            log.info("Session idle since {} - resetting to main menu", lastActivity);
            resetToMainMenu(session);
            sendMainMenuList(waNumber, settings.get(BotSettingKey.SESSION_EXPIRED_MESSAGE));
            return;
        }

        if (newSession) {
            sendWelcomeAndMainMenu(waNumber);
            return;
        }

        if (choiceId == null && text != null) {
            String keyword = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if (step == ConversationStep.FORM) {
                if (Constants.FORM_EXIT_KEYWORDS.contains(keyword)) {
                    cancelForm(session, waNumber);
                    return;
                }
            } else if (Constants.GREETING_KEYWORDS.contains(keyword)) {
                resetToMainMenu(session);
                sendWelcomeAndMainMenu(waNumber);
                return;
            }
        }

        // "Talk to Our Team" and "Back to Menu" buttons work from every
        // screen (including stale buttons from earlier messages).
        if (step != ConversationStep.FORM) {
            if (Constants.TALK_TO_TEAM_ACTION.equals(choiceId)) {
                sendContactInfo(session, waNumber);
                return;
            }
            if (Constants.BACK_TO_MENU.equals(choiceId)) {
                resetToMainMenu(session);
                sendMainMenuList(waNumber);
                return;
            }
        }

        if (step == ConversationStep.FORM) {
            handleForm(session, waNumber, text, choiceId);
        } else if (!handleBrowseChoice(session, waNumber, choiceId)) {
            // Free text or a tap that no longer exists (e.g. a removed service).
            if (step == ConversationStep.MAIN_MENU) {
                sendMainMenuList(waNumber, settings.get(BotSettingKey.FALLBACK_MESSAGE));
            } else {
                sendFallback(waNumber);
            }
        }
    }

    // ------------------------------------------------------------ main menu

    /**
     * Browsing taps (menu options, services) work from ANY screen outside
     * the form, so a parent tapping an older list higher up the chat gets what they picked instead of an
     * error. Only the form is strict, so a stray tap can never disturb the answers being typed.
     *
     * @return true if the tap was understood and answered
     */
    private boolean handleBrowseChoice(ConversationSession session, String waNumber, String choiceId) {
        if (choiceId == null) {
            return false;
        }
        switch (choiceId) {
            case Constants.MENU_SERVICES -> showServices(session, waNumber);
            case Constants.MENU_CENTRES -> {
                session.setCurrentStep(ConversationStep.CENTRES_INFO);
                sendCentresInfo(waNumber);
            }
            case Constants.MENU_BOOK_CONSULTATION -> startForm(session, waNumber);
            case Constants.MENU_TALK_TO_TEAM -> sendContactInfo(session, waNumber);
            default -> {
                return handleBrowseRow(session, waNumber, choiceId);
            }
        }
        return true;
    }

    /** A tap on a row of an old per-service list higher up the chat just shows the services text. */
    private boolean handleBrowseRow(ConversationSession session, String waNumber, String choiceId) {
        if (choiceId.startsWith(Constants.SERVICE_ROW_PREFIX)) {
            showServices(session, waNumber);
            return true;
        }
        return false;
    }

    private void sendWelcomeAndMainMenu(String waNumber) {
        sendMainMenuList(waNumber, settings.get(BotSettingKey.WELCOME_MESSAGE));
    }

    private void sendMainMenuList(String waNumber) {
        sendMainMenuList(waNumber, BotMessages.MENU_PROMPT);
    }

    private void sendMainMenuAfterCompletion(String waNumber) {
        sendMainMenuList(waNumber, BotMessages.MENU_PROMPT_AFTER);
    }

    /** Confirmation text and the main menu go out as ONE message: the text becomes the menu's body. */
    private void sendMainMenuAfterCompletion(String waNumber, String leadIn) {
        sendMainMenuList(waNumber, withLeadIn(leadIn, BotMessages.MENU_PROMPT_AFTER));
    }

    private static String withLeadIn(String leadIn, String body) {
        return leadIn == null ? body : leadIn + "\n\n" + body;
    }

    private void sendMainMenuList(String waNumber, String bodyText) {
        messageSender.sendList(waNumber, TextUtil.truncate(bodyText, Constants.MAX_BUTTON_BODY_LENGTH), BotMessages.LIST_BTN_MENU,
                List.of(new Section(BotMessages.SECTION_MENU, List.of(
                        new Row(Constants.MENU_SERVICES, BotMessages.MENU_SERVICES_TITLE, BotMessages.MENU_SERVICES_DESC),
                        new Row(Constants.MENU_CENTRES, BotMessages.MENU_CENTRES_TITLE, BotMessages.MENU_CENTRES_DESC),
                        new Row(Constants.MENU_BOOK_CONSULTATION, BotMessages.MENU_BOOK_TITLE, BotMessages.MENU_BOOK_DESC),
                        new Row(Constants.MENU_TALK_TO_TEAM, BotMessages.MENU_TALK_TITLE, BotMessages.MENU_TALK_DESC)
                ))));
    }

    // ------------------------------------------------------------- services

    /** All services as one numbered text message (no WhatsApp list-row limits), then Contact Us / Back. */
    private void showServices(ConversationSession session, String waNumber) {
        List<ServiceOffering> services = contentService.getActiveServices();
        if (services.isEmpty()) {
            sendUnavailable(waNumber);
            return;
        }
        session.setCurrentStep(ConversationStep.SERVICES_LIST);
        session.setTaggedServiceId(null);
        sendTextWithButtons(waNumber, BotMessages.servicesText(services.stream().map(ServiceOffering::getName).toList()),
                BotMessages.SERVICE_FOLLOW_UP, talkOrBackButtons());
    }

    // -------------------------------------------------------------- centres

    private void sendCentresInfo(String waNumber) {
        List<Centre> centres = contentService.getActiveCentres();
        if (centres.isEmpty()) {
            sendUnavailable(waNumber);
            return;
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Centre centre : centres) {
            String block = BotMessages.centreBlock(centre.getName(), centre.getAddress(), centre.getContactNumber(), centre.getMapsLink());
            if (current.length() > 0 && current.length() + block.length() + 2 > Constants.MAX_TEXT_LENGTH - 200) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(block);
        }
        chunks.add(current.toString());
        chunks.set(0, BotMessages.CENTRES_HEADER + chunks.get(0));

        for (int i = 0; i < chunks.size() - 1; i++) {
            messageSender.sendText(waNumber, chunks.get(i));
        }
        sendTextWithButtons(waNumber, chunks.get(chunks.size() - 1), BotMessages.CENTRES_FOLLOW_UP, talkOrBackButtons());
    }

    // ------------------------------------------------------------ lead form

    /** "Talk to Our Team" / "Contact Us": no form, the team's contact details from bot_settings as the body of the main menu. */
    private void sendContactInfo(ConversationSession session, String waNumber) {
        resetToMainMenu(session);
        sendMainMenuList(waNumber, settings.get(BotSettingKey.CONTACT_MESSAGE));
    }

    private void startForm(ConversationSession session, String waNumber) {
        startForm(session, waNumber, null);
    }

    /** The intro (and an optional lead-in such as an apology) is folded into the first question: one message. */
    private void startForm(ConversationSession session, String waNumber, String leadIn) {
        List<Centre> centres = contentService.getActiveCentres();
        if (centres.isEmpty()) {
            sendUnavailable(waNumber);
            return;
        }
        session.setCurrentStep(ConversationStep.FORM);
        session.setDraftDataJson(null);
        session.setTaggedCentreId(null);
        String intro = withLeadIn(leadIn, BotMessages.FORM_INTRO);
        if (centres.size() == 1) {
            session.setTaggedCentreId(centres.get(0).getId());
            session.setPendingLeadField(LeadFieldStep.PATIENT_NAME);
            promptField(session, waNumber, LeadFieldStep.PATIENT_NAME, intro);
        } else {
            session.setPendingLeadField(LeadFieldStep.CENTRE);
            promptField(session, waNumber, LeadFieldStep.CENTRE, intro);
        }
    }

    private void handleForm(ConversationSession session, String waNumber, String text, String choiceId) {
        LeadFieldStep field = session.getPendingLeadField();

        if (field == null || field == LeadFieldStep.NONE) {
            // Should not happen; recover instead of leaving the user stuck.
            startForm(session, waNumber);
            return;
        }
        if (field == LeadFieldStep.CENTRE) {
            handleCentreSelection(session, waNumber, choiceId);
            return;
        }
        if (field == LeadFieldStep.CONSENT) {
            handleConsent(session, waNumber, choiceId);
            return;
        }
        if (choiceId != null || text == null || text.isBlank()) {
            promptField(session, waNumber, field);
            return;
        }

        LeadDraft draft = readDraft(session);
        switch (field) {
            case PATIENT_NAME -> {
                Optional<String> name = LeadInputValidator.normalizeName(text);
                if (name.isEmpty()) {
                    retry(waNumber, BotMessages.RETRY_PATIENT_NAME);
                    return;
                }
                draft.setPatientName(name.get());
                advance(session, waNumber, draft, LeadFieldStep.PATIENT_NUMBER);
            }
            case PATIENT_NUMBER -> {
                Optional<String> phone = LeadInputValidator.normalizePhone(text);
                if (phone.isEmpty()) {
                    retry(waNumber, BotMessages.RETRY_PATIENT_NUMBER);
                    return;
                }
                draft.setPatientNumber(phone.get());
                advance(session, waNumber, draft, LeadFieldStep.DATE_OF_BIRTH);
            }
            case DATE_OF_BIRTH -> {
                Optional<LocalDate> dob = LeadInputValidator.parseDateOfBirth(text, LocalDate.now(IST));
                if (dob.isEmpty()) {
                    retry(waNumber, BotMessages.RETRY_DATE_OF_BIRTH);
                    return;
                }
                draft.setDateOfBirth(dob.get().toString());
                advance(session, waNumber, draft, LeadFieldStep.CONCERN);
            }
            case CONCERN -> {
                Optional<String> concern = LeadInputValidator.normalizeConcern(text);
                if (concern.isEmpty()) {
                    retry(waNumber, BotMessages.RETRY_CONCERN);
                    return;
                }
                draft.setConcern(concern.get());
                advance(session, waNumber, draft, LeadFieldStep.REFERRAL_DOCTOR);
            }
            case REFERRAL_DOCTOR -> {
                Optional<String> doctor = LeadInputValidator.normalizeReferralDoctor(text);
                if (doctor.isEmpty()) {
                    retry(waNumber, BotMessages.RETRY_REFERRAL_DOCTOR);
                    return;
                }
                draft.setReferralDoctor(doctor.get());
                advance(session, waNumber, draft, LeadFieldStep.CONSENT);
            }
            default -> promptField(session, waNumber, field);
        }
    }

    private void handleCentreSelection(ConversationSession session, String waNumber, String choiceId) {
        if (Constants.BACK_TO_MENU.equals(choiceId)) {
            cancelForm(session, waNumber);
            return;
        }
        if (choiceId != null && choiceId.startsWith(Constants.CENTRE_PAGE_PREFIX)) {
            Long page = parsePrefixedId(choiceId, Constants.CENTRE_PAGE_PREFIX);
            sendCentreSelection(waNumber, page == null ? 1 : page.intValue());
            return;
        }
        Long centreId = parsePrefixedId(choiceId, Constants.CENTRE_ROW_PREFIX);
        Optional<Centre> centre = centreId == null ? Optional.empty() : contentService.findActiveCentre(centreId);
        if (centre.isEmpty()) {
            promptField(session, waNumber, LeadFieldStep.CENTRE);
            return;
        }
        session.setTaggedCentreId(centre.get().getId());
        session.setPendingLeadField(LeadFieldStep.PATIENT_NAME);
        promptField(session, waNumber, LeadFieldStep.PATIENT_NAME);
    }

    private void handleConsent(ConversationSession session, String waNumber, String choiceId) {
        if (Constants.CONSENT_YES.equals(choiceId)) {
            submitLead(session, waNumber);
        } else if (Constants.CONSENT_NO.equals(choiceId)) {
            resetToMainMenu(session);
            sendMainMenuAfterCompletion(waNumber, BotMessages.CONSENT_DECLINED);
        } else {
            promptField(session, waNumber, LeadFieldStep.CONSENT);
        }
    }

    private void submitLead(ConversationSession session, String waNumber) {
        LeadDraft draft = readDraft(session);
        if (session.getTaggedCentreId() == null || draft.getPatientName() == null || draft.getPatientNumber() == null
                || draft.getDateOfBirth() == null || draft.getConcern() == null || draft.getReferralDoctor() == null) {
            log.warn("Lead form reached consent with missing data - restarting the form");
            startForm(session, waNumber, BotMessages.FORM_DATA_LOST);
            return;
        }

        LeadService.LeadResult result = leadService.createLead(NewLeadCommand.builder()
                .whatsappNumber(waNumber)
                .patientName(draft.getPatientName())
                .patientNumber(draft.getPatientNumber())
                .dateOfBirth(LocalDate.parse(draft.getDateOfBirth()))
                .concern(draft.getConcern())
                .referralDoctor(draft.getReferralDoctor())
                .centreId(session.getTaggedCentreId())
                .serviceId(session.getTaggedServiceId())
                .consentText(settings.get(BotSettingKey.CONSENT_TEXT))
                .build());

        StringBuilder reply = new StringBuilder(settings.get(
                result.duplicate() ? BotSettingKey.DUPLICATE_MESSAGE : BotSettingKey.THANK_YOU_MESSAGE));
        reply.append(BotMessages.reference(result.lead().getLeadCode()));

        resetToMainMenu(session);
        sendMainMenuAfterCompletion(waNumber, reply.toString());
    }

    private void advance(ConversationSession session, String waNumber, LeadDraft draft, LeadFieldStep next) {
        writeDraft(session, draft);
        session.setPendingLeadField(next);
        promptField(session, waNumber, next);
    }

    private void retry(String waNumber, String message) {
        messageSender.sendText(waNumber, message);
    }

    private void promptField(ConversationSession session, String waNumber, LeadFieldStep field) {
        promptField(session, waNumber, field, null);
    }

    /** @param leadIn optional text sent in the SAME message as the question (null for none) */
    private void promptField(ConversationSession session, String waNumber, LeadFieldStep field, String leadIn) {
        switch (field) {
            case CENTRE -> sendCentreSelection(waNumber, 1, leadIn);
            case PATIENT_NAME -> messageSender.sendText(waNumber, withLeadIn(leadIn, BotMessages.ASK_PATIENT_NAME));
            case PATIENT_NUMBER -> messageSender.sendText(waNumber, withLeadIn(leadIn, BotMessages.ASK_PATIENT_NUMBER));
            case DATE_OF_BIRTH -> messageSender.sendText(waNumber, withLeadIn(leadIn, BotMessages.ASK_DATE_OF_BIRTH));
            case CONCERN -> messageSender.sendText(waNumber, withLeadIn(leadIn, BotMessages.ASK_CONCERN));
            case REFERRAL_DOCTOR -> messageSender.sendText(waNumber, withLeadIn(leadIn, BotMessages.ASK_REFERRAL_DOCTOR));
            case CONSENT -> sendReviewAndConsent(session, waNumber);
            case NONE -> {
            }
        }
    }

    /**
     * A WhatsApp list holds at most 10 rows. When all centres plus "Cancel" fit, they are one page; otherwise
     * they are paged, leaving room on every page for "More centres", "Previous centres" and "Cancel".
     */
    private void sendCentreSelection(String waNumber, int requestedPage) {
        sendCentreSelection(waNumber, requestedPage, null);
    }

    private void sendCentreSelection(String waNumber, int requestedPage, String leadIn) {
        List<Centre> centres = contentService.getActiveCentres();
        boolean paged = centres.size() > Constants.MAX_LIST_ROWS - 1;
        int perPage = paged ? CENTRES_PER_PAGE : centres.size();
        int totalPages = paged ? (centres.size() + perPage - 1) / perPage : 1;
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        List<Centre> onThisPage = centres.subList((page - 1) * perPage, Math.min(centres.size(), page * perPage));

        List<Row> rows = new ArrayList<>(onThisPage.stream()
                .map(c -> new Row(Constants.CENTRE_ROW_PREFIX + c.getId(),
                        TextUtil.truncate(c.getName(), Constants.MAX_ROW_TITLE_LENGTH),
                        TextUtil.truncate(c.getAddress(), 72)))
                .toList());
        if (page < totalPages) {
            rows.add(new Row(Constants.CENTRE_PAGE_PREFIX + (page + 1), BotMessages.ROW_MORE_CENTRES, null));
        }
        if (page > 1) {
            rows.add(new Row(Constants.CENTRE_PAGE_PREFIX + (page - 1), BotMessages.ROW_PREVIOUS_CENTRES, null));
        }
        rows.add(new Row(Constants.BACK_TO_MENU, BotMessages.ROW_CANCEL, null));
        messageSender.sendList(waNumber,
                TextUtil.truncate(withLeadIn(leadIn, BotMessages.askCentre(page, totalPages)), Constants.MAX_BUTTON_BODY_LENGTH),
                BotMessages.LIST_BTN_CENTRE,
                List.of(new Section(BotMessages.SECTION_CENTRES, rows)));
    }

    /** Shows everything the parent entered plus the consent text, so mistakes can be caught before anything is saved. */
    private void sendReviewAndConsent(ConversationSession session, String waNumber) {
        LeadDraft draft = readDraft(session);
        String centreName = session.getTaggedCentreId() == null ? "-"
                : contentService.findActiveCentre(session.getTaggedCentreId()).map(Centre::getName).orElse("-");
        String dob = draft.getDateOfBirth() == null ? "-" : LocalDate.parse(draft.getDateOfBirth()).format(DISPLAY_DATE);

        String card = BotMessages.reviewAndConsent(
                orDash(draft.getPatientName()), orDash(draft.getPatientNumber()), dob, centreName,
                orDash(draft.getConcern()), orDash(draft.getReferralDoctor()), settings.get(BotSettingKey.CONSENT_TEXT));
        sendTextWithButtons(waNumber, card, BotMessages.CONSENT_QUESTION, List.of(
                new ButtonOption(Constants.CONSENT_YES, BotMessages.BTN_AGREE),
                new ButtonOption(Constants.CONSENT_NO, BotMessages.BTN_DECLINE)
        ));
    }

    private void cancelForm(ConversationSession session, String waNumber) {
        resetToMainMenu(session);
        sendMainMenuAfterCompletion(waNumber, BotMessages.FORM_CANCELLED);
    }

    // ------------------------------------------------------- idle handling
    // Called by SessionIdleJob, not by a user message. Each call takes the same
    // per-number lock as normal messages and re-checks the session, so a parent
    // who replies at the same moment is never reminded or expired by mistake.

    /** Sends a one-time "are you still there?" with the current step, once a conversation has been quiet for the reminder time. */
    public void remindIfIdle(String waNumber) {
        runLocked(waNumber, () -> {
            ConversationSession session = sessionCacheService.getOrCreate(waNumber);
            if (!isMidConversation(session) || session.isIdleReminderSent() || !idleFor(session, idleReminderMinutes)) {
                return;
            }
            // Mark first: if the send fails we must not retry every 30 seconds.
            session.setIdleReminderSent(true);
            sessionCacheService.save(session);

            log.info("Sending idle reminder - step={}", session.getCurrentStep());
            messageSender.sendText(waNumber, settings.get(BotSettingKey.IDLE_REMINDER_MESSAGE)
                    + BotMessages.youWere(describeStep(session)));
            resendCurrentStep(session, waNumber);
            sessionCacheService.save(session);
        });
    }

    /** Ends a conversation that has been quiet for the full session timeout and tells the parent to start fresh. */
    public void expireIfIdle(String waNumber) {
        runLocked(waNumber, () -> {
            ConversationSession session = sessionCacheService.getOrCreate(waNumber);
            if (!isMidConversation(session) || !idleFor(session, idleTimeoutMinutes)) {
                return;
            }
            // Reset first: even if the notice cannot be sent, the next message starts fresh.
            log.info("Session expired after {} idle minutes - step was {}", idleTimeoutMinutes, session.getCurrentStep());
            resetToMainMenu(session);
            sessionCacheService.save(session);
            sendMainMenuList(waNumber, settings.get(BotSettingKey.SESSION_EXPIRED_MESSAGE));
        });
    }

    private void runLocked(String waNumber, Runnable action) {
        MDC.put(LogUtil.MDC_USER, LogUtil.maskPhone(waNumber));
        try {
            Object lock = perNumberLocks.computeIfAbsent(waNumber, k -> new Object());
            synchronized (lock) {
                action.run();
            }
        } catch (WhatsAppSendException ex) {
            log.error("{} {}", ErrorCodes.SEND_FAILED, ex.getMessage());
        } catch (Exception ex) {
            log.error("{} Unexpected error in the idle-session check", ErrorCodes.FLOW_FAILED, ex);
        } finally {
            MDC.remove(LogUtil.MDC_USER);
        }
    }

    private boolean isMidConversation(ConversationSession session) {
        return session.getCurrentStep() != ConversationStep.MAIN_MENU;
    }

    private boolean idleFor(ConversationSession session, long minutes) {
        Instant last = session.getLastActivityAt();
        return last != null && Duration.between(last, Instant.now()).toMinutes() >= minutes;
    }

    /** Re-sends whatever the parent was last shown, without changing where they are. */
    private void resendCurrentStep(ConversationSession session, String waNumber) {
        switch (session.getCurrentStep()) {
            case SERVICES_LIST, SERVICE_DETAIL -> showServices(session, waNumber);
            case CENTRES_INFO -> sendCentresInfo(waNumber);
            case FORM -> {
                LeadFieldStep field = session.getPendingLeadField();
                if (field == null || field == LeadFieldStep.NONE) {
                    startForm(session, waNumber);
                } else {
                    promptField(session, waNumber, field);
                }
            }
            case MAIN_MENU -> sendMainMenuList(waNumber);
        }
    }

    private String describeStep(ConversationSession session) {
        return switch (session.getCurrentStep()) {
            case SERVICES_LIST, SERVICE_DETAIL -> "browsing our services";
            case CENTRES_INFO -> "looking at our centres";
            case FORM -> "filling in the request form (" + describeFormField(session.getPendingLeadField()) + ")";
            case MAIN_MENU -> "on the main menu";
        };
    }

    private String describeFormField(LeadFieldStep field) {
        if (field == null) {
            return "the first question";
        }
        return switch (field) {
            case CENTRE -> "choosing a centre";
            case PATIENT_NAME -> "the patient's name";
            case PATIENT_NUMBER -> "the patient's contact number";
            case DATE_OF_BIRTH -> "the patient's date of birth";
            case CONCERN -> "your concern";
            case REFERRAL_DOCTOR -> "the referring doctor";
            case CONSENT -> "the final review and consent";
            case NONE -> "the first question";
        };
    }

    // -------------------------------------------------------------- helpers

    private void resetToMainMenu(ConversationSession session) {
        session.setCurrentStep(ConversationStep.MAIN_MENU);
        session.setPendingLeadField(LeadFieldStep.NONE);
        session.setDraftDataJson(null);
        session.setTaggedCentreId(null);
        session.setTaggedServiceId(null);
    }

    private List<ButtonOption> talkOrBackButtons() {
        return List.of(
                new ButtonOption(Constants.TALK_TO_TEAM_ACTION, BotMessages.BTN_TALK_TO_TEAM),
                new ButtonOption(Constants.BACK_TO_MENU, BotMessages.BTN_BACK_TO_MENU));
    }

    /** Free text or an unrecognised tap: say so and offer a hand-off to the team. */
    private void sendFallback(String waNumber) {
        messageSender.sendButtons(waNumber, settings.get(BotSettingKey.FALLBACK_MESSAGE), talkOrBackButtons());
    }

    private void sendUnavailable(String waNumber) {
        messageSender.sendButtons(waNumber, BotMessages.UNAVAILABLE, talkOrBackButtons());
    }

    /**
     * Content and its action buttons arrive as one message when the content
     * fits WhatsApp's button-body limit; otherwise the content goes first
     * and the buttons follow with a short prompt.
     */
    private void sendTextWithButtons(String waNumber, String text, String followUpPrompt, List<ButtonOption> buttons) {
        if (text.length() <= Constants.MAX_BUTTON_BODY_LENGTH) {
            messageSender.sendButtons(waNumber, text, buttons);
        } else {
            messageSender.sendText(waNumber, TextUtil.truncate(text, Constants.MAX_TEXT_LENGTH));
            messageSender.sendButtons(waNumber, followUpPrompt, buttons);
        }
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private Long parsePrefixedId(String choiceId, String prefix) {
        if (choiceId == null || !choiceId.startsWith(prefix)) {
            return null;
        }
        try {
            return Long.parseLong(choiceId.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LeadDraft readDraft(ConversationSession session) {
        if (session.getDraftDataJson() == null) {
            return LeadDraft.builder().build();
        }
        try {
            return objectMapper.readValue(session.getDraftDataJson(), LeadDraft.class);
        } catch (Exception ex) {
            // The form restarts itself at the consent step when data is missing.
            log.error("{} Could not read the saved form draft - continuing with an empty one", ErrorCodes.DRAFT_CORRUPT, ex);
            return LeadDraft.builder().build();
        }
    }

    @SneakyThrows
    private void writeDraft(ConversationSession session, LeadDraft draft) {
        session.setDraftDataJson(objectMapper.writeValueAsString(draft));
    }
}
