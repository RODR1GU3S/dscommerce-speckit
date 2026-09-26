package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.config.JwtProperties;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE = "Bearer";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public TokenResponseDTO issueToken(Long accountId, String name) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.getTtl());
        var headers = JwsHeader.with(MacAlgorithm.HS256).build();
        var claims = JwtClaimsSet.builder()
                .subject(accountId.toString())
                .claim("name", name)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(headers, claims))
                .getTokenValue();

        return new TokenResponseDTO(accessToken, TOKEN_TYPE);
    }
}
