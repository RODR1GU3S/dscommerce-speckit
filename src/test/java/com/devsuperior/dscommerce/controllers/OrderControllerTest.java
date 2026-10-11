package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderItemResponseDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.OrderResponseDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.exceptions.AuthenticationProcessingException;
import com.devsuperior.dscommerce.services.exceptions.InvalidCredentialsException;
import com.devsuperior.dscommerce.services.exceptions.InvalidOrderException;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import jakarta.validation.ConstraintViolationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionSystemException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Positive HTTP integration and a separate input-only MVC slice, without a stand-in controller. */
@NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
class OrderControllerTest {

    private static final String MVC_DIAGNOSTIC = "T038_INTERNAL_CANARY token-secret SELECT password_hash stack-trace";

    @Nested
    @SpringBootTest(properties = "spring.jpa.open-in-view=false")
    @AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
    @ActiveProfiles("test")
    @Import(OrderTestConfig.class)
    class SuccessfulRegistration extends OrderHttpTestSupport {

        @DynamicPropertySource
        static void isolatedDatabase(DynamicPropertyRegistry registry) {
            OrderTestConfig.isolatedDatabase(registry);
        }

        @Test
        void shouldReturn201WithExactlyTheOrderAndItemContractFields() throws Exception {
            var account = login(Set.of(UserRole.CLIENT));
            var second = fixture.createProduct("http-second", new BigDecimal("7.50"));
            var first = fixture.createProduct("http-first", new BigDecimal("10.00"));
            var body = """
                    {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3}]}
                    """.formatted(first, second);

            assertConfirmation(account.token(), body, account.id(),
                    List.of(new ExpectedLine(first, 2, "10.00", "20.00"),
                            new ExpectedLine(second, 3, "7.50", "22.50")), "42.50");
        }

        @Test
        void shouldReturnDecimalCapacityAsTextWithoutExponentOrNumericCoercion() throws Exception {
            var account = login(Set.of(UserRole.CLIENT));
            var product = fixture.createProduct("http-decimal-capacity", new BigDecimal("99999999999999999.99"));
            var body = """
                    {"items":[{"productId":%d,"quantity":1}]}
                    """.formatted(product);

            assertConfirmation(account.token(), body, account.id(),
                    List.of(new ExpectedLine(product, 1, "99999999999999999.99", "99999999999999999.99")),
                    "99999999999999999.99");
        }

        @Test
        void shouldReturnTwoDecimalPlacesForUnitPriceSubtotalAndTotal() throws Exception {
            var account = login(Set.of(UserRole.CLIENT));
            var product = fixture.createProduct("http-tenths", new BigDecimal("0.10"));
            var body = """
                    {"items":[{"productId":%d,"quantity":3}]}
                    """.formatted(product);

            assertConfirmation(account.token(), body, account.id(),
                    List.of(new ExpectedLine(product, 3, "0.10", "0.30")), "0.30");
        }

        @Test
        void shouldPreserveFrameworkUnsupportedMediaTypeStatusAndAcceptHeader() throws Exception {
            var account = login(Set.of(UserRole.CLIENT));
            var product = fixture.createProduct("http-media-type", new BigDecimal("10.00"));
            var result = mvc.perform(post("/orders")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token())
                            .contentType(MediaType.TEXT_PLAIN).accept(MediaType.APPLICATION_JSON)
                            .content("{\"items\":[{\"productId\":" + product + ",\"quantity\":2}]}"))
                    .andExpect(status().isUnsupportedMediaType()).andReturn();

