package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.dto.LoginRequestDTO;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AccountPasswordVerifier;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.JwtTokenService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.HibernateValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/** Real DTO Bean Validation; literal expected decisions never call the production rule helper. */
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationLoginValidationTest {

    private ValidatorFactory factory;
    private Validator validator;

    @BeforeEach
    void prepareRealBeanValidation() {
        factory = Validation.byProvider(HibernateValidator.class).configure().buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterEach
    void closeFactory() {
        if (factory != null) { factory.close(); }
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("domainCases")
    void realDtoValidationIncludesHistoricalAAndRegistrationBWithoutChangingRawValues(DomainCase vector) {
        var request = new LoginRequestDTO("ExactName", vector.password());
        try {
            boolean accepted = validator.validate(request).isEmpty();
            assertThat(accepted).as("literal A/B outcome: %s", vector.id()).isEqualTo(vector.accepted());
        } finally {
            assertThat(Objects.equals(request.password(), vector.password())).as("raw sequence retained: %s", vector.id()).isTrue();
            assertThat("ExactName".equals(request.name())).as("name retained").isTrue();
        }
    }

    @Test
    void nameKeepsItsOldConstraintAndOnlyPasswordUsesTheAdditiveConstraint() {
        var descriptor = validator.getConstraintsForClass(LoginRequestDTO.class);
        Set<String> nameTypes = descriptor.getConstraintsForProperty("name").getConstraintDescriptors().stream()
                .map(constraint -> constraint.getAnnotation().annotationType().getSimpleName()).collect(Collectors.toSet());
        Set<String> passwordTypes = descriptor.getConstraintsForProperty("password").getConstraintDescriptors().stream()
                .map(constraint -> constraint.getAnnotation().annotationType().getSimpleName()).collect(Collectors.toSet());
        assertThat(nameTypes).as("unchanged name constraint").isEqualTo(Set.of("NotBlank"));
        assertThat(passwordTypes).as("password constraint, no NotBlank intersection").isEqualTo(Set.of("ValidLoginPassword"));
        var invalidName = validator.validate(new LoginRequestDTO(" ", "a".repeat(12)));
        List<String> paths = invalidName.stream().map(violation -> violation.getPropertyPath().toString()).sorted().toList();
        assertThat(paths).as("name still invalid without exposing any input").isEqualTo(List.of("name"));
        assertThat(validator.validate(new LoginRequestDTO(" legacy+name ", "x")).isEmpty())
                .as("no registration alphabet/length imposed on historical login fields").isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("rawForwardingCases")
    void validatedPasswordsReachTheNewVerificationBoundaryUnchangedBeforeTokenIssuance(DomainCase vector) {
        var request = new LoginRequestDTO("ExactName", vector.password());
        assertThat(validator.validate(request).isEmpty()).as("accepted DTO precondition: %s", vector.id()).isTrue();
        var repository = mock(UserAccountRepository.class);
        var bcrypt = mock(PasswordEncoder.class);
        var verifier = mock(AccountPasswordVerifier.class);
        var jwt = mock(JwtTokenService.class);
        String stored = "$argon2id$v=19$m=19456,t=2,p=1$" + "A".repeat(22) + "$" + "A".repeat(43);
        var account = new UserAccount(42L, "ExactName", stored);
        var token = new TokenResponseDTO("local-test-token", "Bearer");
        var observed = new AtomicReference<Object>();
        var observedHash = new AtomicReference<Object>();
        var stages = new ArrayList<String>();
        when(repository.findByName("ExactName")).thenAnswer(invocation -> {
            stages.add("lookup");
            return Optional.of(account);
        });
        when(verifier.matches(anyString(), anyString())).thenAnswer(invocation -> {
            stages.add("verify");
            observed.set(invocation.getArgument(0));
            observedHash.set(invocation.getArgument(1));
            return true;
        });
        when(jwt.issueToken(42L, "ExactName")).thenAnswer(invocation -> {
            stages.add("token");
            return token;
        });
        var service = new AuthenticationService(repository, bcrypt, jwt, verifier);

        var returned = service.login(request.name(), request.password());

        assertThat(Objects.equals(observed.get(), vector.password())).as("the exact password sequence reaches verification").isTrue();
        assertThat(Objects.equals(observedHash.get(), stored)).as("the stored hash reaches verification unchanged").isTrue();
        assertThat(returned != null && token.accessToken().equals(returned.accessToken())
                && token.tokenType().equals(returned.tokenType())).as("existing token fields preserved").isTrue();
        assertThat(stages).as("lookup, verification and only then token").containsExactly("lookup", "verify", "token");
        assertThat(mockingDetails(bcrypt).getInvocations().isEmpty()).as("found account never takes dummy/encoder bypass").isTrue();
        var reads = new ArrayList<>(mockingDetails(repository).getInvocations());
        assertThat(reads.size()).as("no repository writes").isEqualTo(1);
        assertThat(reads.getFirst().getMethod().getName()).isEqualTo("findByName");
        assertThat(mockingDetails(verifier).getInvocations().size()).as("one verification").isEqualTo(1);
        assertThat(mockingDetails(jwt).getInvocations().size()).as("one token issuance").isEqualTo(1);
    }

    static Stream<DomainCase> domainCases() {
        return Stream.of(
                new DomainCase("a-short", "x", true),
                new DomainCase("a-over64bytes", "a".repeat(65), true),
                new DomainCase("a-nbsp", "\u00A0", true),
                new DomainCase("a-malformed-unicode", "\uD800", true),
                new DomainCase("overlap-ascii12", "a".repeat(12), true),
                new DomainCase("b-nul12", "\u0000".repeat(12), true),
                new DomainCase("b-control001c12", "\u001C".repeat(12), true),
                new DomainCase("invalid-null", null, false),
                new DomainCase("invalid-empty", "", false),
                new DomainCase("invalid-space", " ", false),
                new DomainCase("invalid-spaces12", " ".repeat(12), false),
                new DomainCase("invalid-tabs12", "\t".repeat(12), false),
                new DomainCase("invalid-nul11", "\u0000".repeat(11), false));
    }

    static Stream<DomainCase> rawForwardingCases() {
        return Stream.concat(Stream.of(new DomainCase("raw-a-outer-spaces", " legacy ", true),
                new DomainCase("raw-b-nul12", "\u0000".repeat(12), true)),
                unionCases().filter(vector -> vector.historical() || vector.registration())
                        .map(vector -> new DomainCase("raw-union-" + vector.id(), vector.password(), true)));
    }

    /** T061 authorship only. Fixed truth-table flags are independent of every production rule helper. */
    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("unionCases")
    void realProviderSeparatesAOnlyBOnlyOverlapAndNeither(UnionCase vector) {
        var historical = new HistoricalPassword(vector.password());
        var current = new LoginRequestDTO("ExactName", vector.password());
        try {
            boolean acceptedA = validator.validate(historical).isEmpty();
            // B is an actual result under test, never the expected union or the forwarding filter.
            boolean acceptedB = RegistrationInputRules.isValidPassword(vector.password());
            boolean acceptedLogin = validator.validate(current).isEmpty();
            assertThat(acceptedA).as("historical provider A: %s", vector.id()).isEqualTo(vector.historical());
            assertThat(acceptedB).as("registration domain B: %s", vector.id()).isEqualTo(vector.registration());
            assertThat(acceptedLogin).as("literal A union B: %s", vector.id())
                    .isEqualTo(vector.historical() || vector.registration());
        } finally {
            assertThat(Objects.equals(historical.password(), vector.password())
                    && Objects.equals(current.password(), vector.password()) && "ExactName".equals(current.name()))
                    .as("both raw passwords and the login name retained: %s", vector.id()).isTrue();
        }
    }

    static Stream<UnionCase> unionCases() {
        return Stream.of(
                new UnionCase("a-only-one-character", "x", true, false),
                new UnionCase("a-only-eleven-characters", "a".repeat(11), true, false),
                new UnionCase("a-only-ascii65bytes", "a".repeat(65), true, false),
                new UnionCase("a-only-acute33-66bytes", "\u00E1".repeat(33), true, false),
                new UnionCase("a-only-emoji17-68bytes", "\uD83D\uDE00".repeat(17), true, false),
                new UnionCase("a-only-nbsp-single", "\u00A0", true, false),
                new UnionCase("a-only-nbsp12", "\u00A0".repeat(12), true, false),
                new UnionCase("a-only-em-space12", "\u2003".repeat(12), true, false),
                new UnionCase("a-only-ideographic-space12", "\u3000".repeat(12), true, false),
                new UnionCase("a-only-narrow-nbsp12", "\u202F".repeat(12), true, false),
                new UnionCase("a-only-isolated-high", "\uD800", true, false),
                new UnionCase("a-only-isolated-low", "\uDC00", true, false),
                new UnionCase("a-only-broken-pair-interior", "legacy\uD800X", true, false),
                new UnionCase("a-only-reversed-pair", "\uDE00\uD83D", true, false),
                new UnionCase("a-only-malformed-with-outer-spaces", " \uD800 ", true, false),
                new UnionCase("a-only-short-with-outer-spaces", " x ", true, false),
                new UnionCase("overlap-ascii12", "AbCdEfGhIjKl", true, true),
                new UnionCase("overlap-ascii64bytes", "a".repeat(64), true, true),
                new UnionCase("overlap-acute32-64bytes", "\u00E1".repeat(32), true, true),
                new UnionCase("overlap-emoji12-48bytes", "\uD83D\uDE00".repeat(12), true, true),
                new UnionCase("overlap-emoji16-64bytes", "\uD83D\uDE00".repeat(16), true, true),
                new UnionCase("overlap-leading-space", " abcdefghijkl", true, true),
                new UnionCase("overlap-trailing-space", "abcdefghijkl ", true, true),
                new UnionCase("overlap-interior-space", "abcdef ghijkl", true, true),
                new UnionCase("overlap-composed", "\u00E9".repeat(12), true, true),
                new UnionCase("overlap-decomposed", "e\u0301".repeat(12), true, true),
                new UnionCase("overlap-embedded-nul", "abcdefghijkl\0abcdefghijkl", true, true),
                new UnionCase("b-only-nul12", "\0".repeat(12), false, true),
                new UnionCase("b-only-control001c12", "\u001C".repeat(12), false, true),
                new UnionCase("b-only-bell12", "\u0007".repeat(12), false, true),
                new UnionCase("b-only-mixed-controls12", "\0\u001C".repeat(6), false, true),
                new UnionCase("neither-null", null, false, false),
                new UnionCase("neither-empty", "", false, false),
                new UnionCase("neither-single-nul", "\0", false, false),
                new UnionCase("neither-nul11", "\0".repeat(11), false, false),
                new UnionCase("neither-control001c11", "\u001C".repeat(11), false, false),
                new UnionCase("neither-nul65bytes", "\0".repeat(65), false, false),
                new UnionCase("neither-ascii-spaces12", " ".repeat(12), false, false),
                new UnionCase("neither-ascii-tabs12", "\t".repeat(12), false, false),
                new UnionCase("neither-mixed-ascii-spaces", " \t\r\n\f\u000B".repeat(2), false, false));
    }

    private record DomainCase(String id, String password, boolean accepted) {
        @Override public String toString() { return id; }
    }

    private record UnionCase(String id, String password, boolean historical, boolean registration) {
        @Override public String toString() { return id; }
    }

    /** Real old @NotBlank provider predicate; no copied trim expression defines this test's oracle. */
    private record HistoricalPassword(@NotBlank String password) {
        @Override public String toString() { return "HistoricalPassword[redacted]"; }
    }
}
