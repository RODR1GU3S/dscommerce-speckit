package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class LoginControllerTest {

    private static final String EXACT_NAME = "  Demo.User  ";
    private static final String EXACT_PASSWORD = "  S3cret-Pass  ";
    private static final String ACCESS_TOKEN = "signed.jwt.token";
    private static final String UNKNOWN_NAME = "UnknownUser";
    private static final String WRONG_PASSWORD = "wrong-password-value";
    private static final String VALID_NAME = "ValidName";
    private static final String VALID_PASSWORD = "valid-password-value";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationService authenticationService;

    @Test
    void postLoginShouldAuthenticateExactCredentialsAndReturnOnlyBearerToken() throws Exception {
        when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD))
                .thenReturn(new TokenResponseDTO(ACCESS_TOKEN, "Bearer"));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"  Demo.User  ","password":"  S3cret-Pass  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("accessToken", "tokenType")))
                .andExpect(jsonPath("$.accessToken").value(ACCESS_TOKEN))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.jwtSecret").doesNotExist())
                .andExpect(jsonPath("$.secretKey").doesNotExist())
                .andExpect(jsonPath("$.signingKey").doesNotExist())
                .andExpect(jsonPath("$.key").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.permissions").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        verify(authenticationService, times(1)).login(EXACT_NAME, EXACT_PASSWORD);
        verifyNoMoreInteractions(authenticationService);
    }

    @Test
    void postLoginShouldReturnIdenticalGenericUnauthorizedResponsesForInvalidCredentials()
            throws Exception {
        when(authenticationService.login(UNKNOWN_NAME, VALID_PASSWORD))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));
        when(authenticationService.login(VALID_NAME, WRONG_PASSWORD))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        MvcResult unknownNameResult = performUnauthorizedLogin(
                UNKNOWN_NAME,
                VALID_PASSWORD);
        MvcResult wrongPasswordResult = performUnauthorizedLogin(
                VALID_NAME,
                WRONG_PASSWORD);

        assertThat(unknownNameResult.getResponse().getContentAsString())
                .isEqualTo(wrongPasswordResult.getResponse().getContentAsString());
        assertConfidentialErrorBody(unknownNameResult, UNKNOWN_NAME, VALID_PASSWORD);
        assertConfidentialErrorBody(wrongPasswordResult, VALID_NAME, WRONG_PASSWORD);

        verify(authenticationService, times(1)).login(UNKNOWN_NAME, VALID_PASSWORD);
        verify(authenticationService, times(1)).login(VALID_NAME, WRONG_PASSWORD);
        verifyNoMoreInteractions(authenticationService);
    }

    @ParameterizedTest(name = "invalid name: {0}")
    @MethodSource("invalidNameRequests")
    void postLoginShouldRejectInvalidNameWithoutCallingAuthenticationService(
            String scenario,
            String requestBody) throws Exception {
        performBadRequest(requestBody, VALID_PASSWORD);

        verifyNoInteractions(authenticationService);
    }

    @ParameterizedTest(name = "invalid password: {0}")
    @MethodSource("invalidPasswordRequests")
    void postLoginShouldRejectInvalidPasswordWithoutCallingAuthenticationService(
            String scenario,
            String requestBody) throws Exception {
        performBadRequest(requestBody, null);

        verifyNoInteractions(authenticationService);
    }

    @Test
    void postLoginShouldRejectNumericNameWithoutCallingAuthenticationService()
            throws Exception {
        String requestBody = """
                {
                  "name": 123,
                  "password": "secret123"
                }
                """;

        performBadRequest(requestBody, "secret123");

        verifyNoInteractions(authenticationService);
    }

    @Test
    void postLoginShouldRejectBooleanPasswordWithoutCallingAuthenticationService()
            throws Exception {
        String requestBody = """
                {
                  "name": "demo",
                  "password": true
                }
                """;

        performBadRequest(requestBody, "true");

        verifyNoInteractions(authenticationService);
    }

    @Test
    void postLoginShouldRejectUnknownJsonPropertyWithoutCallingAuthenticationService()
            throws Exception {
        String requestBody = """
                {
                  "name":"ValidName",
                  "password":"valid-password-value",
                  "unexpectedField":"must-be-rejected"
                }
                """;

        performBadRequest(requestBody, VALID_PASSWORD);

        verifyNoInteractions(authenticationService);
    }

    private MvcResult performUnauthorizedLogin(String name, String password) throws Exception {
        return mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","password":"%s"}
                                """.formatted(name, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "status",
                        "error",
                        "message",
                        "path")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid credentials"))
                .andExpect(jsonPath("$.path").value("/login"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andReturn();
    }

    private void performBadRequest(String requestBody, String rawPassword) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(
                        "status",
                        "error",
                        "message",
                        "path")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid login data"))
                .andExpect(jsonPath("$.path").value("/login"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").doesNotExist())
                .andReturn();

        assertConfidentialErrorBody(result, rawPassword);
    }

    private static void assertConfidentialErrorBody(MvcResult result, String... submittedValues)
            throws Exception {
        String responseBody = result.getResponse()
                .getContentAsString()
                .toLowerCase(Locale.ROOT);

        assertThat(responseBody)
                .doesNotContain(
                        "accesstoken",
                        "tokentype",
                        "password",
                        "passwordhash",
                        "secret",
                        "jwtkey",
                        "jwt key",
                        "secretkey",
                        "signingkey",
                        "key material",
                        "bcrypt",
                        "$2a$",
                        "$2b$",
                        "$2y$");
        for (String submittedValue : submittedValues) {
            if (submittedValue != null && !submittedValue.isBlank()) {
                assertThat(responseBody)
                        .doesNotContain(submittedValue.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static Stream<Arguments> invalidNameRequests() {
        return Stream.of(
                Arguments.of("missing", """
                        {"password":"valid-password-value"}
                        """),
                Arguments.of("null", """
                        {"name":null,"password":"valid-password-value"}
                        """),
                Arguments.of("empty", """
                        {"name":"","password":"valid-password-value"}
                        """),
                Arguments.of("whitespace only", """
                        {"name":"   ","password":"valid-password-value"}
                        """));
    }

    private static Stream<Arguments> invalidPasswordRequests() {
        return Stream.of(
                Arguments.of("missing", """
                        {"name":"ValidName"}
                        """),
                Arguments.of("null", """
                        {"name":"ValidName","password":null}
                        """),
                Arguments.of("empty", """
                        {"name":"ValidName","password":""}
                        """),
                Arguments.of("whitespace only", """
                        {"name":"ValidName","password":"   "}
                        """));
    }
}
