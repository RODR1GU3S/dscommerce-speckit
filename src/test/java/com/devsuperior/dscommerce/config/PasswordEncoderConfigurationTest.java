package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderConfigurationTest {

    private static final String VALID_256_BIT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(DscommerceApplication.class)
                    .withPropertyValues(
                            "security.jwt.secret-base64=" + VALID_256_BIT_SECRET,
                            "security.jwt.ttl=PT15M");

    @Test
    void shouldConfigureBcryptPasswordEncoderThatMatchesRawAndEncodedPasswords() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PasswordEncoder.class);

            var passwordEncoder = context.getBean(PasswordEncoder.class);
            var rawPassword = "configuration-test-password";
            var encodedPassword = passwordEncoder.encode(rawPassword);

            assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
            assertThat(encodedPassword).isNotEqualTo(rawPassword);
            assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
        });
    }
}
