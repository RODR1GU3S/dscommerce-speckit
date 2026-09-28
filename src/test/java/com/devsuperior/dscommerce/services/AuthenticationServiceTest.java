package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final String EXACT_NAME = "ExactCaseName";
    private static final String UNKNOWN_NAME = "UnknownExactName";
    private static final String RAW_PASSWORD = "raw-password-value";
    private static final String DUMMY_HASH_TEST_VALUE = "secret123";
    private static final String STORED_HASH = "$2a$10$stored.hash.value.for.authentication.test";
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$ix7qnD5c6oPLj7LyNGM1uewPu.Cw1FJ39KKMo6nEkTOkQ8q7g60bm";
    private static final String GENERIC_INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void loginShouldAuthenticateWithOneExactLookupAndBcryptBeforeIssuingToken() {
        var account = new UserAccount(ACCOUNT_ID, EXACT_NAME, STORED_HASH);
        var expectedResponse = new TokenResponseDTO("signed-access-token", "Bearer");
        when(userAccountRepository.findByName(EXACT_NAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(true);
        when(jwtTokenService.issueToken(ACCOUNT_ID, EXACT_NAME)).thenReturn(expectedResponse);

        TokenResponseDTO result = authenticationService.login(EXACT_NAME, RAW_PASSWORD);

        assertThat(result).isSameAs(expectedResponse);
        assertThat(account.getPasswordHash()).isEqualTo(STORED_HASH);

        InOrder authenticationOrder = inOrder(
                userAccountRepository,
                passwordEncoder,
                jwtTokenService);
        authenticationOrder.verify(userAccountRepository, times(1)).findByName(EXACT_NAME);
        authenticationOrder.verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, STORED_HASH);
        authenticationOrder.verify(jwtTokenService, times(1)).issueToken(ACCOUNT_ID, EXACT_NAME);

        verify(userAccountRepository, never()).save(any(UserAccount.class));
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoMoreInteractions(userAccountRepository, passwordEncoder, jwtTokenService);
    }

    @Test
    void loginShouldUsePrecomputedDummyBcryptAndRejectUnknownNameWithoutIssuingToken() {
        when(userAccountRepository.findByName(UNKNOWN_NAME)).thenReturn(Optional.empty());
        when(passwordEncoder.matches(RAW_PASSWORD, DUMMY_BCRYPT_HASH)).thenReturn(false);

        Throwable failure = catchThrowable(
                () -> authenticationService.login(UNKNOWN_NAME, RAW_PASSWORD));

        assertThat(failure)
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(GENERIC_INVALID_CREDENTIALS_MESSAGE);
        assertGenericInvalidCredentialsMessage(failure);
        assertThat(DUMMY_BCRYPT_HASH)
                .matches("^\\$2[aby]\\$10\\$[./A-Za-z0-9]{53}$");
        assertThat(new BCryptPasswordEncoder().matches(
                DUMMY_HASH_TEST_VALUE,
                DUMMY_BCRYPT_HASH)).isTrue();

        verify(userAccountRepository, times(1)).findByName(UNKNOWN_NAME);
        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, DUMMY_BCRYPT_HASH);
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(jwtTokenService);
        verifyNoMoreInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void loginShouldRejectWrongPasswordWithoutIssuingToken() {
        var account = new UserAccount(ACCOUNT_ID, EXACT_NAME, STORED_HASH);
        when(userAccountRepository.findByName(EXACT_NAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(false);

        Throwable failure = catchThrowable(
                () -> authenticationService.login(EXACT_NAME, RAW_PASSWORD));

        assertThat(failure)
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage(GENERIC_INVALID_CREDENTIALS_MESSAGE);
        assertGenericInvalidCredentialsMessage(failure);

        verify(userAccountRepository, times(1)).findByName(EXACT_NAME);
        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, STORED_HASH);
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(jwtTokenService);
        verifyNoMoreInteractions(userAccountRepository, passwordEncoder);
    }

    @Test
    void loginShouldProduceUniformFailureForUnknownNameAndWrongPassword() {
        var account = new UserAccount(ACCOUNT_ID, EXACT_NAME, STORED_HASH);
        when(userAccountRepository.findByName(UNKNOWN_NAME)).thenReturn(Optional.empty());
        when(userAccountRepository.findByName(EXACT_NAME)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(RAW_PASSWORD, DUMMY_BCRYPT_HASH)).thenReturn(false);
        when(passwordEncoder.matches(RAW_PASSWORD, STORED_HASH)).thenReturn(false);

        Throwable unknownNameFailure = catchThrowable(
                () -> authenticationService.login(UNKNOWN_NAME, RAW_PASSWORD));
        Throwable wrongPasswordFailure = catchThrowable(
                () -> authenticationService.login(EXACT_NAME, RAW_PASSWORD));

        assertThat(unknownNameFailure.getClass())
                .isEqualTo(InvalidCredentialsException.class)
                .isEqualTo(wrongPasswordFailure.getClass());
        assertThat(unknownNameFailure.getMessage())
                .isEqualTo(GENERIC_INVALID_CREDENTIALS_MESSAGE)
                .isEqualTo(wrongPasswordFailure.getMessage());
        assertGenericInvalidCredentialsMessage(unknownNameFailure);
        assertGenericInvalidCredentialsMessage(wrongPasswordFailure);

        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, DUMMY_BCRYPT_HASH);
        verify(passwordEncoder, times(1)).matches(RAW_PASSWORD, STORED_HASH);
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(jwtTokenService);
    }

    private static void assertGenericInvalidCredentialsMessage(Throwable failure) {
        assertThat(failure.getMessage().toLowerCase())
                .doesNotContain(
                        "unknown",
                        "user",
                        "name",
                        "password",
                        "incorrect",
                        "wrong");
    }
}
