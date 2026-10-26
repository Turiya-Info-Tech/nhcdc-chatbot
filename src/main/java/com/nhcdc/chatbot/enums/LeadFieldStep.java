package com.nhcdc.chatbot.enums;

/**
 * Which single field of the lead form is currently being asked for,
 * one WhatsApp message at a time. Order matches the NHCDC checklist.
 */
public enum LeadFieldStep {
    CENTRE,
    PATIENT_NAME,
    PATIENT_NUMBER,
    DATE_OF_BIRTH,
    CONCERN,
    REFERRAL_DOCTOR,
    CONSENT,
    NONE
}
