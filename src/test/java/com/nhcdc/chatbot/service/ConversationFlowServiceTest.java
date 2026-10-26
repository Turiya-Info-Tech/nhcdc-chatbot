package com.nhcdc.chatbot.service;

import com.nhcdc.chatbot.dto.conversation.IncomingUserMessage;
import com.nhcdc.chatbot.dto.lead.NewLeadCommand;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage.Row;
import com.nhcdc.chatbot.dto.whatsapp.outgoing.OutgoingInteractiveListMessage.Section;
import com.nhcdc.chatbot.entity.Centre;
import com.nhcdc.chatbot.entity.ConversationSession;
import com.nhcdc.chatbot.entity.ServiceOffering;
import com.nhcdc.chatbot.entity.Lead;
import com.nhcdc.chatbot.enums.BotSettingKey;
import com.nhcdc.chatbot.enums.ConversationStep;
import com.nhcdc.chatbot.enums.LeadFieldStep;
import com.nhcdc.chatbot.exception.WhatsAppSendException;
import com.nhcdc.chatbot.service.whatsapp.WhatsAppMessageSender;
import com.nhcdc.chatbot.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationFlowServiceTest {

    private static final String WA = "919000000001";

    @Mock ConversationSessionCacheService sessionCache;
    @Mock ContentService contentService;
    @Mock BotSettingsService settings;
    @Mock LeadService leadService;
    @Mock WhatsAppMessageSender sender;
    @Mock MessageDeduplicationService deduplication;

    ConversationFlowService flow;
    ConversationSession session;
    int messageCounter;

    @BeforeEach
    void setUp() {
        flow = new ConversationFlowService(sessionCache, contentService, settings, leadService,
                sender, deduplication, JsonMapper.builder().build());
        ReflectionTestUtils.setField(flow, "idleTimeoutMinutes", 30L);
        ReflectionTestUtils.setField(flow, "idleReminderMinutes", 5L);

        session = ConversationSession.builder().id(1L).whatsappNumber(WA).lastActivityAt(Instant.now()).build();
        when(sessionCache.getOrCreate(WA)).thenReturn(session);
        when(deduplication.markIfNew(any())).thenReturn(true);
        when(settings.get(any())).thenAnswer(inv -> ((BotSettingKey) inv.getArgument(0)).getDefaultValue());

        Centre andheri = Centre.builder().id(1L).name("Andheri").address("Andheri West").build();
        Centre thane = Centre.builder().id(2L).name("Thane").address("Thane West").build();
        when(contentService.getActiveCentres()).thenReturn(List.of(andheri, thane));
        when(contentService.findActiveCentre(1L)).thenReturn(Optional.of(andheri));
    }

    private void tap(String id) {
        flow.handleIncomingMessage(IncomingUserMessage.builder()
                .messageId("m" + (++messageCounter)).from(WA).selectedOptionId(id).build());
    }

    private void type(String text) {
        flow.handleIncomingMessage(IncomingUserMessage.builder()
                .messageId("m" + (++messageCounter)).from(WA).text(text).build());
    }

    @Test
    void completesLeadFormInPdfOrderAndSavesConsent() {
        when(leadService.createLead(any())).thenReturn(
                new LeadService.LeadResult(Lead.builder().leadCode("NHCDC-00001").build(), false));

        tap(Constants.MENU_BOOK_CONSULTATION);
        assertEquals(ConversationStep.FORM, session.getCurrentStep());
        assertEquals(LeadFieldStep.CENTRE, session.getPendingLeadField());

        tap(Constants.CENTRE_ROW_PREFIX + 1);
        assertEquals(LeadFieldStep.PATIENT_NAME, session.getPendingLeadField());

        type("Aarav Sharma");
        assertEquals(LeadFieldStep.PATIENT_NUMBER, session.getPendingLeadField());
        type("98765 43210");
        assertEquals(LeadFieldStep.DATE_OF_BIRTH, session.getPendingLeadField());
        type("14-03-2021");
        assertEquals(LeadFieldStep.CONCERN, session.getPendingLeadField());
        type("Not speaking yet");
        assertEquals(LeadFieldStep.REFERRAL_DOCTOR, session.getPendingLeadField());
        type("none");
        assertEquals(LeadFieldStep.CONSENT, session.getPendingLeadField());
        verify(sender).sendButtons(eq(WA), contains("Please review your details"), any());
        verify(sender).sendButtons(eq(WA), contains("Aarav Sharma"), any());
        verify(sender).sendButtons(eq(WA), contains("14-03-2021"), any());

        verify(leadService, never()).createLead(any());
        tap(Constants.CONSENT_YES);

        ArgumentCaptor<NewLeadCommand> captor = ArgumentCaptor.forClass(NewLeadCommand.class);
        verify(leadService).createLead(captor.capture());
        NewLeadCommand cmd = captor.getValue();
        assertEquals(WA, cmd.getWhatsappNumber());
        assertEquals("Aarav Sharma", cmd.getPatientName());
        assertEquals("9876543210", cmd.getPatientNumber());
        assertEquals(LocalDate.of(2021, 3, 14), cmd.getDateOfBirth());
        assertEquals("Not speaking yet", cmd.getConcern());
        assertEquals("None", cmd.getReferralDoctor());
        assertEquals(1L, cmd.getCentreId());

        verify(sender).sendList(eq(WA), contains("NHCDC-00001"), any(), any());
        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        assertNull(session.getDraftDataJson());
    }

    private List<Centre> elevenCentres() {
        return java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(i -> Centre.builder().id((long) i).name("Centre " + i).address("Address " + i)
                        .contactNumber("+91 90000 0000" + i).build())
                .toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void centrePickerIsPagedBecauseAWhatsAppListHoldsTenRows() {
        List<Centre> centres = elevenCentres();
        when(contentService.getActiveCentres()).thenReturn(centres);
        when(contentService.findActiveCentre(10L)).thenReturn(Optional.of(centres.get(9)));

        tap(Constants.MENU_BOOK_CONSULTATION);

        ArgumentCaptor<List<Section>> firstPage = ArgumentCaptor.forClass(List.class);
        verify(sender).sendList(eq(WA), contains("Page 1 of 2"), eq("Select Centre"), firstPage.capture());
        List<Row> page1 = firstPage.getValue().get(0).getRows();
        assertEquals(9, page1.size(), "7 centres + More + Cancel");
        assertEquals(Constants.CENTRE_PAGE_PREFIX + 2, page1.get(7).getId());

        tap(Constants.CENTRE_PAGE_PREFIX + 2);

        ArgumentCaptor<List<Section>> secondPage = ArgumentCaptor.forClass(List.class);
        verify(sender).sendList(eq(WA), contains("Page 2 of 2"), eq("Select Centre"), secondPage.capture());
        List<Row> page2 = secondPage.getValue().get(0).getRows();
        assertEquals(6, page2.size(), "4 centres + Previous + Cancel");
        assertEquals(Constants.CENTRE_ROW_PREFIX + 8, page2.get(0).getId());
        assertEquals(Constants.CENTRE_PAGE_PREFIX + 1, page2.get(4).getId());
        assertEquals(LeadFieldStep.CENTRE, session.getPendingLeadField(), "paging must not leave the centre question");

        tap(Constants.CENTRE_ROW_PREFIX + 10);

        assertEquals(10L, session.getTaggedCentreId());
        assertEquals(LeadFieldStep.PATIENT_NAME, session.getPendingLeadField());
    }

    @Test
    void findACentreListsEveryCentre() {
        when(contentService.getActiveCentres()).thenReturn(elevenCentres());

        tap(Constants.MENU_CENTRES);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        // Short content travels with the buttons in one message; long content is sent as text first.
        verify(sender, org.mockito.Mockito.atLeast(0)).sendText(eq(WA), text.capture());
        ArgumentCaptor<String> buttonBody = ArgumentCaptor.forClass(String.class);
        verify(sender, org.mockito.Mockito.times(1)).sendButtons(eq(WA), buttonBody.capture(), any());
        String everythingSent = String.join("\n", text.getAllValues()) + String.join("\n", buttonBody.getAllValues());
        for (int i = 1; i <= 11; i++) {
            assertTrue(everythingSent.contains("Centre " + i + "*"), "centre " + i + " missing");
        }
        assertTrue(everythingSent.contains("Our Centres"));
    }

    @Test
    void declinedConsentSavesNothing() {
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("Aarav");
        type("9876543210");
        type("14-03-2021");
        type("Speech delay");
        type("Dr. Mehta");
        tap(Constants.CONSENT_NO);

        verify(leadService, never()).createLead(any());
        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
    }

    @Test
    void invalidAnswersAreRejectedAndQuestionStaysTheSame() {
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("Aarav");
        type("123");
        assertEquals(LeadFieldStep.PATIENT_NUMBER, session.getPendingLeadField());
        type("9876543210");
        type("31-02-2021");
        assertEquals(LeadFieldStep.DATE_OF_BIRTH, session.getPendingLeadField());
    }

    @Test
    void cancelKeywordAbandonsTheForm() {
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("cancel");

        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        verify(leadService, never()).createLead(any());
    }

    @Test
    void helpInsideFormIsTreatedAsAnAnswerNotAMenuReset() {
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("Aarav");
        type("9876543210");
        type("14-03-2021");
        type("help");

        assertEquals(ConversationStep.FORM, session.getCurrentStep());
        assertEquals(LeadFieldStep.REFERRAL_DOCTOR, session.getPendingLeadField());
    }

    @Test
    void duplicateLeadShowsDuplicateMessage() {
        when(leadService.createLead(any())).thenReturn(
                new LeadService.LeadResult(Lead.builder().leadCode("NHCDC-00007").build(), true));

        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("Aarav");
        type("9876543210");
        type("14-03-2021");
        type("Speech delay");
        type("none");
        tap(Constants.CONSENT_YES);

        verify(sender).sendList(eq(WA), contains("already have your recent request"), any(), any());
    }

    @Test
    void contactUsFromAServiceSendsContactDetailsWithoutStartingTheForm() {
        when(contentService.findActiveService(5L)).thenReturn(Optional.of(
                com.nhcdc.chatbot.entity.ServiceOffering.builder().id(5L).name("Speech Therapy").build()));
        when(contentService.getActiveServices()).thenReturn(List.of(
                com.nhcdc.chatbot.entity.ServiceOffering.builder().id(5L).name("Speech Therapy").build()));

        tap(Constants.MENU_SERVICES);
        tap(Constants.SERVICE_ROW_PREFIX + 5);

        tap(Constants.TALK_TO_TEAM_ACTION);
        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        verify(sender).sendList(eq(WA), contains("Contact Our Team"), eq("Main Menu"), any());
    }

    @Test
    void talkToTeamMenuRowSendsContactDetailsWithoutStartingTheForm() {
        tap(Constants.MENU_TALK_TO_TEAM);

        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        verify(sender).sendList(eq(WA), contains("Contact Our Team"), eq("Main Menu"), any());
        verify(sender, never()).sendText(eq(WA), contains("book your consultation"));
    }

    @Test
    void freeTextWhileBrowsingOffersTalkToTeam() {
        when(contentService.getActiveServices()).thenReturn(List.of(service(1L, "Speech Therapy")));

        tap(Constants.MENU_SERVICES);
        assertEquals(ConversationStep.SERVICES_LIST, session.getCurrentStep());

        type("my child is not talking, what should I do?");
        verify(sender).sendButtons(eq(WA), contains("didn't quite get that"), any());
        verify(leadService, never()).createLead(any());
    }

    private ServiceOffering service(long id, String name) {
        return ServiceOffering.builder().id(id).name(name).build();
    }

    @Test
    void servicesAreSentAsOneNumberedTextWithTalkAndBackButtons() {
        when(contentService.getActiveServices()).thenReturn(List.of(
                service(1L, "Delayed motor development"), service(2L, "Cerebral palsy (CP)")));

        tap(Constants.MENU_SERVICES);

        assertEquals(ConversationStep.SERVICES_LIST, session.getCurrentStep());
        verify(sender).sendButtons(eq(WA), contains("1. Delayed motor development\n2. Cerebral palsy (CP)"), any());
        verify(sender, never()).sendList(any(), any(), any(), any());
    }

    @Test
    void allTwelveServicesAreShownWithNoneDropped() {
        List<ServiceOffering> twelve = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            twelve.add(service(i, "Service " + i));
        }
        when(contentService.getActiveServices()).thenReturn(twelve);

        tap(Constants.MENU_SERVICES);

        verify(sender).sendButtons(eq(WA), contains("12. Service 12"), any());
    }

    @Test
    void oldMenuTapsWorkFromAnyBrowsingScreen() {
        when(contentService.getActiveServices()).thenReturn(List.of(service(1L, "Speech Therapy")));

        tap(Constants.MENU_SERVICES);
        tap(Constants.MENU_CENTRES);                       // old main menu, while on the services screen
        assertEquals(ConversationStep.CENTRES_INFO, session.getCurrentStep());

        tap(Constants.MENU_SERVICES);                      // old main menu, while on the centres screen
        assertEquals(ConversationStep.SERVICES_LIST, session.getCurrentStep());
    }

    @Test
    void tapOnAnOldServiceRowJustShowsTheServicesText() {
        when(contentService.getActiveServices()).thenReturn(List.of(service(1L, "Speech Therapy")));

        tap(Constants.SERVICE_ROW_PREFIX + 99);

        assertEquals(ConversationStep.SERVICES_LIST, session.getCurrentStep());
        verify(sender).sendButtons(eq(WA), contains("1. Speech Therapy"), any());
    }

    @Test
    void formStaysStrictAgainstStrayBrowsingTaps() {
        ServiceOffering speech = service(1L, "Speech Therapy");
        when(contentService.findActiveService(1L)).thenReturn(Optional.of(speech));
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("Aarav Sharma");
        assertEquals(LeadFieldStep.PATIENT_NUMBER, session.getPendingLeadField());

        tap(Constants.SERVICE_ROW_PREFIX + 1);             // stale tap from an old list
        tap(Constants.MENU_SERVICES);

        assertEquals(ConversationStep.FORM, session.getCurrentStep());
        assertEquals(LeadFieldStep.PATIENT_NUMBER, session.getPendingLeadField());
        assertTrue(session.getDraftDataJson().contains("Aarav Sharma"), "typed answers must survive");
    }

    @Test
    void idleSessionResetsToMainMenu() {
        session.setCurrentStep(ConversationStep.FORM);
        session.setPendingLeadField(LeadFieldStep.CONCERN);
        session.setLastActivityAt(Instant.now().minusSeconds(31 * 60));

        type("Speech delay");

        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        assertEquals(LeadFieldStep.NONE, session.getPendingLeadField());
        verify(leadService, never()).createLead(any());
    }

    @Test
    void redeliveredMessageIsIgnored() {
        when(deduplication.markIfNew("dup")).thenReturn(false);

        flow.handleIncomingMessage(IncomingUserMessage.builder().messageId("dup").from(WA).text("hi").build());

        verify(sessionCache, never()).getOrCreate(anyString());
    }

    @Test
    void firstEverMessageGetsWelcomeMenu() {
        ConversationSession fresh = ConversationSession.builder().whatsappNumber(WA).build();
        when(sessionCache.getOrCreate(WA)).thenReturn(fresh);

        type("I need help for my child");

        verify(sender).sendList(eq(WA), contains("Welcome"), eq("Main Menu"), any());
    }

    @Test
    void failedSendIsContainedAndStateIsNotSaved() {
        when(contentService.getActiveServices()).thenReturn(List.of(service(1L, "Speech Therapy")));
        doThrow(new WhatsAppSendException("Meta rejected it", 131030, 400, null))
                .when(sender).sendButtons(eq(WA), anyString(), any());

        assertDoesNotThrow(() -> tap(Constants.MENU_SERVICES));

        verify(sessionCache, never()).save(any());
        // sending is what's broken, so no second "sorry" message is attempted
        verify(sender, never()).sendText(eq(WA), contains("Sorry, something went wrong"));
    }

    @Test
    void unexpectedErrorIsContainedAndParentGetsAnApology() {
        when(contentService.getActiveCentres()).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> tap(Constants.MENU_BOOK_CONSULTATION));

        verify(sender).sendText(eq(WA), contains("Sorry, something went wrong"));
        verify(sessionCache, never()).save(any());
    }

    @Test
    void failureWhileSavingLeadIsContainedAndFormStaysAtConsent() {
        when(leadService.createLead(any())).thenThrow(new IllegalStateException("db down"));
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        type("Aarav");
        type("9876543210");
        type("14-03-2021");
        type("Speech delay");
        type("none");

        assertDoesNotThrow(() -> tap(Constants.CONSENT_YES));

        verify(sender).sendText(eq(WA), contains("Sorry, something went wrong"));
        assertEquals(ConversationStep.FORM, session.getCurrentStep());
        assertEquals(LeadFieldStep.CONSENT, session.getPendingLeadField());
    }

    private void quietFor(int minutes, ConversationStep step, LeadFieldStep field) {
        session.setCurrentStep(step);
        session.setPendingLeadField(field);
        session.setLastActivityAt(Instant.now().minusSeconds(minutes * 60L));
    }

    @Test
    void reminderAfterFiveQuietMinutesNamesTheStepAndResendsTheQuestion() {
        quietFor(6, ConversationStep.FORM, LeadFieldStep.DATE_OF_BIRTH);

        flow.remindIfIdle(WA);

        verify(sender).sendText(eq(WA), contains("haven't heard from you"));
        verify(sender).sendText(eq(WA), contains("filling in the request form (the patient's date of birth)"));
        verify(sender).sendText(eq(WA), contains("Please type it as DD-MM-YYYY"));
        assertTrue(session.isIdleReminderSent());
        assertEquals(ConversationStep.FORM, session.getCurrentStep());
        assertEquals(LeadFieldStep.DATE_OF_BIRTH, session.getPendingLeadField());
    }

    @Test
    void reminderResendsTheListWhenBrowsingServices() {
        when(contentService.getActiveServices()).thenReturn(List.of(service(1L, "Speech Therapy")));
        quietFor(6, ConversationStep.SERVICES_LIST, LeadFieldStep.NONE);

        flow.remindIfIdle(WA);

        verify(sender).sendText(eq(WA), contains("browsing our services"));
        verify(sender).sendButtons(eq(WA), contains("1. Speech Therapy"), any());
    }

    @Test
    void noReminderWhenRecentAlreadySentOrOnMainMenu() {
        quietFor(2, ConversationStep.FORM, LeadFieldStep.PATIENT_NAME);
        flow.remindIfIdle(WA);

        quietFor(10, ConversationStep.FORM, LeadFieldStep.PATIENT_NAME);
        session.setIdleReminderSent(true);
        flow.remindIfIdle(WA);

        session.setIdleReminderSent(false);
        quietFor(10, ConversationStep.MAIN_MENU, LeadFieldStep.NONE);
        flow.remindIfIdle(WA);

        verify(sender, never()).sendText(eq(WA), anyString());
    }

    @Test
    void reminderSendFailureIsContainedAndNotRetried() {
        quietFor(6, ConversationStep.FORM, LeadFieldStep.PATIENT_NAME);
        doThrow(new WhatsAppSendException("Meta down", null, 502, null)).when(sender).sendText(eq(WA), anyString());

        assertDoesNotThrow(() -> flow.remindIfIdle(WA));

        assertTrue(session.isIdleReminderSent(), "must be marked so the job does not retry every 30 seconds");
    }

    @Test
    void sessionExpiresAfterTimeoutAndTellsTheParentToStartFresh() {
        quietFor(31, ConversationStep.FORM, LeadFieldStep.CONCERN);
        session.setDraftDataJson("{\"patient_name\":\"Aarav\"}");

        flow.expireIfIdle(WA);

        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        assertEquals(LeadFieldStep.NONE, session.getPendingLeadField());
        assertNull(session.getDraftDataJson());
        verify(sender).sendList(eq(WA), contains("session has ended"), eq("Main Menu"), any());
        verify(sessionCache).save(session);
    }

    @Test
    void sessionIsNotExpiredBeforeTheTimeout() {
        quietFor(10, ConversationStep.FORM, LeadFieldStep.CONCERN);

        flow.expireIfIdle(WA);

        assertEquals(ConversationStep.FORM, session.getCurrentStep());
        verify(sender, never()).sendList(eq(WA), anyString(), anyString(), any());
    }

    @Test
    void textTypedAfterExpiryIsNotTakenAsAFormAnswer() {
        quietFor(31, ConversationStep.FORM, LeadFieldStep.PATIENT_NAME);
        flow.expireIfIdle(WA);

        type("Aarav Sharma");

        verify(leadService, never()).createLead(any());
        assertEquals(ConversationStep.MAIN_MENU, session.getCurrentStep());
        verify(sender).sendList(eq(WA), contains("didn't quite get that"), eq("Main Menu"), any());
    }

    @Test
    void replyingAfterTheReminderClearsItSoALaterQuietPeriodGetsANewOne() {
        quietFor(6, ConversationStep.FORM, LeadFieldStep.PATIENT_NAME);
        session.setIdleReminderSent(true);

        type("Aarav Sharma");

        assertFalse(session.isIdleReminderSent());
        assertEquals(LeadFieldStep.PATIENT_NUMBER, session.getPendingLeadField());
    }

    @Test
    void corruptDraftRestartsTheFormInsteadOfSavingBadData() {
        tap(Constants.MENU_BOOK_CONSULTATION);
        tap(Constants.CENTRE_ROW_PREFIX + 1);
        session.setPendingLeadField(LeadFieldStep.CONSENT);
        session.setDraftDataJson("{definitely not json");

        tap(Constants.CONSENT_YES);

        verify(leadService, never()).createLead(any());
        verify(sender).sendList(eq(WA), contains("lost some of your details"), any(), any());
        assertEquals(LeadFieldStep.CENTRE, session.getPendingLeadField());
    }
}
