package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.RegistrationAttemptReference;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.beanvalidation.SpringConstraintValidatorFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

/** T019 authorship. T020 owns execution; no future validator class or helper is needed to compile this test. */
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationValidationTest {

    private static final Instant REFERENCE = Instant.parse("2024-02-29T23:59:59Z");
    private static final Instant PROVIDER_TIME = Instant.parse("2099-01-01T00:00:00Z");
    private static final String EMOJI = "\uD83D\uDE00";
    // Independent literal transcription of the approved closed set; not Character.isWhitespace or a product helper.
    private static final int[] CLOSED_SPACES = {
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0, 0x1680,
            0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A,
            0x2028, 0x2029, 0x202F, 0x205F, 0x3000
    };

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("validationVectors")
    void validatesContractVectorsWithRealBeanValidationAndKeepsRawValues(Vector vector) {
        var request = vector.request();
        var before = rawValues(request);
        try (var local = new LocalValidation(vector.reference())) {
            try {
                boolean accepted = local.validator.validate(request).isEmpty();
                // Never render ConstraintViolation, invalidValue, request fields or secret arguments on failure.
                assertThat(accepted).as("contract outcome for %s", vector.id()).isEqualTo(vector.accepted());
            } finally {
                assertRawValuesUnchanged(vector.id(), before, request);
            }
        }
    }

    @Test
    void independentFixturesSeparateCodePointsUtf16BytesAndProviderLimits() {
        assertThat(EMOJI.repeat(120).length()).isEqualTo(240);
        assertThat(points(EMOJI.repeat(120))).isEqualTo(120);
        assertThat(points(EMOJI.repeat(11))).isEqualTo(11);
        assertThat(bytes(EMOJI.repeat(11))).isEqualTo(44);
        assertThat(points(EMOJI.repeat(12))).isEqualTo(12);
        assertThat(bytes(EMOJI.repeat(12))).isEqualTo(48);
        assertThat(bytes(EMOJI.repeat(16))).isEqualTo(64);
        assertThat(bytes(EMOJI.repeat(17))).isEqualTo(68);
        assertThat(bytes("\u00E1".repeat(32))).isEqualTo(64);
        assertThat(bytes("\u00E1".repeat(33))).isEqualTo(66);
        assertThat(points("e\u0301".repeat(22))).isEqualTo(44);
        assertThat(bytes("e\u0301".repeat(22))).isEqualTo(66);
        assertThat(points(emailAtLimit())).isEqualTo(254);
        assertThat(points(emailAboveLimit())).isEqualTo(255);
        assertThat(CLOSED_SPACES).hasSize(25);
        assertThat(points(allClosedSpaces())).isEqualTo(25);
        assertThat(bytes(allClosedSpaces())).isEqualTo(61);
    }

    @Test
    void validationDoesNotConsultServicesOrRepositories() {
        try (var local = new LocalValidation(REFERENCE)) {
            local.validator.validate(base());
            local.validator.validate(with(Field.NAME, ""));
            local.validator.validate(with(Field.EMAIL, "not-an-email"));
            assertThat(local.context.getBeanNamesForType(DataSource.class).length).isZero();
            assertThat(local.context.getBeanNamesForType(EntityManagerFactory.class).length).isZero();
            local.assertNoDomainIo();
        }
    }

    static Stream<Vector> validationVectors() {
        return Stream.of(presenceVectors(), nameVectors(), fullNameVectors(), emailVectors(), phoneVectors(),
                birthDateVectors(), passwordVectors(), closedWhitespaceVectors(), malformedUnicodeVectors())
                .flatMap(List::stream);
    }

    private static List<Vector> presenceVectors() {
        var vectors = new ArrayList<Vector>();
        for (var field : Field.values()) {
            add(vectors, "presence-null-" + field, field, null, false);
            add(vectors, "presence-empty-" + field, field, "", false);
            add(vectors, "presence-space-" + field, field, " ", false);
        }
        return vectors;
    }

    private static List<Vector> nameVectors() {
        var vectors = new ArrayList<Vector>();
        add(vectors, "name-min3", Field.NAME, "Ana", true);
        add(vectors, "name-max40", Field.NAME, "A" + "n".repeat(39), true);
        add(vectors, "name-case-distinct", Field.NAME, "ana", true);
        add(vectors, "name-digit-start", Field.NAME, "0._", true);
        add(vectors, "name-approved-signs", Field.NAME, "A._-9", true);
        add(vectors, "name-short2", Field.NAME, "An", false);
        add(vectors, "name-long41", Field.NAME, "A" + "n".repeat(40), false);
        add(vectors, "name-leading-dot", Field.NAME, ".ab", false);
        add(vectors, "name-leading-underscore", Field.NAME, "_ab", false);
        add(vectors, "name-leading-hyphen", Field.NAME, "-ab", false);
        add(vectors, "name-leading-space", Field.NAME, " Ana", false);
        add(vectors, "name-trailing-space", Field.NAME, "Ana ", false);
        add(vectors, "name-interior-space", Field.NAME, "An a", false);
        add(vectors, "name-non-ascii-letter", Field.NAME, "\u00C1na", false);
        add(vectors, "name-non-ascii-digit", Field.NAME, "A\u0661b", false);
        add(vectors, "name-final-newline", Field.NAME, "Ana\n", false);
        add(vectors, "name-tilde", Field.NAME, "An~", false);
        add(vectors, "name-slash", Field.NAME, "A/b", false);
        add(vectors, "name-supplementary", Field.NAME, "A" + EMOJI, false);
        return vectors;
    }

    private static List<Vector> fullNameVectors() {
        var vectors = new ArrayList<Vector>();
        add(vectors, "full-name-min2", Field.FULL_NAME, "Al", true);
        add(vectors, "full-name-min2-supplementary", Field.FULL_NAME, EMOJI.repeat(2), true);
        add(vectors, "full-name-max120", Field.FULL_NAME, "A".repeat(120), true);
        add(vectors, "full-name-max120-supplementary", Field.FULL_NAME, EMOJI.repeat(120), true);
        add(vectors, "full-name-decomposed2", Field.FULL_NAME, "e\u0301", true);
        add(vectors, "full-name-decomposed120", Field.FULL_NAME, "e\u0301".repeat(60), true);
        add(vectors, "full-name-accented-homonym", Field.FULL_NAME, "Jo\u00E3o da Silva", true);
        add(vectors, "full-name-interior-spaces", Field.FULL_NAME, "Ana  Silva", true);
        add(vectors, "full-name-no-extra-control-ban", Field.FULL_NAME, "A\u0000", true);
        add(vectors, "full-name-outside-set-200B", Field.FULL_NAME, "A\u200B", true);
        add(vectors, "full-name-outside-set-180E", Field.FULL_NAME, "A\u180E", true);
        add(vectors, "full-name-outside-set-FEFF", Field.FULL_NAME, "A\uFEFF", true);
        add(vectors, "full-name-short1", Field.FULL_NAME, "A", false);
        add(vectors, "full-name-short1-supplementary", Field.FULL_NAME, EMOJI, false);
        add(vectors, "full-name-long121", Field.FULL_NAME, "A".repeat(121), false);
        add(vectors, "full-name-long121-supplementary", Field.FULL_NAME, EMOJI.repeat(121), false);
        add(vectors, "full-name-decomposed122", Field.FULL_NAME, "e\u0301".repeat(61), false);
        add(vectors, "full-name-trim-to-one", Field.FULL_NAME, " A ", false);
        add(vectors, "full-name-only-closed-set", Field.FULL_NAME, allClosedSpaces(), false);
        return vectors;
    }

    private static List<Vector> emailVectors() {
        var vectors = new ArrayList<Vector>();
        add(vectors, "email-simple", Field.EMAIL, "ana@example.test", true);
        add(vectors, "email-case-and-plus", Field.EMAIL, "Pessoa+tag@Exemplo.com", true);
        add(vectors, "email-provider-unicode", Field.EMAIL, "jos\u00E9@example.test", true);
        add(vectors, "email-provider-quoted-local", Field.EMAIL, "\"ana silva\"@example.test", true);
        add(vectors, "email-provider-single-label", Field.EMAIL, "a@localhost", true);
        add(vectors, "email-provider-ip-literal", Field.EMAIL, "a@[127.0.0.1]", true);
        add(vectors, "email-provider-local64", Field.EMAIL, "a".repeat(64) + "@example.test", true);
        add(vectors, "email-provider-local65", Field.EMAIL, "a".repeat(65) + "@example.test", false);
        add(vectors, "email-total254", Field.EMAIL, emailAtLimit(), true);
        add(vectors, "email-total255", Field.EMAIL, emailAboveLimit(), false);
        add(vectors, "email-raw-over254-trimmed254", Field.EMAIL, "\u00A0" + emailAtLimit() + "\u3000", true);
        add(vectors, "email-missing-at", Field.EMAIL, "ana.example.test", false);
        add(vectors, "email-missing-local", Field.EMAIL, "@example.test", false);
        add(vectors, "email-missing-domain", Field.EMAIL, "ana@", false);
        add(vectors, "email-double-at", Field.EMAIL, "ana@@example.test", false);
        add(vectors, "email-local-consecutive-dots", Field.EMAIL, "ana..silva@example.test", false);
        add(vectors, "email-domain-empty-label", Field.EMAIL, "ana@example..test", false);
        add(vectors, "email-domain-leading-hyphen", Field.EMAIL, "ana@-example.test", false);
        add(vectors, "email-domain-trailing-dot", Field.EMAIL, "ana@example.test.", false);
        add(vectors, "email-invalid-interior-local-space", Field.EMAIL, "ana silva@example.test", false);
        add(vectors, "email-invalid-interior-domain-space", Field.EMAIL, "ana@example .test", false);
        add(vectors, "email-provider-label64", Field.EMAIL, "a@" + "b".repeat(64) + ".test", false);
        return vectors;
    }

    private static List<Vector> phoneVectors() {
        var vectors = new ArrayList<Vector>();
        add(vectors, "phone-min8", Field.PHONE, "+12345678", true);
        add(vectors, "phone-max15", Field.PHONE, "+123456789012345", true);
        add(vectors, "phone-short7", Field.PHONE, "+1234567", false);
        add(vectors, "phone-long16", Field.PHONE, "+1234567890123456", false);
        add(vectors, "phone-missing-plus", Field.PHONE, "12345678", false);
        add(vectors, "phone-only-plus", Field.PHONE, "+", false);
        add(vectors, "phone-arabic-digits", Field.PHONE, "+\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668", false);
        add(vectors, "phone-fullwidth-digits", Field.PHONE, "+\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17\uFF18", false);
        add(vectors, "phone-leading-space", Field.PHONE, " +12345678", false);
        add(vectors, "phone-trailing-space", Field.PHONE, "+12345678 ", false);
        add(vectors, "phone-interior-space", Field.PHONE, "+1234 5678", false);
        add(vectors, "phone-hyphen", Field.PHONE, "+1234-5678", false);
        add(vectors, "phone-dot", Field.PHONE, "+1234.5678", false);
        add(vectors, "phone-parentheses", Field.PHONE, "+(12345678)", false);
        add(vectors, "phone-fullwidth-plus", Field.PHONE, "\uFF0B12345678", false);
        return vectors;
    }

    private static List<Vector> birthDateVectors() {
        var vectors = new ArrayList<Vector>();
        add(vectors, "date-year0001", Field.BIRTH_DATE, "0001-01-01", true);
        vectors.add(vector("date-year9999", Field.BIRTH_DATE, "9999-12-31", true, Instant.parse("9999-12-31T23:59:59Z")));
        add(vectors, "date-today-no-minimum-age", Field.BIRTH_DATE, "2024-02-29", true);
        add(vectors, "date-yesterday", Field.BIRTH_DATE, "2024-02-28", true);
        add(vectors, "date-tomorrow-vs-attempt-not-provider-clock", Field.BIRTH_DATE, "2024-03-01", false);
        add(vectors, "date-century-leap2000", Field.BIRTH_DATE, "2000-02-29", true);
        add(vectors, "date-century-non-leap1900", Field.BIRTH_DATE, "1900-02-29", false);
        vectors.add(vector("date-century-non-leap2100", Field.BIRTH_DATE, "2100-02-29", false, Instant.parse("2200-01-01T00:00:00Z")));
        add(vectors, "date-impossible-april31", Field.BIRTH_DATE, "2023-04-31", false);
        add(vectors, "date-impossible-february30", Field.BIRTH_DATE, "2024-02-30", false);
        add(vectors, "date-day00", Field.BIRTH_DATE, "2024-02-00", false);
        add(vectors, "date-month00", Field.BIRTH_DATE, "2024-00-01", false);
        add(vectors, "date-month13", Field.BIRTH_DATE, "2024-13-01", false);
        add(vectors, "date-year0000", Field.BIRTH_DATE, "0000-01-01", false);
        add(vectors, "date-year10000", Field.BIRTH_DATE, "10000-01-01", false);
        add(vectors, "date-unpadded-month", Field.BIRTH_DATE, "2024-2-09", false);
        add(vectors, "date-unpadded-day", Field.BIRTH_DATE, "2024-02-9", false);
        add(vectors, "date-slashes", Field.BIRTH_DATE, "2024/02/29", false);
        add(vectors, "date-time-suffix", Field.BIRTH_DATE, "2024-02-29T00:00:00Z", false);
        add(vectors, "date-zone-suffix", Field.BIRTH_DATE, "2024-02-29Z", false);
        add(vectors, "date-leading-space", Field.BIRTH_DATE, " 2024-02-29", false);
        add(vectors, "date-trailing-space", Field.BIRTH_DATE, "2024-02-29 ", false);
        add(vectors, "date-non-ascii-digits", Field.BIRTH_DATE, "\u0662\u0660\u0662\u0664-02-29", false);
        add(vectors, "date-signed-year", Field.BIRTH_DATE, "+2024-02-29", false);
        return vectors;
    }

    private static List<Vector> passwordVectors() {
        var vectors = new ArrayList<Vector>();
        add(vectors, "password-ascii12", Field.PASSWORD, "a".repeat(12), true);
        add(vectors, "password-ascii64", Field.PASSWORD, "a".repeat(64), true);
        add(vectors, "password-ascii11", Field.PASSWORD, "a".repeat(11), false);
        add(vectors, "password-ascii65", Field.PASSWORD, "a".repeat(65), false);
        add(vectors, "password-no-composition-rule", Field.PASSWORD, "123456789012", true);
        add(vectors, "password-32-two-byte", Field.PASSWORD, "\u00E1".repeat(32), true);
        add(vectors, "password-33-two-byte", Field.PASSWORD, "\u00E1".repeat(33), false);
        add(vectors, "password-mixed64-bytes", Field.PASSWORD, "\u00E1".repeat(31) + "ab", true);
        add(vectors, "password-mixed65-bytes", Field.PASSWORD, "\u00E1".repeat(31) + "abc", false);
        add(vectors, "password-11-supplementary", Field.PASSWORD, EMOJI.repeat(11), false);
        add(vectors, "password-12-supplementary", Field.PASSWORD, EMOJI.repeat(12), true);
        add(vectors, "password-16-supplementary", Field.PASSWORD, EMOJI.repeat(16), true);
        add(vectors, "password-17-supplementary", Field.PASSWORD, EMOJI.repeat(17), false);
        add(vectors, "password-leading-space-counts", Field.PASSWORD, " " + "a".repeat(11), true);
        add(vectors, "password-trailing-space-counts", Field.PASSWORD, "a".repeat(11) + " ", true);
        add(vectors, "password-leading-space-over64", Field.PASSWORD, " " + "a".repeat(64), false);
        add(vectors, "password-trailing-space-over64", Field.PASSWORD, "a".repeat(64) + " ", false);
        add(vectors, "password-interior-space", Field.PASSWORD, "a".repeat(5) + " " + "a".repeat(6), true);
        add(vectors, "password-nul-not-closed-space", Field.PASSWORD, "\u0000".repeat(12), true);
        add(vectors, "password-embedded-nul", Field.PASSWORD, "a".repeat(11) + "\u0000", true);
        add(vectors, "password-other-control-not-closed-space", Field.PASSWORD, "\u0007".repeat(12), true);
        add(vectors, "password-decomposed63-bytes", Field.PASSWORD, "e\u0301".repeat(21), true);
        add(vectors, "password-decomposed66-no-nfc", Field.PASSWORD, "e\u0301".repeat(22), false);
        add(vectors, "password-composed44-bytes", Field.PASSWORD, "\u00E9".repeat(22), true);
        add(vectors, "password-outside-set-200B", Field.PASSWORD, "\u200B".repeat(12), true);
        add(vectors, "password-outside-set-FEFF", Field.PASSWORD, "\uFEFF".repeat(12), true);
        add(vectors, "password-outside-set-180E", Field.PASSWORD, "\u180E".repeat(12), true);
        add(vectors, "password-only-mixed-closed-spaces", Field.PASSWORD, allClosedSpaces(), false);
        return vectors;
    }

    private static List<Vector> closedWhitespaceVectors() {
        var vectors = new ArrayList<Vector>();
        for (int codePoint : CLOSED_SPACES) {
            String space = new String(Character.toChars(codePoint));
            String label = Integer.toHexString(codePoint);
            add(vectors, "closed-full-name-trim-valid-" + label, Field.FULL_NAME, space + "Al" + space, true);
            add(vectors, "closed-full-name-trim-short-" + label, Field.FULL_NAME, space + "A" + space, false);
            add(vectors, "closed-email-trim-valid-" + label, Field.EMAIL, space + "a@example.test" + space, true);
            add(vectors, "closed-name-no-trim-" + label, Field.NAME, space + "Ana" + space, false);
            add(vectors, "closed-phone-no-trim-" + label, Field.PHONE, space + "+12345678" + space, false);
            add(vectors, "closed-password-only-spaces-" + label, Field.PASSWORD, space.repeat(12), false);
            add(vectors, "closed-password-leading-preserved-" + label, Field.PASSWORD, space + "a".repeat(11), true);
            add(vectors, "closed-password-trailing-preserved-" + label, Field.PASSWORD, "a".repeat(11) + space, true);
        }
        return vectors;
    }

    private static List<Vector> malformedUnicodeVectors() {
        var vectors = new ArrayList<Vector>();
        String[] malformed = {"\uD800", "\uDC00", "\uD800A", "A\uDC00"};
        for (var field : List.of(Field.FULL_NAME, Field.EMAIL, Field.PASSWORD)) {
            for (int index = 0; index < malformed.length; index++) {
                String raw = switch (field) {
                    case FULL_NAME -> "Al" + malformed[index];
                    case EMAIL -> "ana" + malformed[index] + "@example.test";
                    case PASSWORD -> "a".repeat(12) + malformed[index];
                    default -> throw new AssertionError("Unexpected Unicode fixture field");
                };
                add(vectors, "malformed-unicode-" + field + "-" + index, field, raw, false);
            }
        }
        return vectors;
    }

    private static String emailAtLimit() {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
    }

    private static String emailAboveLimit() {
        return "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(62);
    }

    private static String allClosedSpaces() {
        var value = new StringBuilder();
        for (int codePoint : CLOSED_SPACES) { value.appendCodePoint(codePoint); }
        return value.toString();
    }

    private static CustomerRegistrationRequestDTO base() {
        return new CustomerRegistrationRequestDTO("Ana", "Ana Silva", "ana@example.test", "+12345678",
                "2000-02-29", "ValidPass123");
    }

    private static CustomerRegistrationRequestDTO with(Field field, String raw) {
        var base = base();
        return new CustomerRegistrationRequestDTO(field == Field.NAME ? raw : base.name(),
                field == Field.FULL_NAME ? raw : base.fullName(), field == Field.EMAIL ? raw : base.email(),
                field == Field.PHONE ? raw : base.phone(), field == Field.BIRTH_DATE ? raw : base.birthDate(),
                field == Field.PASSWORD ? raw : base.password());
    }

    private static void add(List<Vector> vectors, String id, Field field, String raw, boolean accepted) {
        vectors.add(vector(id, field, raw, accepted, REFERENCE));
    }

    private static Vector vector(String id, Field field, String raw, boolean accepted, Instant reference) {
        return new Vector(id, with(field, raw), accepted, reference);
    }

    private static int points(String value) { return value.codePointCount(0, value.length()); }
    private static int bytes(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }

    private static String[] rawValues(CustomerRegistrationRequestDTO value) {
        return new String[]{value.name(), value.fullName(), value.email(), value.phone(), value.birthDate(), value.password()};
    }

    private static void assertRawValuesUnchanged(String id, String[] before, CustomerRegistrationRequestDTO request) {
        var after = rawValues(request);
        for (int index = 0; index < before.length; index++) {
            assertThat(Objects.equals(before[index], after[index])).as("raw input preserved for %s, field %s", id, Field.values()[index]).isTrue();
        }
    }

    private enum Field { NAME, FULL_NAME, EMAIL, PHONE, BIRTH_DATE, PASSWORD }

    private record Vector(String id, CustomerRegistrationRequestDTO request, boolean accepted, Instant reference) {
        @Override public String toString() { return id; }
    }

    /** No application bootstrap/cache, request scope, data source, real repository or domain service. */
    private static final class LocalValidation implements AutoCloseable {
        private final DomainGuardBeanFactory beans = new DomainGuardBeanFactory();
        private final GenericApplicationContext context = new GenericApplicationContext(beans);
        private final UserAccountRepository repository = mock(UserAccountRepository.class);
        private final AuthenticationService service = mock(AuthenticationService.class);
        private final PasswordEncoder encoder = mock(PasswordEncoder.class);
        private final ValidatorFactory factory;
        private final Validator validator;

        LocalValidation(Instant reference) {
            AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
            beans.registerSingleton("registrationAttemptReference", RegistrationAttemptReference.fixed(reference));
            beans.registerSingleton("domainRepositoryProbe", repository);
            beans.registerSingleton("domainServiceProbe", service);
            beans.registerSingleton("passwordEncoderProbe", encoder);
            // Discover only local validation components when T021 supplies them; never a future class-name stub.
            new ClassPathBeanDefinitionScanner(context).scan("com.devsuperior.dscommerce.validation");
            var created = Validation.byProvider(HibernateValidator.class).configure()
                    .constraintValidatorFactory(new SpringConstraintValidatorFactory(beans))
                    .clockProvider(() -> Clock.fixed(PROVIDER_TIME, ZoneOffset.UTC)).buildValidatorFactory();
            try {
                beans.registerSingleton("validatorFactory", created);
                beans.registerSingleton("validator", created.getValidator());
                context.refresh();
                factory = created;
                validator = created.getValidator();
                assertThat(beans.getBean(RegistrationAttemptReference.class).snapshot().utcDate())
                        .as("explicit non-HTTP reference precondition").isEqualTo(LocalDate.ofInstant(reference, ZoneOffset.UTC));
            } catch (RuntimeException | Error failure) {
                try { created.close(); } finally { context.close(); }
                throw failure;
            }
        }

        void assertNoDomainIo() {
            boolean noCalls = mockingDetails(repository).getInvocations().isEmpty()
                    && mockingDetails(service).getInvocations().isEmpty() && mockingDetails(encoder).getInvocations().isEmpty();
            // Mockito verification errors may print arguments; inspect only booleans, never invocation contents.
            assertThat(noCalls).as("validation must not invoke domain services, repositories or encoding").isTrue();
            assertThat(beans.domainDependencyRequested).as("validation must not resolve domain dependencies").isFalse();
        }

        @Override public void close() {
            try { factory.close(); }
            finally { try { context.close(); } finally { assertNoDomainIo(); } }
        }
    }

    private static final class DomainGuardBeanFactory extends DefaultListableBeanFactory {
        private boolean domainDependencyRequested;

        @Override
        public Object resolveDependency(DependencyDescriptor descriptor, String requestingBeanName,
                                        Set<String> autowiredBeanNames, TypeConverter typeConverter) {
            rejectDomainType(descriptor.getDependencyType());
            rejectDomainType(descriptor.getResolvableType().resolveGeneric(0));
            return super.resolveDependency(descriptor, requestingBeanName, autowiredBeanNames, typeConverter);
        }

        private void rejectDomainType(Class<?> type) {
            if (type == null || type == RegistrationAttemptReference.class) { return; }
            if (type.getPackageName().startsWith("com.devsuperior.dscommerce.repositories")
                    || type.getPackageName().startsWith("com.devsuperior.dscommerce.services")) {
                domainDependencyRequested = true;
                throw new AssertionError("Domain dependency requested during local validation");
            }
        }
    }
}
