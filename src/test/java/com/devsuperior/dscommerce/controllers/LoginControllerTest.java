package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.SecurityConfig;
import com.devsuperior.dscommerce.config.OrderClockConfig;
import com.devsuperior.dscommerce.config.OrderSecuritySliceTestConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = LoginController.class, properties = "test.order-security.public-mvc-slice=true")
@Import({SecurityConfig.class, OrderClockConfig.class, OrderSecuritySliceTestConfig.class})
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

    @Autowired private ObjectMapper mapper;
    @Autowired private FilterChainProxy filters;

    private static final String INVALID_BEARER = "Bearer not-a-valid-jwt";
    private static final String INTERNAL_DETAIL = "internal-detail-canary";

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

    @ParameterizedTest(name = "exact login HTTP contract, invalid Bearer={0}")
    @MethodSource("bearerModes")
    void shouldPreserveExactCredentialsAndTokenResponseWithOrWithoutIrrelevantBearer(boolean invalidBearer)
            throws Exception {
        when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD))
                .thenReturn(new TokenResponseDTO(ACCESS_TOKEN, "Bearer"));

        performLoginContract(invalidBearer, "", credentials(EXACT_NAME, EXACT_PASSWORD), 200);

        verify(authenticationService, times(1)).login(EXACT_NAME, EXACT_PASSWORD);
        verifyNoMoreInteractions(authenticationService);
    }

    @ParameterizedTest(name = "uniform credential rejection, invalid Bearer={0}")
    @MethodSource("bearerModes")
    void shouldPreserveIdenticalUnauthorizedResponsesDespiteIrrelevantBearer(boolean invalidBearer) throws Exception {
        when(authenticationService.login(UNKNOWN_NAME, VALID_PASSWORD))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));
        when(authenticationService.login(VALID_NAME, WRONG_PASSWORD))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        var unknown = performLoginContract(invalidBearer, "", credentials(UNKNOWN_NAME, VALID_PASSWORD), 401);
        var wrong = performLoginContract(invalidBearer, "", credentials(VALID_NAME, WRONG_PASSWORD), 401);
        assertThat(unknown.getResponse().getContentAsString())
                .isEqualTo(wrong.getResponse().getContentAsString());
        assertConfidentialErrorBody(unknown, UNKNOWN_NAME, VALID_PASSWORD);
        assertConfidentialErrorBody(wrong, VALID_NAME, WRONG_PASSWORD);

        verify(authenticationService, times(1)).login(UNKNOWN_NAME, VALID_PASSWORD);
        verify(authenticationService, times(1)).login(VALID_NAME, WRONG_PASSWORD);
        verifyNoMoreInteractions(authenticationService);
    }

    @ParameterizedTest(name = "mocked internal login failure, invalid Bearer={0}")
    @MethodSource("bearerModes")
    void shouldPreserveSafeLoginSpecificInternalFailureResponse(boolean invalidBearer) throws Exception {
        when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD)).thenThrow(processingFailure());

        var result = performLoginContract(invalidBearer, "", credentials(EXACT_NAME, EXACT_PASSWORD), 500);
        assertConfidentialErrorBody(result, EXACT_NAME, EXACT_PASSWORD, INTERNAL_DETAIL);

        verify(authenticationService, times(1)).login(EXACT_NAME, EXACT_PASSWORD);
        verifyNoMoreInteractions(authenticationService);
    }

    @ParameterizedTest(name = "strict login input: {1}, invalid Bearer={0}")
    @MethodSource("strictInvalidRequests")
    void shouldPreserveStrictLoginInputBeforeAuthenticationDespiteIrrelevantBearer(
            boolean invalidBearer, String scenario, String requestBody) throws Exception {
        var result = performLoginContract(invalidBearer, "", requestBody, 400);
        assertConfidentialErrorBody(result, VALID_NAME, VALID_PASSWORD, "secret123", "user@example.invalid");
        verifyNoInteractions(authenticationService);
    }

    static Stream<Boolean> bearerModes() {
        return Stream.of(false, true);
    }

    static Stream<Arguments> strictInvalidRequests() {
        return bearerModes().flatMap(bearer -> Stream.concat(
                Stream.concat(invalidNameRequests(), invalidPasswordRequests()),
                Stream.of(
                        Arguments.of("numeric name", "{\"name\":123,\"password\":\"secret123\"}"),
                        Arguments.of("boolean password", "{\"name\":\"ValidName\",\"password\":true}"),
                        Arguments.of("unknown property", "{\"name\":\"ValidName\",\"password\":\"valid-password-value\",\"unexpectedField\":\"must-be-rejected\"}"),
                        Arguments.of("boolean name", "{\"name\":true,\"password\":\"secret123\"}"),
                        Arguments.of("object name", "{\"name\":{},\"password\":\"secret123\"}"),
                        Arguments.of("array name", "{\"name\":[],\"password\":\"secret123\"}"),
                        Arguments.of("numeric password", "{\"name\":\"ValidName\",\"password\":123}"),
                        Arguments.of("object password", "{\"name\":\"ValidName\",\"password\":{}}"),
                        Arguments.of("array password", "{\"name\":\"ValidName\",\"password\":[]}"),
                        Arguments.of("both credentials missing", "{}"),
                        Arguments.of("array root", "[]"),
                        Arguments.of("string root", "\"credential-string\""),
                        Arguments.of("numeric root", "123"),
                        Arguments.of("boolean root", "true"),
                        Arguments.of("null root", "null"),
                        Arguments.of("malformed JSON", "{\"name\":"),
                        Arguments.of("missing body", ""),
                        Arguments.of("email cannot replace name", "{\"email\":\"user@example.invalid\",\"password\":\"secret123\"}"),
                        Arguments.of("identifier cannot replace name", "{\"identifier\":\"ValidName\",\"password\":\"secret123\"}"),
                        Arguments.of("profiles are not a login input", "{\"name\":\"ValidName\",\"password\":\"valid-password-value\",\"roles\":[\"CLIENT\"]}")))
                .map(arguments -> Arguments.of(bearer, arguments.get()[0], arguments.get()[1])));
    }

    @ParameterizedTest(name = "login context path, status={1}, invalid Bearer={0}")
    @MethodSource("contextPathOutcomes")
    void shouldPreserveAllLoginContractsWithTheExternalContextPath(boolean invalidBearer, int expectedStatus)
            throws Exception {
        if (expectedStatus == 200) {
            when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD))
                    .thenReturn(new TokenResponseDTO(ACCESS_TOKEN, "Bearer"));
        } else if (expectedStatus == 401) {
            when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD))
                    .thenThrow(new InvalidCredentialsException("Invalid credentials"));
        } else if (expectedStatus == 500) {
            when(authenticationService.login(EXACT_NAME, EXACT_PASSWORD)).thenThrow(processingFailure());
        }

        var body = expectedStatus == 400 ? "{}" : credentials(EXACT_NAME, EXACT_PASSWORD);
        performLoginContract(invalidBearer, "/api", body, expectedStatus);
        if (expectedStatus == 400) {
            verifyNoInteractions(authenticationService);
        } else {
            verify(authenticationService, times(1)).login(EXACT_NAME, EXACT_PASSWORD);
            verifyNoMoreInteractions(authenticationService);
        }
    }

    static Stream<Arguments> contextPathOutcomes() {
        return bearerModes().flatMap(bearer -> Stream.of(200, 400, 401, 500)
                .map(status -> Arguments.of(bearer, status)));
    }

    private String credentials(String name, String password) throws Exception {
        return mapper.writeValueAsString(mapper.createObjectNode().put("name", name).put("password", password));
    }

    private static AuthenticationProcessingException processingFailure() {
        return new AuthenticationProcessingException("Authentication could not be completed",
                new IllegalStateException(INTERNAL_DETAIL + " " + EXACT_PASSWORD + " $2a$hash-canary"));
    }

    /** This slice checks HTTP contracts; its mock token is never decoded as evidence of a real JWT. */
    private MvcResult performLoginContract(boolean invalidBearer, String contextPath, String body, int expectedStatus)
            throws Exception {
        var path = contextPath + "/login";
        var selectionRequest = new MockHttpServletRequest("POST", path);
        selectionRequest.setContextPath(contextPath);
        selectionRequest.setServletPath("/login");
        var chain = filters.getFilterChains().stream().filter(candidate -> candidate.matches(selectionRequest))
                .findFirst().orElseThrow();
        assertThat(chain.getFilters()).anyMatch(AuthorizationFilter.class::isInstance)
                .noneMatch(BearerTokenAuthenticationFilter.class::isInstance);
        var request = post(path).contextPath(contextPath).servletPath("/login")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (invalidBearer) request.header(HttpHeaders.AUTHORIZATION, INVALID_BEARER);
        var result = mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE)).andReturn();

        var responseBody = result.getResponse().getContentAsString();
        if (expectedStatus == 200) {
            var expected = mapper.createObjectNode().put("accessToken", ACCESS_TOKEN).put("tokenType", "Bearer");
            assertThat(mapper.readTree(responseBody)).isEqualTo(expected);
            assertThat(responseBody).doesNotContain(EXACT_NAME, EXACT_PASSWORD);
        } else {
            var reason = expectedStatus == 400 ? "Bad Request"
                    : expectedStatus == 401 ? "Unauthorized" : "Internal Server Error";
            var message = expectedStatus == 400 ? "Invalid login data"
                    : expectedStatus == 401 ? "Invalid credentials" : "Authentication could not be completed";
            var expected = mapper.createObjectNode().put("status", expectedStatus).put("error", reason)
                    .put("message", message).put("path", path);
            assertThat(mapper.readTree(responseBody)).isEqualTo(expected);
            assertConfidentialErrorBody(result, EXACT_NAME, EXACT_PASSWORD, INTERNAL_DETAIL, INVALID_BEARER);
        }
        return result;
    }
}
