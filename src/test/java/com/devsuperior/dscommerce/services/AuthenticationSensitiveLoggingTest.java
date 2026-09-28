package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AuthenticationSensitiveLoggingTest {

    private static final Long ACCOUNT_ID = 48L;
    private static final String EXISTING_NAME = "LoggingTestAccount";
    private static final String UNKNOWN_NAME = "UnknownLoggingTestAccount";
    private static final String RAW_PASSWORD =
            "RAW_PASSWORD_LOG_SENTINEL_48_ONLY_FOR_TESTS";
    private static final String STORED_BCRYPT_HASH =
            "$2a$10$ix7qnD5c6oPLj7LyNGM1uewPu.Cw1FJ39KKMo6nEkTOkQ8q7g60bm";
    private static final String JWT_SECRET_BASE64 =
            "TE9HR0lOR19URVNUX0pXVF9TRUNSRVRfQkFTRTY0X1NFTlRJTkVM";
    private static final String DECODED_KEY_MATERIAL =
            "LOGGING_TEST_DECODED_KEY_MATERIAL_SENTINEL_48";
    private static final String ACCESS_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.LOGGING_TEST_TOKEN_SENTINEL_48.signature";
    private static final String TOKEN_PAYLOAD_SENTINEL =
            "LOGGING_TEST_TOKEN_SENTINEL_48";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void validLoginShouldNotLogSensitiveAuthenticationData(CapturedOutput output) {
        var account = account();
        var expectedResponse = new TokenResponseDTO(ACCESS_TOKEN, "Bearer");
        when(userAccountRepository.findByName(EXISTING_NAME))
                .thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_BCRYPT_HASH))
                .thenReturn(true);
        when(jwtTokenService.issueToken(ACCOUNT_ID, EXISTING_NAME))
                .thenReturn(expectedResponse);

        TokenResponseDTO result = authenticationService.login(
                EXISTING_NAME,
                RAW_PASSWORD);

        assertThat(result).isSameAs(expectedResponse);
        assertNoSensitiveOutput(output);
    }

    @Test
    void unknownNameShouldNotLogSensitiveAuthenticationData(CapturedOutput output) {
        when(userAccountRepository.findByName(UNKNOWN_NAME))
                .thenReturn(Optional.empty());

        catchThrowable(() -> authenticationService.login(UNKNOWN_NAME, RAW_PASSWORD));

        assertNoSensitiveOutput(output);
    }

    @Test
    void wrongPasswordShouldNotLogSensitiveAuthenticationData(CapturedOutput output) {
        when(userAccountRepository.findByName(EXISTING_NAME))
                .thenReturn(Optional.of(account()));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_BCRYPT_HASH))
                .thenReturn(false);

        catchThrowable(() -> authenticationService.login(EXISTING_NAME, RAW_PASSWORD));

        assertNoSensitiveOutput(output);
    }

    @Test
    void internalFailureShouldNotLogSensitiveAuthenticationData(CapturedOutput output) {
        RuntimeException internalFailure = new RuntimeException(
                "internal-failure-sentinel "
                        + RAW_PASSWORD + " "
                        + STORED_BCRYPT_HASH + " "
                        + JWT_SECRET_BASE64 + " "
                        + DECODED_KEY_MATERIAL + " "
                        + ACCESS_TOKEN);
        when(userAccountRepository.findByName(EXISTING_NAME))
                .thenThrow(internalFailure);

        catchThrowable(() -> authenticationService.login(EXISTING_NAME, RAW_PASSWORD));

        assertNoSensitiveOutput(output);
    }

    private static UserAccount account() {
        return new UserAccount(ACCOUNT_ID, EXISTING_NAME, STORED_BCRYPT_HASH);
    }

    private static void assertNoSensitiveOutput(CapturedOutput output) {
        String capturedOutput = output.getAll().toLowerCase(Locale.ROOT);

        assertThat(capturedOutput)
                .doesNotContain(
                        RAW_PASSWORD.toLowerCase(Locale.ROOT),
                        STORED_BCRYPT_HASH.toLowerCase(Locale.ROOT),
                        JWT_SECRET_BASE64.toLowerCase(Locale.ROOT),
                        DECODED_KEY_MATERIAL.toLowerCase(Locale.ROOT),
                        ACCESS_TOKEN.toLowerCase(Locale.ROOT),
                        TOKEN_PAYLOAD_SENTINEL.toLowerCase(Locale.ROOT),
                        "internal-failure-sentinel");
    }
}
