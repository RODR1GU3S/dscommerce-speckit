package com.devsuperior.dscommerce.validation;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.util.regex.Pattern;

/** Local registration rules and permitted conversions; never changes the raw request. */
public final class RegistrationInputRules {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{2,39}");
    private static final Pattern PHONE = Pattern.compile("\\+[0-9]{8,15}");
    private static final Pattern BIRTH_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE
            .withResolverStyle(ResolverStyle.STRICT);

    private RegistrationInputRules() { }

    public static boolean isValidName(String raw) {
        return raw != null && NAME.matcher(raw).matches();
    }

    public static boolean isValidFullName(String raw) {
        if (!hasValidUnicode(raw)) { return false; }
        String value = trimExternalSpaces(raw);
        int points = value.codePointCount(0, value.length());
        return points >= 2 && points <= 120;
    }

    /** Uses the existing provider's @Email contract, with no private provider API or new factory. */
    public static boolean isValidEmail(String raw, Validator validator) {
        Objects.requireNonNull(validator, "A local Bean Validation validator is required");
        if (!hasValidUnicode(raw)) { return false; }
        String value = trimExternalSpaces(raw);
        int points = value.codePointCount(0, value.length());
        return points >= 1 && points <= 254
                && validator.validateValue(EmailSyntax.class, "value", value).isEmpty();
    }

    /** Shared only by fullName/email validation and their later command conversion. */
    public static String trimExternalSpaces(String raw) {
        requireValidUnicode(raw);
        int start = 0;
        int end = raw.length();
        while (start < end && isRegistrationSpace(raw.codePointAt(start))) {
            start += Character.charCount(raw.codePointAt(start));
        }
        while (start < end && isRegistrationSpace(raw.codePointBefore(end))) {
            end -= Character.charCount(raw.codePointBefore(end));
        }
        return raw.substring(start, end);
    }

    /** For a syntactically validated email; preserves its spelling separately from this binary key. */
    public static byte[] emailKey(String raw) {
        String value = trimExternalSpaces(raw);
        int points = value.codePointCount(0, value.length());
        if (points == 0 || points > 254) {
            throw new IllegalArgumentException("Invalid customer registration email");
        }
        var key = new StringBuilder(value.length());
        value.codePoints().forEach(point -> key.appendCodePoint(Character.toLowerCase(Character.toUpperCase(point))));
        return key.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isValidPhone(String raw) {
        return raw != null && PHONE.matcher(raw).matches();
    }

    /** Invalid input returns null; parser exceptions and received text never enter a diagnostic chain. */
    public static LocalDate parseBirthDate(String raw) {
        if (raw == null || !BIRTH_DATE.matcher(raw).matches()) { return null; }
        try {
            LocalDate date = LocalDate.parse(raw, DATE_FORMAT);
            return date.getYear() >= 1 && date.getYear() <= 9999 ? date : null;
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    public static boolean isValidBirthDate(String raw, LocalDate referenceDate) {
        Objects.requireNonNull(referenceDate, "A fixed registration reference date is required");
        LocalDate date = parseBirthDate(raw);
        return date != null && !date.isAfter(referenceDate);
    }

    /** New registrations only. Login's historical accepted domain is not restricted by these rules. */
    public static boolean isValidPassword(String raw) {
        if (!hasValidUnicode(raw)) { return false; }
        int points = raw.codePointCount(0, raw.length());
        return points >= 12 && raw.getBytes(StandardCharsets.UTF_8).length <= 64
                && !raw.codePoints().allMatch(RegistrationInputRules::isRegistrationSpace);
    }

    public static boolean hasValidUnicode(String value) {
        if (value == null) { return false; }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return false;
            }
        }
        return true;
    }

    private static void requireValidUnicode(String value) {
        if (!hasValidUnicode(value)) {
            throw new IllegalArgumentException("Invalid customer registration text");
        }
    }

    private static boolean isRegistrationSpace(int point) {
        return (point >= 0x0009 && point <= 0x000D) || point == 0x0020 || point == 0x0085
                || point == 0x00A0 || point == 0x1680 || (point >= 0x2000 && point <= 0x200A)
                || point == 0x2028 || point == 0x2029 || point == 0x202F || point == 0x205F || point == 0x3000;
    }

    /** Metadata carrier for validateValue; no instance, raw email retention or recursive DTO constraint. */
    private static final class EmailSyntax {
        @Email
        private String value;
    }
}
