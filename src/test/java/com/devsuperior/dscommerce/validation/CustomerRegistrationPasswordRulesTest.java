package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T058 authorship only. T064 owns execution; local acceptance is not cryptographic verification. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationPasswordRulesTest {

    private static final String EMOJI = "\uD83D\uDE00";
    private static final String PLAIN = "abcdefghijkl";
    private static final String PLAIN_HEX = "6162636465666768696a6b6c";
    private ValidatorFactory factory;
    private Validator validator;

    @BeforeAll
    void prepareOnlyTheRealLocalDtoValidation() {
        var reference = RegistrationAttemptReference.fixed(Instant.parse("2024-02-29T23:59:59Z"));
        var configuration = Validation.byProvider(HibernateValidator.class).configure();
        var defaults = configuration.getDefaultConstraintValidatorFactory();
        var delegate = new AtomicReference<Validator>();
        factory = configuration.constraintValidatorFactory(new ConstraintValidatorFactory() {
            @Override public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> type) {
                if (CustomerRegistrationValidator.class.equals(type)) {
                    return type.cast(new CustomerRegistrationValidator(reference, Objects.requireNonNull(delegate.get())));
                }
                return defaults.getInstance(type);
            }
            @Override public void releaseInstance(ConstraintValidator<?, ?> instance) {
                if (!(instance instanceof CustomerRegistrationValidator)) { defaults.releaseInstance(instance); }
            }
        }).buildValidatorFactory();
        validator = factory.getValidator();
        delegate.set(validator);
    }

    @AfterAll
    void closeOnlyTheLocalFactory() {
        if (factory != null) { factory.close(); }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("passwordCases")
    void passwordVectorsFollowFixedLimitsAndPreserveAllRawFields(PasswordCase vector) {
        if (vector.points() != null) {
            // Literal expectations below do not call any production counting/Unicode/space helper.
            assertThat(vector.raw().codePointCount(0, vector.raw().length()))
                    .as("independent code-point count: %s", vector.id()).isEqualTo(vector.points().intValue());
            assertThat(vector.raw().getBytes(StandardCharsets.UTF_8).length)
                    .as("independent UTF-8 byte count: %s", vector.id()).isEqualTo(vector.bytes().intValue());
        }
        assertOutcomeAndUnchangedRawFields(vector.id(), vector.raw(), vector.accepted());
    }

    static Stream<PasswordCase> passwordCases() {
        return Stream.of(boundaryCases(), closedSpaceCases(), outsideSpaceCases(), malformedUnicodeCases())
                .flatMap(stream -> stream);
    }

    private static Stream<PasswordCase> boundaryCases() {
        return Stream.of(
                v("null", null, null, null, false),
                v("empty", "", 0, 0, false),
                v("ascii11", "a".repeat(11), 11, 11, false),
                v("ascii12", "a".repeat(12), 12, 12, true),
                v("ascii64", "a".repeat(64), 64, 64, true),
                v("ascii65", "a".repeat(65), 65, 65, false),
                v("acute32", "\u00E1".repeat(32), 32, 64, true),
                v("acute33", "\u00E1".repeat(33), 33, 66, false),
                v("emoji11", EMOJI.repeat(11), 11, 44, false),
                v("emoji12", EMOJI.repeat(12), 12, 48, true),
                v("emoji16", EMOJI.repeat(16), 16, 64, true),
                v("emoji17", EMOJI.repeat(17), 17, 68, false),
                v("mixed-two-byte63", "\u00E1".repeat(31) + "a", 32, 63, true),
                v("mixed-two-byte64", "\u00E1".repeat(31) + "ab", 33, 64, true),
                v("mixed-two-byte65", "\u00E1".repeat(31) + "abc", 34, 65, false),
                v("mixed-four-byte63", EMOJI.repeat(15) + "abc", 18, 63, true),
                v("mixed-four-byte64", EMOJI.repeat(15) + "abcd", 19, 64, true),
                v("mixed-four-byte65", EMOJI.repeat(15) + "abcde", 20, 65, false),
                v("mixed-three-widths64", EMOJI.repeat(14) + "\u00E1".repeat(3) + "ab", 19, 64, true),
                v("composed22", "\u00E9".repeat(22), 22, 44, true),
                v("decomposed21", "e\u0301".repeat(21), 42, 63, true),
                v("decomposed22-no-nfc", "e\u0301".repeat(22), 44, 66, false),
                v("composed-six-not-twelve-points", "\u00E9".repeat(6), 6, 12, false),
                v("decomposed-six-twelve-points", "e\u0301".repeat(6), 12, 18, true),
                v("lowercase-only", PLAIN, 12, 12, true),
                v("uppercase-only", "ABCDEFGHIJKL", 12, 12, true),
                v("digits-only-no-composition-rule", "123456789012", 12, 12, true),
                v("nul12", "\0".repeat(12), 12, 12, true),
                v("nul-at-minimum", "abcdefghijk\0", 12, 12, true),
                v("nul-suffix", PLAIN + "\0", 13, 13, true),
                v("nul-middle", PLAIN + "\0" + PLAIN, 25, 25, true),
                v("mixed-closed-spaces-only", " \t\n\r\u0085\u00A0\u1680\u2000\u2028\u202F\u205F\u3000",
                        12, 26, false));
    }

    private static Stream<PasswordCase> closedSpaceCases() {
        // Closed set transcribed from the spec, with fixed UTF-8 widths, not isWhitespace/trim/strip.
        return Stream.of(
                new PointWidth(0x0009, 1), new PointWidth(0x000A, 1), new PointWidth(0x000B, 1),
                new PointWidth(0x000C, 1), new PointWidth(0x000D, 1), new PointWidth(0x0020, 1),
                new PointWidth(0x0085, 2), new PointWidth(0x00A0, 2), new PointWidth(0x1680, 3),
                new PointWidth(0x2000, 3), new PointWidth(0x2001, 3), new PointWidth(0x2002, 3),
                new PointWidth(0x2003, 3), new PointWidth(0x2004, 3), new PointWidth(0x2005, 3),
                new PointWidth(0x2006, 3), new PointWidth(0x2007, 3), new PointWidth(0x2008, 3),
                new PointWidth(0x2009, 3), new PointWidth(0x200A, 3), new PointWidth(0x2028, 3),
                new PointWidth(0x2029, 3), new PointWidth(0x202F, 3), new PointWidth(0x205F, 3),
                new PointWidth(0x3000, 3)).flatMap(point -> {
                    String space = new String(Character.toChars(point.codePoint()));
                    String id = "closed-" + Integer.toHexString(point.codePoint());
                    return Stream.of(
                            v(id + "-only", space.repeat(12), 12, 12 * point.bytes(), false),
                            v(id + "-leading", space + "a".repeat(11), 12, 11 + point.bytes(), true),
                            v(id + "-trailing", "a".repeat(11) + space, 12, 11 + point.bytes(), true),
                            v(id + "-interior", "a".repeat(5) + space + "a".repeat(6), 12, 11 + point.bytes(), true),
                            v(id + "-leading-over64", space + "a".repeat(64), 65, 64 + point.bytes(), false),
                            v(id + "-trailing-over64", "a".repeat(64) + space, 65, 64 + point.bytes(), false));
                });
    }

    private static Stream<PasswordCase> outsideSpaceCases() {
        return Stream.of(
                new PointWidth(0x0000, 1), new PointWidth(0x0001, 1), new PointWidth(0x0007, 1),
                new PointWidth(0x0008, 1), new PointWidth(0x000E, 1), new PointWidth(0x001B, 1),
                new PointWidth(0x001C, 1), new PointWidth(0x001F, 1), new PointWidth(0x007F, 1),
                new PointWidth(0x009F, 2), new PointWidth(0x180E, 3), new PointWidth(0x200B, 3),
                new PointWidth(0x2060, 3), new PointWidth(0xFEFF, 3)).flatMap(point -> {
                    String character = new String(Character.toChars(point.codePoint()));
                    String id = "outside-" + Integer.toHexString(point.codePoint());
                    return Stream.of(
                            v(id + "-only", character.repeat(12), 12, 12 * point.bytes(), true),
                            v(id + "-suffix", "a".repeat(11) + character, 12, 11 + point.bytes(), true));
                });
    }

    private static Stream<PasswordCase> malformedUnicodeCases() {
        // No encoding/count oracle for malformed Unicode: replacement UTF-8 must not legitimize it.
        return Stream.of(
                v("isolated-high-suffix", "a".repeat(12) + "\uD800", null, null, false),
                v("isolated-low-suffix", "a".repeat(12) + "\uDC00", null, null, false),
                v("isolated-high-prefix", "\uD800" + "a".repeat(12), null, null, false),
                v("isolated-low-prefix", "\uDC00" + "a".repeat(12), null, null, false),
                v("broken-pair-interior", "a".repeat(6) + "\uD800X" + "a".repeat(6), null, null, false),
                v("reversed-pair-interior", "a".repeat(6) + "\uDE00\uD83D" + "a".repeat(6), null, null, false),
                v("valid-pairs-and-invalid-high", EMOJI.repeat(12) + "\uD800", null, null, false),
                v("invalid-high-before-valid-pairs", "\uD800" + EMOJI.repeat(12), null, null, false));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("distinctSequences")
    void acceptedSpellingsRetainTheirDistinctLiteralUtf8Sequences(SequencePair pair) {
        assertOutcomeAndUnchangedRawFields(pair.id() + "-first", pair.first(), true);
        assertOutcomeAndUnchangedRawFields(pair.id() + "-second", pair.second(), true);
        byte[] first = pair.first().getBytes(StandardCharsets.UTF_8);
        byte[] second = pair.second().getBytes(StandardCharsets.UTF_8);
        assertThat(HexFormat.of().formatHex(first).equals(pair.firstHex())
                && HexFormat.of().formatHex(second).equals(pair.secondHex()))
                .as("fixed literal UTF-8 fixtures without BOM: %s", pair.id()).isTrue();
        assertThat(!pair.first().equals(pair.second()) && !Arrays.equals(first, second))
                .as("distinct accepted input sequences, not a claim about authentication: %s", pair.id()).isTrue();
    }

    static Stream<SequencePair> distinctSequences() {
        return Stream.of(
                new SequencePair("case", PLAIN, "Abcdefghijkl", PLAIN_HEX, "4162636465666768696a6b6c"),
                new SequencePair("composed-decomposed", "\u00E9".repeat(12), "e\u0301".repeat(12),
                        "c3a9".repeat(12), "65cc81".repeat(12)),
                new SequencePair("leading-space", " " + PLAIN, PLAIN, "20" + PLAIN_HEX, PLAIN_HEX),
                new SequencePair("trailing-space", PLAIN + " ", PLAIN, PLAIN_HEX + "20", PLAIN_HEX),
                new SequencePair("nul-suffix", PLAIN, PLAIN + "\0", PLAIN_HEX, PLAIN_HEX + "00"),
                new SequencePair("control-only", "\0".repeat(12), "\u001C".repeat(12),
                        "00".repeat(12), "1c".repeat(12)),
                new SequencePair("p-q-local-only", PLAIN, PLAIN + "\0" + PLAIN,
                        PLAIN_HEX, PLAIN_HEX + "00" + PLAIN_HEX));
    }

    private void assertOutcomeAndUnchangedRawFields(String id, String password, boolean accepted) {
        var input = new CustomerRegistrationRequestDTO("Ana", "Ana Silva", "ana@example.test",
                "+12345678", "2000-02-29", password);
        // A complete positive control isolates the changed password from every other DTO field.
        var control = new CustomerRegistrationRequestDTO("Ana", "Ana Silva", "ana@example.test",
                "+12345678", "2000-02-29", PLAIN);
        assertThat(validator.validate(control).isEmpty()).as("valid local control: %s", id).isTrue();
        try {
            boolean actual = validator.validate(input).isEmpty();
            // Never print ConstraintViolation, invalidValue, password, byte arrays or DTO toString.
            assertThat(actual).as("fixed password policy outcome: %s", id).isEqualTo(accepted);
        } finally {
            assertThat("Ana".equals(input.name()) && "Ana Silva".equals(input.fullName())
                    && "ana@example.test".equals(input.email()) && "+12345678".equals(input.phone())
                    && "2000-02-29".equals(input.birthDate()) && Objects.equals(password, input.password()))
                    .as("all six raw fields unchanged: %s", id).isTrue();
        }
    }

    private static PasswordCase v(String id, String raw, Integer points, Integer bytes, boolean accepted) {
        return new PasswordCase(id, raw, points, bytes, accepted);
    }

    private record PointWidth(int codePoint, int bytes) { }
    private record PasswordCase(String id, String raw, Integer points, Integer bytes, boolean accepted) {
        @Override public String toString() { return id; }
    }
    private record SequencePair(String id, String first, String second, String firstHex, String secondHex) {
        @Override public String toString() { return id; }
    }
}
