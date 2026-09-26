package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

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
        UserAccount userAccount = userAccountRepository.findByName(name)
                .orElseThrow(() -> new UnsupportedOperationException(
                        "Invalid credentials handling is not implemented yet"));

        boolean passwordMatches = passwordEncoder.matches(
                password,
                userAccount.getPasswordHash());
        if (!passwordMatches) {
            throw new UnsupportedOperationException(
                    "Invalid credentials handling is not implemented yet");
        }

        return jwtTokenService.issueToken(userAccount.getId(), userAccount.getName());
    }
}