            assertThat(result.getResolvedException())
                    .isInstanceOf(org.springframework.web.HttpMediaTypeNotSupportedException.class);
            var failure = (org.springframework.web.HttpMediaTypeNotSupportedException) result.getResolvedException();
            assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT)).isNotBlank()
                    .isEqualTo(failure.getHeaders().getFirst(HttpHeaders.ACCEPT));
            assertThat(failure.getHeaders().getAccept()).contains(MediaType.APPLICATION_JSON);
            System.out.printf("ORDER_FRAMEWORK_HEADERS status=%d accept=%s%n",
                    result.getResponse().getStatus(), result.getResponse().getHeader(HttpHeaders.ACCEPT));
        }
    }

    /** No filters, repositories or real service: proves only MVC reading/validation/delegation. */
    @Nested
    @WebMvcTest(controllers = OrderController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
    @AutoConfigureMockMvc(addFilters = false, print = MockMvcPrint.NONE)
    @ExtendWith(OutputCaptureExtension.class)
    class InputBoundary {

        @Autowired private MockMvc inputMvc;
        @Autowired private ObjectMapper inputMapper;
        @Autowired private ApplicationContext inputContext;
        @MockBean private OrderService observedService;

        @BeforeEach
        void prepareOnlyTheReadingBoundary() {
            assertThat(mockingDetails(observedService).isMock()).isTrue();
            assertThat(inputContext.getBeansOfType(com.devsuperior.dscommerce.repositories.ProductRepository.class)).isEmpty();
            assertThat(inputContext.getBeansOfType(com.devsuperior.dscommerce.repositories.OrderRepository.class)).isEmpty();
            // Always return a well-formed confirmation if MVC delegates, even for an invalid request.
            // This exposes missing validation as 201/service invocation, rather than a stub NPE.
            var confirmation = new OrderResponseDTO(1L, 42L, Instant.parse("2026-10-08T15:00:00.123Z"),
                    OrderStatus.WAITING_PAYMENT, List.of(new OrderItemResponseDTO(1L, 1, "10.00", "10.00")), "10.00");
            when(observedService.register(any(OrderRequestDTO.class), nullable(AuthenticatedAccountPrincipal.class)))
                    .thenReturn(confirmation);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.OrderControllerTest#invalidBodiesAndContainers")
        void shouldRejectInvalidBodiesAndContainersBeforeService(String description, String rawJson) throws Exception {
            assertInvalidInput(description, rawJson);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.OrderControllerTest#invalidIntegerFields")
        void shouldRejectInvalidIntegerFieldsBeforeService(String description, String rawJson) throws Exception {
            assertInvalidInput(description, rawJson);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.OrderControllerTest#unknownAndSystemFields")
        void shouldRejectExtraOrSystemFieldsBeforeService(String description, String rawJson) throws Exception {
            assertInvalidInput(description, rawJson);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.OrderControllerTest#repeatedJsonKeys")
        void shouldRejectRepeatedJsonKeysBeforeService(String description, String rawJson) throws Exception {
            assertInvalidInput(description, rawJson);
        }

        @Test
        void shouldRejectTheWholeInputWithAZeroQuantityBetweenValidLinesBeforeService() throws Exception {
            assertInvalidInput("US2.13 zero between valid lines", """
                    {"items":[{"productId":1,"quantity":2},{"productId":2,"quantity":0},{"productId":3,"quantity":3}]}
                    """);
        }

        @ParameterizedTest(name = "reading positive productId={0}, quantity={1}")
        @CsvSource({"1,1", "9223372036854775807,2147483647"})
        void shouldReadPositiveIntegerTokensIncludingBothMaximumsExactly(long productId, int quantity) throws Exception {
            var rawJson = "{\"items\":[{\"productId\":%d,\"quantity\":%d}]}".formatted(productId, quantity);
            inputMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(rawJson))
                    .andExpect(status().isCreated());
            var received = ArgumentCaptor.forClass(OrderRequestDTO.class);
            verify(observedService).register(received.capture(), nullable(AuthenticatedAccountPrincipal.class));
            assertThat(received.getValue().items()).containsExactly(new OrderItemRequestDTO(productId, quantity));
            verifyNoMoreInteractions(observedService);
        }

        @ParameterizedTest(name = "MVC {0}, context {4}")
        @MethodSource("com.devsuperior.dscommerce.controllers.OrderControllerTest#mvcFailures")
        void shouldTranslateMvcFailuresWithoutExposingDiagnostics(String label, RuntimeException failure,
                                                                 int expectedStatus, String message,
                                                                 boolean contextPath, CapturedOutput output) throws Exception {
            when(observedService.register(any(OrderRequestDTO.class), nullable(AuthenticatedAccountPrincipal.class)))
                    .thenThrow(failure);
            var path = contextPath ? "/api/orders" : "/orders";
            var request = post(path).servletPath("/orders").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"items\":[{\"productId\":1,\"quantity\":1}]}");
            if (contextPath) request.contextPath("/api");
            var start = output.getAll().length();
            MvcResult observed = null;
            try {
                observed = inputMvc.perform(request).andReturn();
            } catch (jakarta.servlet.ServletException escaped) {
                // A known service exception escaping MVC is missing behavior, not a fixture/startup error.
                assertThat(escaped.getCause()).isSameAs(failure);
            }
            assertThat(observed).as("MVC failure must become a contractual HTTP response").isNotNull();
            var response = observed.getResponse();
            var expected = inputMapper.createObjectNode().put("status", expectedStatus)
                    .put("error", HttpStatus.valueOf(expectedStatus).getReasonPhrase())
                    .put("message", message).put("path", path);
            assertAll(
                    () -> assertThat(response.getStatus()).isEqualTo(expectedStatus),
                    () -> assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE),
                    () -> assertThat(inputMapper.readTree(response.getContentAsString())).isEqualTo(expected),
                    () -> assertThat(response.getContentAsString() + output.getAll().substring(start))
                            .as("received data and internal diagnostics must not be exposed")
                            .doesNotContain(MVC_DIAGNOSTIC, "token-secret", "SELECT password_hash", "stack-trace"));
            if (expectedStatus == 401) {
                assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
            }
            assertThat(observed.getResolvedException()).isSameAs(failure);
            verify(observedService).register(any(OrderRequestDTO.class), nullable(AuthenticatedAccountPrincipal.class));
            verifyNoMoreInteractions(observedService);
            System.out.printf("T038_MVC case=%s context=%s status=%d%n", label, contextPath, response.getStatus());
        }

        @ParameterizedTest
        @CsvSource({"false", "true"})
        void shouldPreserveContextPathForReadingAndBeanValidationErrors(boolean beanValidation) throws Exception {
            var body = beanValidation ? "{\"items\":[{\"productId\":1,\"quantity\":0}]}" : "{\"items\":[";
            var result = inputMvc.perform(post("/api/orders").contextPath("/api").servletPath("/orders")
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
            var expected = inputMapper.createObjectNode().put("status", 400).put("error", "Bad Request")
                    .put("message", "Invalid order data").put("path", "/api/orders");
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(inputMapper.readTree(result.getResponse().getContentAsString())).isEqualTo(expected);
            verifyNoInteractions(observedService);
        }

        private void assertInvalidInput(String description, String rawJson) throws Exception {
            var request = post("/orders").contentType(MediaType.APPLICATION_JSON);
            if (rawJson != null) { request.content(rawJson); }
            // Raw text goes directly to MVC: never parse/rewrite duplicate keys in preparation.
            MvcResult result = inputMvc.perform(request).andReturn();
            System.out.printf("T026_BOUNDARY case=%s status=%d serviceCalls=%d%n", description,
                    result.getResponse().getStatus(), mockingDetails(observedService).getInvocations().size());
            assertAll(description,
                    () -> status().isBadRequest().match(result),
                    () -> {
                        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
                        assertThat(result.getResponse().getContentAsString()).isNotBlank();
                        var expected = inputMapper.readTree("""
                                {"status":400,"error":"Bad Request","message":"Invalid order data","path":"/orders"}
                                """);
                        // JsonNode equality requires exactly these four fields and their JSON types/values.
                        assertThat(inputMapper.readTree(result.getResponse().getContentAsString())).isEqualTo(expected);
                    },
                    () -> verifyNoInteractions(observedService));
        }
    }

    static Stream<Arguments> mvcFailures() {
        var cases = new ArrayList<Arguments>();
        for (boolean context : List.of(false, true)) {
            for (var reason : InvalidOrderException.Reason.values()) {
                var message = switch (reason) {
                    case DUPLICATE_PRODUCT -> "Duplicate product in order";
                    case INVALID_PRODUCT_PRICE -> "Product price is not valid for order";
                    case UNREPRESENTABLE_VALUE -> "Order value cannot be represented";
                };
                var failure = new InvalidOrderException(reason) {
                    @Override public String getMessage() { return MVC_DIAGNOSTIC; }
                };
                cases.add(Arguments.of(reason.name(), failure, 422, message, context));
            }
            cases.add(Arguments.of("product absent", new ProductNotFoundException(MVC_DIAGNOSTIC), 404, "Product not found", context));
            cases.add(Arguments.of("late authentication", new BadCredentialsException(MVC_DIAGNOSTIC), 401, "Authentication required or invalid", context));
            cases.add(Arguments.of("invalid credentials", new InvalidCredentialsException(MVC_DIAGNOSTIC), 401, "Authentication required or invalid", context));
            cases.add(Arguments.of("late permission", new AccessDeniedException(MVC_DIAGNOSTIC), 403, "Order registration not permitted", context));
            for (var failure : List.of(new IllegalStateException(MVC_DIAGNOSTIC),
                    new AuthenticationProcessingException(MVC_DIAGNOSTIC, new IllegalStateException(MVC_DIAGNOSTIC)),
                    new DataIntegrityViolationException(MVC_DIAGNOSTIC),
                    new TransactionSystemException(MVC_DIAGNOSTIC),
                    new OrderAccountResolutionException(MVC_DIAGNOSTIC, new IllegalStateException(MVC_DIAGNOSTIC)),
                    new ConstraintViolationException(MVC_DIAGNOSTIC, Set.of()),
                    new org.springframework.web.server.ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            MVC_DIAGNOSTIC),
                    new IllegalArgumentException(MVC_DIAGNOSTIC))) {
                cases.add(Arguments.of(failure.getClass().getSimpleName(), failure, 500,
                        "Order registration could not be completed", context));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> invalidBodiesAndContainers() {
        return Stream.of(
                Arguments.of("absent body", null),
                Arguments.of("empty body", ""),
                Arguments.of("whitespace body", "  "),
                Arguments.of("unreadable JSON", "{\"items\":["),
                Arguments.of("root null", "null"),
                Arguments.of("root array", "[]"),
                Arguments.of("root string", "\"text\""),
                Arguments.of("root number", "1"),
                Arguments.of("root boolean", "true"),
                Arguments.of("missing items", "{}"),
                Arguments.of("null items", "{\"items\":null}"),
                Arguments.of("empty items", "{\"items\":[]}"),
                Arguments.of("object items", "{\"items\":{}}"),
                Arguments.of("string items", "{\"items\":\"text\"}"),
                Arguments.of("number items", "{\"items\":1}"),
                Arguments.of("boolean items", "{\"items\":true}"),
                Arguments.of("null line", "{\"items\":[null]}"),
                Arguments.of("array line", "{\"items\":[[]]}"),
                Arguments.of("string line", "{\"items\":[\"text\"]}"),
                Arguments.of("number line", "{\"items\":[1]}"),
                Arguments.of("boolean line", "{\"items\":[true]}"),
                Arguments.of("empty line object", "{\"items\":[{}]}"),
                Arguments.of("missing productId", "{\"items\":[{\"quantity\":1}]}"),
                Arguments.of("missing quantity", "{\"items\":[{\"productId\":1}]}"));
    }

    static Stream<Arguments> invalidIntegerFields() {
        var cases = new ArrayList<Arguments>();
        for (var field : List.of("productId", "quantity")) {
            var overflow = field.equals("productId") ? "9223372036854775808" : "2147483648";
            var underflow = field.equals("productId") ? "-9223372036854775809" : "-2147483649";
            for (var token : List.of("null", "0", "-1", overflow, underflow, "1.0", "1.5", "1e0",
                    "\"1\"", "\"text\"", "true", "false", "[]", "{}")) {
                var line = field.equals("productId")
                        ? "{\"productId\":%s,\"quantity\":1}".formatted(token)
                        : "{\"productId\":1,\"quantity\":%s}".formatted(token);
                cases.add(Arguments.of(field + " token " + token, "{\"items\":[" + line + "]}"));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> unknownAndSystemFields() {
        var cases = new ArrayList<Arguments>();
        var fields = List.of(new ExtraField("accountId", "99"), new ExtraField("buyerId", "99"),
                new ExtraField("buyer", "{\"id\":99}"), new ExtraField("id", "99"),
                new ExtraField("createdAt", "\"2026-10-08T15:00:00.123Z\""), new ExtraField("status", "\"PAID\""),
                new ExtraField("unitPrice", "\"10.00\""), new ExtraField("subtotal", "\"20.00\""),
                new ExtraField("total", "\"42.50\""), new ExtraField("unexpected", "{\"anything\":true}"));
        for (var field : fields) {
            for (var value : List.of(field.value(), "null")) {
                cases.add(Arguments.of("root extra " + field.name() + "=" + value,
                        "{\"items\":[{\"productId\":1,\"quantity\":1}],\"%s\":%s}".formatted(field.name(), value)));
                cases.add(Arguments.of("line extra " + field.name() + "=" + value,
                        "{\"items\":[{\"productId\":1,\"quantity\":1,\"%s\":%s}]}".formatted(field.name(), value)));
            }
        }
        return cases.stream();
    }

    static Stream<Arguments> repeatedJsonKeys() {
        var cases = new ArrayList<Arguments>();
        var validItems = "[{\"productId\":1,\"quantity\":1}]";
        for (var repeatedItems : List.of(validItems, "[{\"productId\":2,\"quantity\":2}]", "null", "[]")) {
            cases.add(Arguments.of("duplicate items last=" + repeatedItems,
                    "{\"items\":%s,\"items\":%s}".formatted(validItems, repeatedItems)));
        }
        cases.add(Arguments.of("duplicate items first null", "{\"items\":null,\"items\":" + validItems + "}"));
        for (var field : List.of("productId", "quantity")) {
            for (var pair : List.of("1,1", "1,2", "0,1", "1,null")) {
                var values = pair.split(",");
                var other = field.equals("productId") ? "quantity" : "productId";
                cases.add(Arguments.of("duplicate " + field + " " + pair,
                        "{\"items\":[{\"%s\":%s,\"%s\":%s,\"%s\":1}]}"
                                .formatted(field, values[0], field, values[1], other)));
            }
        }
        return cases.stream();
    }

    private record ExtraField(String name, String value) { }
}

/** Shared preparation/assertions for the two T015 classes; never registers an order component. */
abstract class OrderHttpTestSupport {

    private static final String PASSWORD = "order-http-test-password";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected OrderTestFixture fixture;
    @Autowired protected OrderTestConfig.ControlledClock clock;
    @Autowired protected JwtDecoder decoder;
    @Autowired protected FilterChainProxy securityFilters;
    @Autowired protected JdbcTemplate jdbc;
    protected final Set<Long> ownedAccounts = new LinkedHashSet<>();

    @BeforeEach
    void prepareExistingContext() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(securityFilters.getFilters("/login")).isNotEmpty();
        assertThat(securityFilters.getFilters("/orders")).isNotEmpty();
        clock.setInstant(Instant.now());
    }

    @AfterEach
    void cleanOwnedFixturesWithoutRequiringOrderSchema() {
        if (tableExists("TB_ORDER")) {
            for (var accountId : ownedAccounts) {
                if (tableExists("TB_ORDER_ITEM")) {
                    jdbc.update("delete from tb_order_item where order_id in "
                            + "(select id from tb_order where buyer_id = ?)", accountId);
                }
                jdbc.update("delete from tb_order where buyer_id = ?", accountId);
            }
        }
        fixture.cleanUp();
        ownedAccounts.clear();
    }

    protected LoggedAccount login(Set<UserRole> roles) throws Exception {
        var name = "order-http-" + UUID.randomUUID();
        var id = fixture.createAccount(name, PASSWORD, roles);
        ownedAccounts.add(id);
        assertDatabaseRoles(id, roles);
        var body = """
                {"name":"%s","password":"%s"}
                """.formatted(name, PASSWORD);
        var result = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertFields(response, "accessToken", "tokenType");
        assertThat(response.path("tokenType").textValue()).isEqualTo("Bearer");
        // Never assert/print a token value or a complete Jwt object on failure.
        assertThat(response.path("accessToken").isTextual()).isTrue();
        var token = response.path("accessToken").textValue();
        assertThat(token.isBlank()).isFalse();
        var decoded = decodeWithoutLeakingToken(token);
        assertThat(decoded.getSubject()).isEqualTo(Long.toString(id));
        assertThat(decoded.getClaims().containsKey("roles")).isFalse();
        clock.setInstant(decoded.getIssuedAt().plus(Duration.ofMillis(123)));
        return new LoggedAccount(id, name, token, decoded);
    }

    protected Jwt decodeWithoutLeakingToken(String token) {
        try {
            return decoder.decode(token);
        } catch (JwtException failure) {
            // Decoder failures are preparation failures, never RED; keep diagnostics token-free.
            throw new AssertionError("Real decoder rejected the test JWT: " + failure.getClass().getSimpleName());
        }
    }

    protected void assertDatabaseRoles(long id, Set<UserRole> expected) {
        assertThat(jdbc.queryForList("select role from tb_user_account_role where user_account_id = ?",
                String.class, id)).containsExactlyInAnyOrderElementsOf(expected.stream().map(Enum::name).toList());
    }

    protected JsonNode assertConfirmation(String token, String rawBody, long accountId,
                                           List<ExpectedLine> expectedItems, String total) throws Exception {
        var result = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(rawBody))
                // Initial RED must occur here, before response mapping or any order SQL assertion.
                .andExpect(status().isCreated()).andReturn();
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertFields(response, "id", "accountId", "createdAt", "status", "items", "total");
        assertPositiveInt64(response.path("id"));
        assertPositiveInt64(response.path("accountId"));
        assertThat(response.path("accountId").longValue()).isEqualTo(accountId);
        assertThat(response.path("createdAt").isTextual()).isTrue();
        assertThat(response.path("createdAt").textValue())
                .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");
        assertThat(Instant.parse(response.path("createdAt").textValue()))
                .isEqualTo(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(response.path("status").textValue()).isEqualTo("WAITING_PAYMENT");
        assertThat(response.path("items").isArray()).isTrue();
        assertThat(response.path("items").size()).isEqualTo(expectedItems.size());
        for (var position = 0; position < expectedItems.size(); position++) {
            var actual = response.path("items").get(position);
            var expected = expectedItems.get(position);
            assertFields(actual, "productId", "quantity", "unitPrice", "subtotal");
            assertPositiveInt64(actual.path("productId"));
            assertThat(actual.path("productId").longValue()).isEqualTo(expected.productId());
            assertThat(actual.path("quantity").isIntegralNumber()).isTrue();
            assertThat(actual.path("quantity").canConvertToInt()).isTrue();
            assertThat(actual.path("quantity").intValue()).isEqualTo(expected.quantity());
            assertMoney(actual.path("unitPrice"), expected.unitPrice());
            assertMoney(actual.path("subtotal"), expected.subtotal());
        }
        assertMoney(response.path("total"), total);
        return response;
    }

    private boolean tableExists(String name) {
        return Integer.valueOf(1).equals(jdbc.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = 'PUBLIC' and table_name = ?", Integer.class, name));
    }

    private static void assertFields(JsonNode value, String... expected) {
        assertThat(value.isObject()).isTrue();
        var fields = new HashSet<String>();
        value.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }

    private static void assertPositiveInt64(JsonNode value) {
        assertThat(value.isIntegralNumber()).isTrue();
        assertThat(value.canConvertToLong()).isTrue();
        assertThat(value.longValue()).isPositive();
    }

    private static void assertMoney(JsonNode value, String expected) {
        assertThat(value.isTextual()).isTrue();
        assertThat(value.textValue()).matches("(?:0|[1-9][0-9]{0,16})\\.[0-9]{2}").isEqualTo(expected);
    }

    protected record ExpectedLine(long productId, int quantity, String unitPrice, String subtotal) { }

    protected record LoggedAccount(long id, String name, String token, Jwt decoded) {
        @Override
        public String toString() {
            return "LoggedAccount[id=" + id + ", token=<redacted>]";
        }
    }
}
