package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationInternalFailureTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final String EXACT_NAME = "ExactCaseName";
    private static final String RAW_PASSWORD = "raw-password-sensitive-sentinel";
    private static final String STORED_HASH = "$2a$10$stored-hash-sensitive-sentinel";
    private static final String GENERIC_PROCESSING_MESSAGE =
            "Authentication could not be completed";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void loginShouldConvertUnexpectedRepositoryFailureWithoutCallingLaterStages() {
        RuntimeException originalCause = new RuntimeException(
                "repository-sensitive-detail-sentinel");
        when(userAccountRepository.findByName(EXACT_NAME)).thenThrow(originalCause);

        Throwable failure = catchThrowable(
                () -> authenticationService.login(EXACT_NAME, RAW_PASSWORD));

        assertSafeProcessingFailure(failure, originalCause);
        verify(userAccountRepository, times(1)).findByName(EXACT_NAME);
        verifyNoInteractions(passwordEncoder, jwtTokenService);
        verifyNoMoreInteractions(userAccountRepository);
    }

    @Test
    void loginShouldConvertUnexpectedPasswordEncoderFailureWithoutIssuingToken() {
        var account = new UserAccount(ACCOUNT_ID, EXACT_NAME, STORED_HASH);
        RuntimeException originalCause = new RuntimeException(
                "encoder failure exposed " + RAW_PASSWORD + " and " + STORED_HASH);
        when(userAccountRepository.findByName(EXACT_NAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_HASH)).thenThrow(originalCause);

        Throwable failure = catchThrowable(
                () -> authenticationService.login(EXACT_NAME, RAW_PASSWORD));

        assertSafeProcessingFailure(failure, originalCause);
        verify(userAccountRepository, times(1)).findByName(EXACT_NAME);
        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, STORED_HASH);
        verifyNoInteractions(jwtTokenService);
        verifyNoMoreInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void loginShouldConvertUnexpectedTokenGenerationFailureWithoutReturningSuccess() {
        var account = new UserAccount(ACCOUNT_ID, EXACT_NAME, STORED_HASH);
        RuntimeException originalCause = new RuntimeException(
                "token-sensitive-sentinel secret-key-sentinel jwt-key-sentinel "
                        + RAW_PASSWORD + " " + STORED_HASH);
        when(userAccountRepository.findByName(EXACT_NAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtTokenService.issueToken(ACCOUNT_ID, EXACT_NAME)).thenThrow(originalCause);

        Throwable failure = catchThrowable(
                () -> authenticationService.login(EXACT_NAME, RAW_PASSWORD));

        assertSafeProcessingFailure(failure, originalCause);
        verify(userAccountRepository, times(1)).findByName(EXACT_NAME);
        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, STORED_HASH);
        verify(jwtTokenService, times(1)).issueToken(ACCOUNT_ID, EXACT_NAME);
        verifyNoMoreInteractions(userAccountRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void loginShouldKeepInvalidCredentialsDistinctFromInternalProcessingFailure() {
        var account = new UserAccount(ACCOUNT_ID, EXACT_NAME, STORED_HASH);
        when(userAccountRepository.findByName(EXACT_NAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(false);

        Throwable failure = catchThrowable(
                () -> authenticationService.login(EXACT_NAME, RAW_PASSWORD));

        assertThat(failure)
                .isExactlyInstanceOf(InvalidCredentialsException.class)
                .isNotInstanceOf(AuthenticationProcessingException.class)
                .hasMessage("Invalid credentials");
        verifyNoInteractions(jwtTokenService);
    }

    private static void assertSafeProcessingFailure(
            Throwable failure,
            RuntimeException originalCause) {
        assertThat(failure)
                .isExactlyInstanceOf(AuthenticationProcessingException.class)
                .hasMessage(GENERIC_PROCESSING_MESSAGE);
        assertThat(failure.getCause()).isSameAs(originalCause);

        String exposedMessage = failure.getMessage().toLowerCase(Locale.ROOT);
        assertThat(exposedMessage)
                .doesNotContain(
                        originalCause.getMessage().toLowerCase(Locale.ROOT),
                        "repository-sensitive-detail-sentinel",
                        RAW_PASSWORD.toLowerCase(Locale.ROOT),
                        STORED_HASH.toLowerCase(Locale.ROOT),
                        "token-sensitive-sentinel",
                        "secret-key-sentinel",
                        "jwt-key-sentinel",
                        "passwordhash",
                        "bcrypt",
                        "$2a$");
    }
}
