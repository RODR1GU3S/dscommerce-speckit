package com.devsuperior.dscommerce.security;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.JwtTokenService;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
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
import java.util.HexFormat;
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

    /** T059: algorithm-only known answer, not the registration profile or an HTTP/Spring context test. */
    @Test
    void bouncyCastleMatchesThePublishedRfc9106Argon2idVector() {
        // RFC 9106 section 5.3: https://www.rfc-editor.org/rfc/rfc9106.html#section-5.3
        // These published binary inputs and costs belong only to the algorithm vector.
        byte[] password = HexFormat.of().parseHex("01".repeat(32));
        var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(0x13).withMemoryAsKB(32).withIterations(3).withParallelism(4)
                .withSalt(HexFormat.of().parseHex("02".repeat(16)))
                .withSecret(HexFormat.of().parseHex("03".repeat(8)))
                .withAdditional(HexFormat.of().parseHex("04".repeat(12))).build();
        byte[] expected = HexFormat.of().parseHex(
                "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659");

        byte[] actual = deriveBouncyCastleTag(password, parameters);

        assertThat(Arrays.equals(expected, actual)).as("published RFC 9106 Argon2id tag").isTrue();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("wrapperByteSequences")
    void registrationWrapperUsesTheApprovedProfileAndLiteralUtf8Bytes(WrapperCase vector) {
        assertInputUnits(vector.id(), vector.password(), vector.points(), vector.bytes());
        byte[] expectedUtf8 = HexFormat.of().parseHex(vector.utf8Hex());
        assertThat(expectedUtf8.length == vector.bytes()
                && Arrays.equals(expectedUtf8, vector.password().getBytes(StandardCharsets.UTF_8)))
                .as("independent literal UTF-8 fixture without added BOM: %s", vector.id()).isTrue();

        String encoded = hasher.hash(vector.password());
        byte[] salt = assertProfileAndReadSalt(encoded);
        // Independent adapter check: explicit approved costs and literal input bytes, not product constants.
        var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(0x13).withMemoryAsKB(19456).withIterations(2).withParallelism(1)
                .withSalt(salt).build();
        byte[] expectedDigest = deriveBouncyCastleTag(expectedUtf8, parameters);
        byte[] actualDigest = Base64.getDecoder().decode(encoded.split("\\$", -1)[5]);
        assertThat(Arrays.equals(expectedDigest, actualDigest))
                .as("wrapper encodes the exact fixture bytes at the approved profile: %s", vector.id()).isTrue();
        assertThat(verifier.matches(vector.password(), encoded)).as("wrapper verification: %s", vector.id()).isTrue();
        assertThat(mockingDetails(bcrypt).getInvocations().isEmpty()).as("no BCrypt fallback").isTrue();
    }

    static Stream<WrapperCase> wrapperByteSequences() {
        return Stream.of(
                new WrapperCase("ascii12", "abcdefghijkl", 12, 12, "6162636465666768696a6b6c"),
                new WrapperCase("ascii64", "a".repeat(64), 64, 64, "61".repeat(64)),
                new WrapperCase("acute32-bytes64", "\u00E1".repeat(32), 32, 64, "c3a1".repeat(32)),
                new WrapperCase("emoji12-bytes48", "\uD83D\uDE00".repeat(12), 12, 48, "f09f9880".repeat(12)),
                new WrapperCase("emoji16-bytes64", "\uD83D\uDE00".repeat(16), 16, 64, "f09f9880".repeat(16)),
                new WrapperCase("outer-spaces", " abcdefghijkl ", 14, 14, "206162636465666768696a6b6c20"),
                new WrapperCase("composed", "\u00E9".repeat(12), 12, 24, "c3a9".repeat(12)),
                new WrapperCase("decomposed", "e\u0301".repeat(12), 24, 36, "65cc81".repeat(12)),
                new WrapperCase("nul12", "\0".repeat(12), 12, 12, "00".repeat(12)),
                new WrapperCase("control1c12", "\u001C".repeat(12), 12, 12, "1c".repeat(12)));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("exactNulPairs")
    void pQAndPPlusNulAuthenticateOnlyTheirOwnHashesInBothDirections(ExactPair pair) {
        assertInputUnits(pair.id() + "-first", pair.first(), pair.firstPoints(), pair.firstBytes());
        assertInputUnits(pair.id() + "-second", pair.second(), pair.secondPoints(), pair.secondBytes());
        String firstHash = hasher.hash(pair.first());
        String secondHash = hasher.hash(pair.second());
        assertProfileAndReadSalt(firstHash);
        assertProfileAndReadSalt(secondHash);

        assertThat(verifier.matches(pair.first(), firstHash)).as("own first sequence: %s", pair.id()).isTrue();
        assertThat(verifier.matches(pair.second(), firstHash)).as("reject second against first: %s", pair.id()).isFalse();
        assertThat(verifier.matches(pair.second(), secondHash)).as("own second sequence: %s", pair.id()).isTrue();
        assertThat(verifier.matches(pair.first(), secondHash)).as("reject first against second: %s", pair.id()).isFalse();
        assertThat(mockingDetails(bcrypt).getInvocations().isEmpty()).as("no BCrypt fallback").isTrue();
        // No equality/inequality requirement on independently salted PHC strings.
    }

    static Stream<ExactPair> exactNulPairs() {
        String p = "abcdefghijkl";
        String q = p + "\0" + p;
        String pPlusNul = p + "\0";
        return Stream.of(
                new ExactPair("p-q", p, q, 12, 12, 25, 25),
                new ExactPair("p-p-plus-nul", p, pPlusNul, 12, 12, 13, 13),
                new ExactPair("q-p-plus-nul", q, pPlusNul, 25, 25, 13, 13));
    }

    private static byte[] deriveBouncyCastleTag(byte[] password, Argon2Parameters parameters) {
        var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] tag = new byte[32];
        assertThat(generator.generateBytes(password, tag)).as("complete Argon2 tag length").isEqualTo(32);
        return tag;
    }

    private static void assertInputUnits(String id, String password, int points, int bytes) {
        assertThat(password.codePointCount(0, password.length())).as("literal code points: %s", id).isEqualTo(points);
        assertThat(password.getBytes(StandardCharsets.UTF_8).length).as("literal UTF-8 bytes: %s", id).isEqualTo(bytes);
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

    private record WrapperCase(String id, String password, int points, int bytes, String utf8Hex) {
        @Override public String toString() { return id; }
    }

    private record ExactPair(String id, String first, String second,
                             int firstPoints, int firstBytes, int secondPoints, int secondBytes) {
        @Override public String toString() { return id; }
    }
}
