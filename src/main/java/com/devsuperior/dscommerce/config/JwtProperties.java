package com.devsuperior.dscommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Base64;

@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Duration MINIMUM_TTL = Duration.ofSeconds(1);

    private byte[] secret;
    private Duration ttl;

    public byte[] getSecret() {
        return secret.clone();
    }

    public void setSecretBase64(String secretBase64) {
        final byte[] decodedSecret;

        try {
            decodedSecret = Base64.getDecoder().decode(secretBase64);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("security.jwt.secret-base64 must be valid Base64", exception);
        }

        if (decodedSecret.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "security.jwt.secret-base64 must decode to at least 32 bytes");
        }

        this.secret = decodedSecret;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        if (ttl == null || ttl.compareTo(MINIMUM_TTL) < 0) {
            throw new IllegalArgumentException("security.jwt.ttl must be at least 1 second");
        }

        this.ttl = ttl;
    }
}
