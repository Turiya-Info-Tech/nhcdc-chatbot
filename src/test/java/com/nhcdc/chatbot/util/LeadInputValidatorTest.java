package com.nhcdc.chatbot.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadInputValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Test
    void nameIsTrimmedAndMustContainALetter() {
        assertEquals("Aarav Sharma", LeadInputValidator.normalizeName("  Aarav   Sharma ").orElseThrow());
        assertTrue(LeadInputValidator.normalizeName("12345").isEmpty());
        assertTrue(LeadInputValidator.normalizeName("A").isEmpty());
        assertTrue(LeadInputValidator.normalizeName("   ").isEmpty());
    }

    @Test
    void phoneAcceptsCommonIndianFormats() {
        assertEquals("9876543210", LeadInputValidator.normalizePhone("9876543210").orElseThrow());
        assertEquals("9876543210", LeadInputValidator.normalizePhone("+91 98765 43210").orElseThrow());
        assertEquals("9876543210", LeadInputValidator.normalizePhone("919876543210").orElseThrow());
        assertEquals("9876543210", LeadInputValidator.normalizePhone("09876543210").orElseThrow());
    }

    @Test
    void phoneRejectsInvalidNumbers() {
        assertTrue(LeadInputValidator.normalizePhone("12345").isEmpty());
        assertTrue(LeadInputValidator.normalizePhone("5876543210").isEmpty());
        assertTrue(LeadInputValidator.normalizePhone("98765abcde").isEmpty());
        assertTrue(LeadInputValidator.normalizePhone(null).isEmpty());
    }

    @Test
    void dobParsesDdMmYyyyWithVariousSeparators() {
        assertEquals(LocalDate.of(2021, 3, 14), LeadInputValidator.parseDateOfBirth("14-03-2021", TODAY).orElseThrow());
        assertEquals(LocalDate.of(2021, 3, 4), LeadInputValidator.parseDateOfBirth("4/3/2021", TODAY).orElseThrow());
        assertEquals(LocalDate.of(2021, 3, 14), LeadInputValidator.parseDateOfBirth("14.03.2021", TODAY).orElseThrow());
    }

    @Test
    void dobRejectsImpossibleFutureAndAncientDates() {
        assertTrue(LeadInputValidator.parseDateOfBirth("31-02-2021", TODAY).isEmpty());
        assertTrue(LeadInputValidator.parseDateOfBirth("03-10-2026", TODAY).isEmpty());
        assertTrue(LeadInputValidator.parseDateOfBirth("01-01-1900", TODAY).isEmpty());
        assertTrue(LeadInputValidator.parseDateOfBirth("three years old", TODAY).isEmpty());
        assertTrue(LeadInputValidator.parseDateOfBirth("2021-03-14", TODAY).isEmpty());
        assertTrue(LeadInputValidator.parseDateOfBirth(null, TODAY).isEmpty());
    }

    @Test
    void concernIsRequired() {
        assertEquals("Speech delay", LeadInputValidator.normalizeConcern(" Speech delay ").orElseThrow());
        assertTrue(LeadInputValidator.normalizeConcern("").isEmpty());
        assertTrue(LeadInputValidator.normalizeConcern("x".repeat(1001)).isEmpty());
    }

    @Test
    void referralDoctorNormalizesNoneAnswers() {
        assertEquals("None", LeadInputValidator.normalizeReferralDoctor("none").orElseThrow());
        assertEquals("None", LeadInputValidator.normalizeReferralDoctor("NA").orElseThrow());
        assertEquals("Dr. Mehta", LeadInputValidator.normalizeReferralDoctor(" Dr. Mehta ").orElseThrow());
        assertTrue(LeadInputValidator.normalizeReferralDoctor("  ").isEmpty());
    }
}
