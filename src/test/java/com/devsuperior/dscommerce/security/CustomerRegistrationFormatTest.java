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
        return Stream.concat(legacy, Stream.of(
                new DispatchCase("argon2-canonical", CANONICAL_PHC, Algorithm.ARGON2),
                new DispatchCase("argon2-canonical-all-ff",
                        PREFIX + "/".repeat(21) + "w$" + "/".repeat(42) + "8", Algorithm.ARGON2),
                new DispatchCase("argon2-canonical-standard-plus-slash",
                        PREFIX + "+".repeat(21) + "w$" + "/".repeat(42) + "8", Algorithm.ARGON2)));
    }

    static Stream<FormatCase> unrecognizedFormats() {
        return Stream.concat(Stream.of(
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
                new FormatCase("bcrypt-short-body", bcryptHash("2a", "10", 52))), additionalMalformedFormats());
    }

    /** T060 authorship only: bounded strings, mocked encoders, no hostile parameter reaches a real KDF. */
    private static Stream<FormatCase> additionalMalformedFormats() {
        return Stream.of(variantAndEnvelopeFormats(), parameterFormats(), base64AndLengthFormats(), bcryptFormats())
                .flatMap(stream -> stream);
    }

    private static Stream<FormatCase> variantAndEnvelopeFormats() {
        return Stream.of(
                new FormatCase("argon2d", CANONICAL_PHC.replace("argon2id", "argon2d")),
                new FormatCase("unknown-algorithm", CANONICAL_PHC.replace("argon2id", "argon2x")),
                new FormatCase("algorithm-case", CANONICAL_PHC.replace("argon2id", "Argon2id")),
                new FormatCase("version-missing", CANONICAL_PHC.replace("$v=19", "")),
                new FormatCase("version20", CANONICAL_PHC.replace("v=19", "v=20")),
                new FormatCase("version-zero", CANONICAL_PHC.replace("v=19", "v=0")),
                new FormatCase("version-leading-zero", CANONICAL_PHC.replace("v=19", "v=019")),
                new FormatCase("version-plus", CANONICAL_PHC.replace("v=19", "v=+19")),
                new FormatCase("version-negative", CANONICAL_PHC.replace("v=19", "v=-19")),
                new FormatCase("version-duplicate", CANONICAL_PHC.replace("$v=19$", "$v=19$v=19$")),
                new FormatCase("wrapped-argon2", "{argon2}" + CANONICAL_PHC),
                new FormatCase("wrapped-argon2id", "{argon2id}" + CANONICAL_PHC),
                new FormatCase("wrapped-noop", "{noop}unsupported-format"),
                new FormatCase("whitespace-only", " \t\n"),
                new FormatCase("phc-leading-space", " " + CANONICAL_PHC),
                new FormatCase("phc-trailing-space", CANONICAL_PHC + " "),
                new FormatCase("phc-trailing-newline", CANONICAL_PHC + "\n"),
                new FormatCase("phc-suffix-nul", CANONICAL_PHC + "\0"),
                new FormatCase("phc-prefix-noise", "noise" + CANONICAL_PHC),
                new FormatCase("phc-extra-segment", CANONICAL_PHC + "$extra"),
                new FormatCase("phc-concatenated", CANONICAL_PHC + CANONICAL_PHC),
                new FormatCase("phc-no-leading-dollar", CANONICAL_PHC.substring(1)));
    }

    private static Stream<FormatCase> parameterFormats() {
        return Stream.of(
                new FormatCase("order-m-p-t", phcWithParameters("m=19456,p=1,t=2")),
                new FormatCase("order-t-m-p", phcWithParameters("t=2,m=19456,p=1")),
                new FormatCase("order-t-p-m", phcWithParameters("t=2,p=1,m=19456")),
                new FormatCase("order-p-m-t", phcWithParameters("p=1,m=19456,t=2")),
                new FormatCase("order-p-t-m", phcWithParameters("p=1,t=2,m=19456")),
                new FormatCase("memory-duplicate", phcWithParameters("m=19456,m=19456,t=2,p=1")),
                new FormatCase("iterations-duplicate", phcWithParameters("m=19456,t=2,t=2,p=1")),
                new FormatCase("parallelism-duplicate", phcWithParameters("m=19456,t=2,p=1,p=1")),
                new FormatCase("memory-missing", phcWithParameters("t=2,p=1")),
                new FormatCase("iterations-missing", phcWithParameters("m=19456,p=1")),
                new FormatCase("parallelism-missing", phcWithParameters("m=19456,t=2")),
                new FormatCase("parameter-unknown", phcWithParameters("m=19456,t=2,p=1,x=1")),
                new FormatCase("parameter-trailing-comma", phcWithParameters("m=19456,t=2,p=1,")),
                new FormatCase("parameter-double-comma", phcWithParameters("m=19456,,t=2,p=1")),
                new FormatCase("parameter-spaces", phcWithParameters("m=19456, t=2,p=1")),
                new FormatCase("memory-below-profile", phcWithParameters("m=19455,t=2,p=1")),
                new FormatCase("memory-above-profile", phcWithParameters("m=19457,t=2,p=1")),
                new FormatCase("memory-zero", phcWithParameters("m=0,t=2,p=1")),
                new FormatCase("memory-negative", phcWithParameters("m=-1,t=2,p=1")),
                new FormatCase("memory-plus", phcWithParameters("m=+19456,t=2,p=1")),
                new FormatCase("memory-leading-zero", phcWithParameters("m=019456,t=2,p=1")),
                new FormatCase("memory-decimal", phcWithParameters("m=19456.0,t=2,p=1")),
                new FormatCase("memory-int-overflow", phcWithParameters("m=2147483648,t=2,p=1")),
                new FormatCase("memory-long-max", phcWithParameters("m=9223372036854775807,t=2,p=1")),
                new FormatCase("memory-oversized-number", phcWithParameters("m=" + "9".repeat(128) + ",t=2,p=1")),
                new FormatCase("iterations-one", phcWithParameters("m=19456,t=1,p=1")),
                new FormatCase("iterations-three", phcWithParameters("m=19456,t=3,p=1")),
                new FormatCase("iterations-zero", phcWithParameters("m=19456,t=0,p=1")),
                new FormatCase("iterations-negative", phcWithParameters("m=19456,t=-2,p=1")),
                new FormatCase("iterations-plus", phcWithParameters("m=19456,t=+2,p=1")),
                new FormatCase("iterations-leading-zero", phcWithParameters("m=19456,t=02,p=1")),
                new FormatCase("iterations-hostile", phcWithParameters("m=19456,t=2147483647,p=1")),
                new FormatCase("iterations-int-overflow", phcWithParameters("m=19456,t=2147483648,p=1")),
                new FormatCase("parallelism-zero", phcWithParameters("m=19456,t=2,p=0")),
                new FormatCase("parallelism-two", phcWithParameters("m=19456,t=2,p=2")),
                new FormatCase("parallelism-negative", phcWithParameters("m=19456,t=2,p=-1")),
                new FormatCase("parallelism-plus", phcWithParameters("m=19456,t=2,p=+1")),
                new FormatCase("parallelism-leading-zero", phcWithParameters("m=19456,t=2,p=01")),
                new FormatCase("parallelism-hostile", phcWithParameters("m=19456,t=2,p=2147483647")),
                new FormatCase("parallelism-int-overflow", phcWithParameters("m=19456,t=2,p=2147483648")));
    }

    private static Stream<FormatCase> base64AndLengthFormats() {
        return Stream.of(
                new FormatCase("salt-one-padding", phcWithParts(SALT + "=", DIGEST)),
                new FormatCase("digest-one-padding", phcWithParts(SALT, DIGEST + "=")),
                new FormatCase("digest-two-padding", phcWithParts(SALT, DIGEST + "==")),
                new FormatCase("salt-empty", phcWithParts("", DIGEST)),
                new FormatCase("digest-empty", phcWithParts(SALT, "")),
                new FormatCase("salt21", phcWithParts("A".repeat(21), DIGEST)),
                new FormatCase("salt23", phcWithParts("A".repeat(23), DIGEST)),
                new FormatCase("salt24", phcWithParts("A".repeat(24), DIGEST)),
                new FormatCase("digest40", phcWithParts(SALT, "A".repeat(40))),
                new FormatCase("digest44", phcWithParts(SALT, "A".repeat(44))),
                new FormatCase("salt-invalid-symbol", phcWithParts("%" + "A".repeat(21), DIGEST)),
                new FormatCase("salt-url-minus", phcWithParts("-" + "A".repeat(21), DIGEST)),
                new FormatCase("salt-non-ascii", phcWithParts("\u00E9" + "A".repeat(21), DIGEST)),
                new FormatCase("salt-tab", phcWithParts("\t" + "A".repeat(21), DIGEST)),
                new FormatCase("salt-crlf", phcWithParts("A".repeat(20) + "\r\n", DIGEST)),
                new FormatCase("digest-invalid-symbol", phcWithParts(SALT, "%" + "A".repeat(42))),
                new FormatCase("digest-url-minus", phcWithParts(SALT, "-" + "A".repeat(42))),
                new FormatCase("digest-url-underscore", phcWithParts(SALT, "_" + "A".repeat(42))),
                new FormatCase("digest-space", phcWithParts(SALT, " " + "A".repeat(42))),
                new FormatCase("digest-newline", phcWithParts(SALT, "\n" + "A".repeat(42))),
                new FormatCase("salt-noncanonical-pad-bits-r", phcWithParts("A".repeat(21) + "R", DIGEST)),
                new FormatCase("digest-noncanonical-pad-bits-f", phcWithParts(SALT, "A".repeat(42) + "F")));
    }

    private static Stream<FormatCase> bcryptFormats() {
        return Stream.of(
                new FormatCase("bcrypt-version2c", bcryptHash("2c", "10", 53)),
                new FormatCase("bcrypt-version-case", bcryptHash("2A", "10", 53)),
                new FormatCase("bcrypt-cost00", bcryptHash("2a", "00", 53)),
                new FormatCase("bcrypt-cost01", bcryptHash("2a", "01", 53)),
                new FormatCase("bcrypt-cost33", bcryptHash("2a", "33", 53)),
                new FormatCase("bcrypt-cost99", bcryptHash("2a", "99", 53)),
                new FormatCase("bcrypt-cost-single-digit", bcryptHash("2a", "4", 53)),
                new FormatCase("bcrypt-cost-three-digits", bcryptHash("2a", "004", 53)),
                new FormatCase("bcrypt-cost-negative", bcryptHash("2a", "-1", 53)),
                new FormatCase("bcrypt-cost-plus", bcryptHash("2a", "+4", 53)),
                new FormatCase("bcrypt-cost-letter", bcryptHash("2a", "A4", 53)),
                new FormatCase("bcrypt-cost-decimal", bcryptHash("2a", "10.0", 53)),
                new FormatCase("bcrypt-cost-non-ascii", bcryptHash("2a", "\uFF10\uFF14", 53)),
                new FormatCase("bcrypt-cost-hostile", bcryptHash("2a", "2147483647", 53)),
                new FormatCase("bcrypt-long-body", bcryptHash("2a", "10", 54)),
                new FormatCase("bcrypt-empty-body", bcryptHash("2a", "10", 0)),
                new FormatCase("bcrypt-body-plus", "$2a$10$" + ".".repeat(52) + "+"),
                new FormatCase("bcrypt-body-underscore", "$2a$10$" + ".".repeat(52) + "_"),
                new FormatCase("bcrypt-body-space", "$2a$10$" + ".".repeat(52) + " "),
                new FormatCase("bcrypt-body-nul", "$2a$10$" + ".".repeat(52) + "\0"),
                new FormatCase("bcrypt-leading-space", " " + bcryptHash("2a", "10", 53)),
                new FormatCase("bcrypt-trailing-space", bcryptHash("2a", "10", 53) + " "),
                new FormatCase("bcrypt-trailing-newline", bcryptHash("2a", "10", 53) + "\n"),
                new FormatCase("bcrypt-suffix-nul", bcryptHash("2a", "10", 53) + "\0"),
                new FormatCase("bcrypt-suffix-character", bcryptHash("2a", "10", 53) + "x"),
                new FormatCase("bcrypt-prefix-noise", "noise" + bcryptHash("2a", "10", 53)),
                new FormatCase("bcrypt-missing-separator", "$2a10$" + ".".repeat(53)),
                new FormatCase("bcrypt-double-separator", "$2a$$10$" + ".".repeat(53)));
    }

    private static String phcWithParameters(String parameters) {
        return "$argon2id$v=19$" + parameters + "$" + SALT + "$" + DIGEST;
    }

    private static String phcWithParts(String salt, String digest) {
        return PREFIX + salt + "$" + digest;
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
