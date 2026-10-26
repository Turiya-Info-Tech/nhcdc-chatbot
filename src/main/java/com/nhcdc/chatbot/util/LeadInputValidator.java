package com.nhcdc.chatbot.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Validation/normalization for the free-text answers in the lead form.
 * Each method returns the cleaned value, or empty when the input should
 * be rejected and the question asked again.
 */
public final class LeadInputValidator {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_CONCERN_LENGTH = 1000;
    private static final int MAX_DOCTOR_LENGTH = 150;
    private static final int MAX_AGE_YEARS = 100;

    private static final Pattern HAS_LETTER = Pattern.compile(".*\\p{L}.*");
    private static final Pattern INDIAN_MOBILE = Pattern.compile("[6-9]\\d{9}");
    private static final DateTimeFormatter DOB_FORMAT = DateTimeFormatter.ofPattern("d-M-uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private LeadInputValidator() {
    }

    public static Optional<String> normalizeName(String raw) {
        String value = collapseSpaces(raw);
        if (value.length() < 2 || value.length() > MAX_NAME_LENGTH || !HAS_LETTER.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /** Accepts 10-digit Indian mobiles, optionally with +91 / 91 / 0 prefix; returns the 10 digits. */
    public static Optional<String> normalizePhone(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("[\\s\\-().+]", "");
        if (!digits.matches("\\d+")) {
            return Optional.empty();
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return INDIAN_MOBILE.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }

    /** Accepts DD-MM-YYYY (also with / or . separators); rejects future dates and dates over 100 years ago. */
    public static Optional<LocalDate> parseDateOfBirth(String raw, LocalDate today) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().replace('/', '-').replace('.', '-');
        try {
            LocalDate dob = LocalDate.parse(normalized, DOB_FORMAT);
            if (dob.isAfter(today) || dob.isBefore(today.minusYears(MAX_AGE_YEARS))) {
                return Optional.empty();
            }
            return Optional.of(dob);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public static Optional<String> normalizeConcern(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() < 2 || value.length() > MAX_CONCERN_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /** Any name is accepted; "none"/"no"/"na"/"-" are stored as "None". */
    public static Optional<String> normalizeReferralDoctor(String raw) {
        String value = collapseSpaces(raw);
        if (value.isEmpty() || value.length() > MAX_DOCTOR_LENGTH) {
            return Optional.empty();
        }
        String lower = value.toLowerCase();
        if (lower.equals("none") || lower.equals("no") || lower.equals("na") || lower.equals("n/a") || lower.equals("-")) {
            return Optional.of("None");
        }
        return Optional.of(value);
    }

    private static String collapseSpaces(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }
}
