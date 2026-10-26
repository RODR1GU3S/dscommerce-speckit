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
        return Stream.of(new DomainCase("raw-a-outer-spaces", " legacy ", true),
                new DomainCase("raw-b-nul12", "\u0000".repeat(12), true));
    }

    private record DomainCase(String id, String password, boolean accepted) {
        @Override public String toString() { return id; }
    }
}
