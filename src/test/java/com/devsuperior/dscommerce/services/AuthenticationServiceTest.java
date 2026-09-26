package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final String EXACT_NAME = "ExactCaseName";
    private static final String RAW_PASSWORD = "raw-password-value";
    private static final String STORED_HASH = "$2a$10$stored.hash.value.for.authentication.test";

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
}
