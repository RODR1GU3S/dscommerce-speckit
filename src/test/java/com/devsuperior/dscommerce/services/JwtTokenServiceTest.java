package com.devsuperior.dscommerce.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JwtTokenServiceTest {

    private static final String SERVICE_BEAN_NAME = "jwtTokenService";
    private static final Long ACCOUNT_ID = 42L;
    private static final String ACCOUNT_NAME = "ExactCaseName";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Value("${security.jwt.ttl}")
    private Duration configuredTtl;

    @Value("${security.jwt.secret-base64}")
    private String configuredSecretBase64;

    private Object serviceBean;
    private Method tokenIssuanceMethod;

    @BeforeEach
    void discoverTokenServiceThroughSpringContext() {
        assertThat(applicationContext.containsBean(SERVICE_BEAN_NAME))
                .as("Spring context must expose the JwtTokenService bean")
                .isTrue();

        serviceBean = applicationContext.getBean(SERVICE_BEAN_NAME);
        List<Method> candidates = Arrays.stream(serviceBean.getClass().getMethods())
                .filter(method -> method.getReturnType() != Void.TYPE)
                .filter(method -> Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[]{Long.class, String.class}))
                .toList();

        assertThat(candidates)
                .as("JwtTokenService must expose one token-issuance operation for id and exact name")
                .hasSize(1);
        tokenIssuanceMethod = candidates.getFirst();
    }

    @Test
    void shouldIssueNonEmptyVerifiableHs256TokenWithRequiredSafeClaims() {
        Object tokenResponse = invokeTokenIssuance();
        String accessToken = readStringProperty(tokenResponse, "accessToken");
        String tokenType = readStringProperty(tokenResponse, "tokenType");

        assertThat(accessToken).isNotBlank();
        assertThat(tokenType).isEqualTo("Bearer");

        Jwt decoded = jwtDecoder.decode(accessToken);

        assertThat(decoded.getHeaders().get("alg")).hasToString("HS256");
        assertThat(decoded.getSubject()).isEqualTo(ACCOUNT_ID.toString());
        assertThat(decoded.getClaimAsString("name")).isEqualTo(ACCOUNT_NAME);
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isNotNull();
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()))
                .isEqualTo(configuredTtl);

        assertNoSensitiveClaims(decoded.getClaims(), accessToken);
    }

    private Object invokeTokenIssuance() {
        try {
            return tokenIssuanceMethod.invoke(serviceBean, ACCOUNT_ID, ACCOUNT_NAME);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Token issuance must complete successfully", exception.getCause());
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Token issuance operation must be callable", exception);
        }
    }

    private static String readStringProperty(Object target, String propertyName) {
        assertThat(target).as("Token response must not be null").isNotNull();

        Method accessor = Arrays.stream(target.getClass().getMethods())
                .filter(method -> method.getParameterCount() == 0)
                .filter(method -> method.getReturnType().equals(String.class))
                .filter(method -> method.getName().equals(propertyName)
                        || method.getName().equals("get" + capitalize(propertyName)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Token response must expose " + propertyName));

        try {
            return (String) accessor.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to read token response property " + propertyName, exception);
        }
    }

    private void assertNoSensitiveClaims(Map<String, Object> claims, String accessToken) {
        assertThat(claims.keySet())
                .allSatisfy(claimName -> assertThat(claimName.toLowerCase(Locale.ROOT))
                        .doesNotContain("password", "passwordhash", "secret"));

        String claimValues = claims.values().toString();
        String decodedSecret = new String(
                Base64.getDecoder().decode(configuredSecretBase64),
                StandardCharsets.UTF_8);

        assertThat(claimValues)
                .doesNotContain(configuredSecretBase64)
                .doesNotContain(decodedSecret);
        assertThat(accessToken)
                .doesNotContain(configuredSecretBase64)
                .doesNotContain(decodedSecret);
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
