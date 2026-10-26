package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.JwtTokenService;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.Invocation;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/** Closed-format boundary. Mocks make every encoder interaction observable without deriving hostile costs. */
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationFormatTest {

    private static final String PREFIX = "$argon2id$v=19$m=19456,t=2,p=1$";
    private static final String SALT = "A".repeat(22);
    private static final String DIGEST = "A".repeat(43);
    private static final String CANONICAL_PHC = PREFIX + SALT + "$" + DIGEST;
    private static final String CANDIDATE = "format-boundary-candidate";

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("recognizedFormats")
    void recognizedFormatsDispatchToExactlyOneMatchingAlgorithm(DispatchCase vector) {
        var bcrypt = mock(PasswordEncoder.class);
        var registration = mock(RegistrationPasswordHasher.class);
        when(bcrypt.matches(anyString(), anyString())).thenReturn(true);
        when(registration.matches(anyString(), anyString())).thenReturn(true);
        var verifier = new AccountPasswordVerifier(bcrypt, registration);

        assertThat(verifier.matches(CANDIDATE, vector.storedHash())).as("dispatch: %s", vector.id()).isTrue();

        assertOnlyMatches(bcrypt, vector.algorithm() == Algorithm.BCRYPT ? 1 : 0, vector.storedHash());
        assertOnlyMatches(registration, vector.algorithm() == Algorithm.ARGON2 ? 1 : 0, vector.storedHash());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("unrecognizedFormats")
    void rejectedFormatsStopAuthenticationBeforeEncodersTokenOrWrites(FormatCase vector) {
        var bcrypt = mock(PasswordEncoder.class);
        var registration = mock(RegistrationPasswordHasher.class);
        var verifier = new AccountPasswordVerifier(bcrypt, registration);
        var repository = mock(UserAccountRepository.class);
        var jwt = mock(JwtTokenService.class);
        var account = new UserAccount(42L, "ExactName", vector.storedHash());
        when(repository.findByName("ExactName")).thenReturn(Optional.of(account));
        var service = new AuthenticationService(repository, bcrypt, jwt, verifier);

        RuntimeException failure = loginFailure(service);

        assertThat(failure instanceof InvalidCredentialsException).as("credentials category: %s", vector.id()).isTrue();
        assertThat("Invalid credentials".equals(failure.getMessage())).as("fixed credentials message").isTrue();
        assertOnlyMatches(bcrypt, 0, vector.storedHash());
        assertOnlyMatches(registration, 0, vector.storedHash());
        assertReadOnlyLookupAndNoToken(repository, jwt);
        assertThat(Objects.equals(account.getPasswordHash(), vector.storedHash())).as("stored hash not repaired or rewritten").isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Algorithm.class)
    void recognizedEncoderFailuresRemainProcessingFailuresWithoutFallback(Algorithm algorithm) {
        var bcrypt = mock(PasswordEncoder.class);
        var registration = mock(RegistrationPasswordHasher.class);
        String storedHash = algorithm == Algorithm.BCRYPT ? bcryptHash("2a", "10", 53) : CANONICAL_PHC;
        if (algorithm == Algorithm.BCRYPT) {
            when(bcrypt.matches(anyString(), anyString())).thenThrow(new IllegalStateException("private encoder diagnostic"));
        } else {
            when(registration.matches(anyString(), anyString())).thenThrow(new IllegalStateException("private encoder diagnostic"));
        }
        var verifier = new AccountPasswordVerifier(bcrypt, registration);
        var repository = mock(UserAccountRepository.class);
        var jwt = mock(JwtTokenService.class);
        when(repository.findByName("ExactName")).thenReturn(Optional.of(new UserAccount(42L, "ExactName", storedHash)));
        var service = new AuthenticationService(repository, bcrypt, jwt, verifier);

        RuntimeException failure = loginFailure(service);

        assertThat(failure instanceof AuthenticationProcessingException).as("processing rather than mismatch").isTrue();
        assertThat("Authentication could not be completed".equals(failure.getMessage())).as("fixed processing message").isTrue();
        Throwable sanitized = failure.getCause();
        assertThat(sanitized instanceof IllegalStateException).as("sanitized verifier failure retained").isTrue();
        assertThat("Authentication could not be completed".equals(sanitized.getMessage()) && sanitized.getCause() == null)
                .as("original encoder diagnostic chain discarded").isTrue();
        assertOnlyMatches(bcrypt, algorithm == Algorithm.BCRYPT ? 1 : 0, storedHash);
        assertOnlyMatches(registration, algorithm == Algorithm.ARGON2 ? 1 : 0, storedHash);
        assertReadOnlyLookupAndNoToken(repository, jwt);
    }

    static Stream<DispatchCase> recognizedFormats() {
        Stream<DispatchCase> legacy = Stream.of("2", "2a", "2b", "2y").flatMap(version ->
                Stream.of("04", "10", "31").map(cost -> new DispatchCase("bcrypt-" + version + "-" + cost,
                        bcryptHash(version, cost, 53), Algorithm.BCRYPT)));
        return Stream.concat(legacy, Stream.of(new DispatchCase("argon2-canonical", CANONICAL_PHC, Algorithm.ARGON2)));
    }

    static Stream<FormatCase> unrecognizedFormats() {
        return Stream.of(
                new FormatCase("null", null),
                new FormatCase("empty", ""),
                new FormatCase("plain-text", "unsupported-format"),
                new FormatCase("wrapped-bcrypt", "{bcrypt}" + bcryptHash("2a", "10", 53)),
                new FormatCase("argon2i", CANONICAL_PHC.replace("argon2id", "argon2i")),
                new FormatCase("version16", CANONICAL_PHC.replace("v=19", "v=16")),
                new FormatCase("memory16384", CANONICAL_PHC.replace("m=19456", "m=16384")),
                new FormatCase("hostile-memory", CANONICAL_PHC.replace("m=19456", "m=2147483647")),
                new FormatCase("padded-salt", PREFIX + SALT + "==$" + DIGEST),
                new FormatCase("short-salt", PREFIX + "A".repeat(20) + "$" + DIGEST),
                new FormatCase("short-digest", PREFIX + SALT + "$" + "A".repeat(42)),
                new FormatCase("noncanonical-salt", PREFIX + "A".repeat(21) + "B$" + DIGEST),
                new FormatCase("noncanonical-digest", PREFIX + SALT + "$" + "A".repeat(42) + "B"),
                new FormatCase("url-safe-salt", PREFIX + "_" + "A".repeat(21) + "$" + DIGEST),
                new FormatCase("phc-suffix", CANONICAL_PHC + "x"),
                new FormatCase("bcrypt-2x", bcryptHash("2x", "10", 53)),
                new FormatCase("bcrypt-cost03", bcryptHash("2a", "03", 53)),
                new FormatCase("bcrypt-cost32", bcryptHash("2a", "32", 53)),
                new FormatCase("bcrypt-short-body", bcryptHash("2a", "10", 52)));
    }

    private static String bcryptHash(String version, String cost, int bodyLength) {
        return "$" + version + "$" + cost + "$" + ".".repeat(bodyLength);
    }

    private static RuntimeException loginFailure(AuthenticationService service) {
        try { service.login("ExactName", CANDIDATE); return null; }
        catch (RuntimeException failure) { return failure; }
    }

    private static void assertOnlyMatches(Object mock, int expected, String storedHash) {
        var calls = new ArrayList<>(mockingDetails(mock).getInvocations());
        assertThat(calls.size()).as("encoder boundary invocation count").isEqualTo(expected);
        for (Invocation call : calls) {
            assertThat(call.getMethod().getName()).as("only matches is permitted").isEqualTo("matches");
            assertThat(CANDIDATE.equals(call.getArguments()[0]) && Objects.equals(call.getArguments()[1], storedHash))
                    .as("original candidate and stored hash passed unchanged").isTrue();
        }
    }

    private static void assertReadOnlyLookupAndNoToken(UserAccountRepository repository, JwtTokenService jwt) {
        var calls = new ArrayList<>(mockingDetails(repository).getInvocations());
        assertThat(calls.size()).as("only one repository lookup, no write").isEqualTo(1);
        Invocation lookup = calls.getFirst();
        assertThat(lookup.getMethod().getName()).as("read-only boundary").isEqualTo("findByName");
        assertThat("ExactName".equals(lookup.getArguments()[0])).as("exact name lookup").isTrue();
        assertThat(mockingDetails(jwt).getInvocations().isEmpty()).as("no token after rejection/failure").isTrue();
    }

    private enum Algorithm { BCRYPT, ARGON2 }
    private record DispatchCase(String id, String storedHash, Algorithm algorithm) {
        @Override public String toString() { return id; }
    }
    private record FormatCase(String id, String storedHash) {
        @Override public String toString() { return id; }
    }
}
