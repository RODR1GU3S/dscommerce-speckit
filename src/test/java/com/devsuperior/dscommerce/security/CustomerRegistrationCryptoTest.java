package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.invocation.Invocation;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/** T026 authorship; T027 owns execution. No application context, database or HTTP bootstrap. */
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationCryptoTest {

    private RegistrationPasswordHasher hasher;
    private PasswordEncoder bcrypt;
    private AccountPasswordVerifier verifier;

    @BeforeEach
    void prepareRealCryptographicComponents() {
        hasher = new RegistrationPasswordHasher();
        bcrypt = spy(new SecurityConfig().passwordEncoder());
        verifier = new AccountPasswordVerifier(bcrypt, hasher);
    }

    @Test
    void generatedHashesHaveTheExactApprovedProfileAndIndependentFreshSalts() {
        String first = hasher.hash("registration-profile-009");
        String second = hasher.hash("registration-profile-009");
        byte[] firstSalt = assertProfileAndReadSalt(first);
        byte[] secondSalt = assertProfileAndReadSalt(second);
        assertThat(!Arrays.equals(firstSalt, secondSalt)).as("fresh independent salts").isTrue();
        assertThat(mockingDetails(bcrypt).getInvocations().isEmpty()).as("no BCrypt fallback").isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("roundTripCases")
    void realArgon2RoundTripsPreserveTheExactChosenSequence(RoundTripCase vector) {
        String encoded = hasher.hash(vector.password());
        assertProfileAndReadSalt(encoded);
        assertThat(verifier.matches(vector.password(), encoded)).as("chosen sequence: %s", vector.id()).isTrue();
        assertThat(verifier.matches(vector.different(), encoded)).as("different sequence: %s", vector.id()).isFalse();
        assertThat(mockingDetails(bcrypt).getInvocations().isEmpty()).as("no BCrypt fallback").isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(LegacyCredential.class)
    void fixedLegacyBcryptCredentialsAuthenticateWithoutEncodingUpgradingOrSaving(LegacyCredential credential) {
        String password = credential.password();
        String fixedHash = credential.passwordHash();
        if (credential == LegacyCredential.SHORT) {
            assertThat(password.length()).as("short historical input length").isEqualTo(3);
        } else {
            assertThat(password.getBytes(StandardCharsets.UTF_8).length).as("long historical byte length").isEqualTo(98);
        }
        assertThat(bcrypt instanceof BCryptPasswordEncoder).as("existing configured BCrypt implementation").isTrue();
        var account = new UserAccount(42L, "LegacyExactName", fixedHash);
        byte[] before = fixedHash.getBytes(StandardCharsets.UTF_8);
        var repository = mock(UserAccountRepository.class);
        var jwt = mock(JwtTokenService.class);
        var token = new TokenResponseDTO("local-test-token", "Bearer");
        when(repository.findByName("LegacyExactName")).thenReturn(Optional.of(account));
        when(jwt.issueToken(42L, "LegacyExactName")).thenReturn(token);
        var service = new AuthenticationService(repository, bcrypt, jwt, verifier);

        var returned = service.login("LegacyExactName", password);

        assertThat(returned != null && token.accessToken().equals(returned.accessToken())
                && token.tokenType().equals(returned.tokenType())).as("existing token fields preserved").isTrue();
        assertThat(fixedHash.equals(account.getPasswordHash())).as("stored hash value unchanged").isTrue();
        assertThat(Arrays.equals(before, account.getPasswordHash().getBytes(StandardCharsets.UTF_8)))
                .as("stored hash bytes unchanged").isTrue();
        Invocation lookup = onlyCall(repository, "findByName");
        Invocation matching = onlyCall(bcrypt, "matches");
        Invocation issuance = onlyCall(jwt, "issueToken");
        assertThat("LegacyExactName".equals(lookup.getArguments()[0])).as("exact lookup").isTrue();
        assertThat(matching.getArguments()[0] instanceof CharSequence candidate && password.contentEquals(candidate)
                && fixedHash.equals(matching.getArguments()[1]))
                .as("original password and fixed hash passed unchanged").isTrue();
        assertThat(lookup.getSequenceNumber() < matching.getSequenceNumber()
                && matching.getSequenceNumber() < issuance.getSequenceNumber()).as("lookup, verification, token order").isTrue();
        // onlyCall also excludes encode, upgradeEncoding, save, flush and every other repository operation.
    }

    static Stream<RoundTripCase> roundTripCases() {
        return Stream.of(
                new RoundTripCase("ascii", "registration-009", "Registration-009"),
                new RoundTripCase("outer-spaces", " registration-009 ", "registration-009"),
                new RoundTripCase("nul", "\u0000".repeat(12), "\u0000".repeat(11) + "x"),
                new RoundTripCase("unicode", "Senha-\uD83D\uDE00-\u00E1-009", "Senha-\uD83D\uDE00-A-009"));
    }

    private static byte[] assertProfileAndReadSalt(String encoded) {
        assertThat(encoded != null).as("generated PHC present").isTrue();
        assertThat(encoded.length()).as("whole PHC length").isEqualTo(97);
        String[] parts = encoded.split("\\$", -1);
        assertThat(parts.length).as("whole PHC segments").isEqualTo(6);
        assertThat(parts[0].isEmpty() && parts[1].equals("argon2id") && parts[2].equals("v=19")
                && parts[3].equals("m=19456,t=2,p=1")).as("literal approved algorithm and parameters").isTrue();
        assertThat(parts[4].length()).as("salt Base64 length").isEqualTo(22);
        assertThat(parts[5].length()).as("digest Base64 length").isEqualTo(43);
        byte[] salt = Base64.getDecoder().decode(parts[4]);
        byte[] digest = Base64.getDecoder().decode(parts[5]);
        assertThat(salt.length).as("salt byte length").isEqualTo(16);
        assertThat(digest.length).as("digest byte length").isEqualTo(32);
        assertThat(Base64.getEncoder().withoutPadding().encodeToString(salt).equals(parts[4])
                && Base64.getEncoder().withoutPadding().encodeToString(digest).equals(parts[5]))
                .as("canonical standard Base64 without padding").isTrue();
        return salt;
    }

    private static Invocation onlyCall(Object mock, String method) {
        var calls = new ArrayList<>(mockingDetails(mock).getInvocations());
        assertThat(calls.size()).as("only permitted boundary call: %s", method).isEqualTo(1);
        Invocation call = calls.getFirst();
        assertThat(call.getMethod().getName()).as("permitted method name").isEqualTo(method);
        return call;
    }

    private record RoundTripCase(String id, String password, String different) {
        @Override public String toString() { return id; }
    }
}
