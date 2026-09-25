package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigurationTest {

    private static final String VALID_256_BIT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(DscommerceApplication.class);

    @Test
    void shouldRejectInvalidBase64Secret() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret-base64=not-valid-base64***",
                        "security.jwt.ttl=PT15M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectSecretShorterThan256BitsAfterBase64Decoding() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret-base64=c2hvcnQ=",
                        "security.jwt.ttl=PT15M")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectZeroTtl() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret-base64=" + VALID_256_BIT_SECRET,
                        "security.jwt.ttl=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldRejectNegativeTtl() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret-base64=" + VALID_256_BIT_SECRET,
                        "security.jwt.ttl=-PT1S")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldConfigureWorkingHs256EncoderAndDecoder() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret-base64=" + VALID_256_BIT_SECRET,
                        "security.jwt.ttl=PT15M")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtEncoder.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);

                    var issuedAt = Instant.now();
                    var claims = JwtClaimsSet.builder()
                            .subject("configuration-test")
                            .issuedAt(issuedAt)
                            .expiresAt(issuedAt.plusSeconds(900))
                            .build();
                    var headers = JwsHeader.with(MacAlgorithm.HS256).build();

                    var token = context.getBean(JwtEncoder.class)
                            .encode(JwtEncoderParameters.from(headers, claims))
                            .getTokenValue();
                    var decoded = context.getBean(JwtDecoder.class).decode(token);

                    assertThat(token).isNotBlank();
                    assertThat(decoded.getSubject()).isEqualTo("configuration-test");
                    assertThat(decoded.getHeaders().get("alg")).hasToString("HS256");
                });
    }
}
