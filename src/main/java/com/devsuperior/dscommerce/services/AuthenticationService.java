package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$ix7qnD5c6oPLj7LyNGM1uewPu.Cw1FJ39KKMo6nEkTOkQ8q7g60bm";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String AUTHENTICATION_PROCESSING_MESSAGE =
            "Authentication could not be completed";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthenticationService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public TokenResponseDTO login(String name, String password) {
        UserAccount userAccount;
        try {
            userAccount = userAccountRepository.findByName(name).orElse(null);
        } catch (RuntimeException cause) {
            throw authenticationProcessingFailure(cause);
        }

        if (userAccount == null) {
            try {
                passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            } catch (RuntimeException cause) {
                throw authenticationProcessingFailure(cause);
            }
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        boolean passwordMatches;
        try {
            passwordMatches = passwordEncoder.matches(
                    password,
                    userAccount.getPasswordHash());
        } catch (RuntimeException cause) {
            throw authenticationProcessingFailure(cause);
        }
        if (!passwordMatches) {
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        try {
            return jwtTokenService.issueToken(userAccount.getId(), userAccount.getName());
        } catch (RuntimeException cause) {
            throw authenticationProcessingFailure(cause);
        }
    }

    private AuthenticationProcessingException authenticationProcessingFailure(
            RuntimeException cause) {
        return new AuthenticationProcessingException(
                AUTHENTICATION_PROCESSING_MESSAGE,
                cause);
    }
}
