package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.dto.PaymentRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.AccessAccounts;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AuthenticatedAccountService;
import com.devsuperior.dscommerce.services.PaymentAccessService;
import com.devsuperior.dscommerce.services.PaymentRegistrationService;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RootHttpContext
class PaymentRegistrationEndpointHttpTest extends PaymentEndpointScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** Real HTTP/security/binding; settlement service is mocked, so no claim of payment persistence or commit. */
abstract class PaymentEndpointScenarios extends PaymentRegistrationHttpTestSupport {
    private static final String PATH = "/admin/orders/71/payments";
    private static final Instant OCCURRENCE = Instant.parse("2026-10-21T11:00:00.123Z");
    private static final String BODY = "{\"occurredAt\":\"2026-10-21T11:00:00.123000000Z\"}";
    @MockBean protected PaymentRegistrationService payments;
    @SpyBean protected AuthenticatedAccountService accountResolution;
    @SpyBean protected PaymentAccessService paymentAccess;
    private PaymentRegistrationTestFixture fixture;
    private AccessAccounts accounts;
    private final Map<Long, String> tokens = new HashMap<>();

    @BeforeAll
    void prepareBoundaryAccountsWithRealLogin() {
        fixture = new PaymentRegistrationTestFixture(context);
        accounts = fixture.prepareAccessAccounts();
        for (var account : java.util.List.of(accounts.admin(), accounts.both(), accounts.client(), accounts.none())) {
            tokens.put(account.id(), realHttpLogin(account.id(), account.name(), account.password()));
        }
        System.out.println("T012_CONTEXT context=" + (expectedContextPath().isEmpty() ? "root" : "api")
                + " database=" + context.getEnvironment().getRequiredProperty("spring.datasource.url").split(";", 2)[0]
                + " settlementService=mock http=real");
    }

    @BeforeEach
    void configureOnlyTheSettlementBoundary() {
        clock.setInstant(Instant.now());
        when(payments.register(anyLong(), any(PaymentRegistrationRequestDTO.class), any(Authentication.class)))
                .thenAnswer(call -> {
                    // This is the request thread before any simulated settlement work; not a proxy/commit assertion.
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
                    Authentication authentication = call.getArgument(2);
                    assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedAccountPrincipal.class);
                    PaymentRegistrationRequestDTO input = call.getArgument(1);
                    return new PaymentConfirmationDTO(call.getArgument(0), input.occurredAt(), OrderStatus.PAID);
                });
    }

    @AfterAll
    void cleanOnlyBoundaryOwnedAccounts() {
        if (fixture != null) { fixture.close(); }
        tokens.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "BOTH"})
    void authorizedPostBindsLosslesslyAndReturnsOnlyTheThreeConfirmationFields(String profile) throws Exception {
        var payer = profile.equals("ADMIN") ? accounts.admin() : accounts.both();
        var response = call("POST", PATH, token(payer), BODY);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var json = mapper.readTree(response.body());
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.has("orderId") && json.has("occurredAt") && json.has("status")).isTrue();
        assertThat(json.get("orderId").longValue()).isEqualTo(71L);
        assertThat(json.get("occurredAt").textValue()).isEqualTo("2026-10-21T11:00:00.123Z");
        assertThat(json.get("status").textValue()).isEqualTo("PAID");
        verify(payments).register(eq(71L), eq(new PaymentRegistrationRequestDTO(OCCURRENCE)),
                argThat(auth -> ((AuthenticatedAccountPrincipal) auth.getPrincipal()).accountId().equals(payer.id())));
        verifyNoMoreInteractions(payments);
        verify(paymentAccess).requireAdmin(any(Authentication.class));
    }

    @Test
    void positiveLongMaximumReachesTheServiceWithoutIdentityLoss() throws Exception {
        var response = call("POST", "/admin/orders/9223372036854775807/payments", token(accounts.admin()), BODY);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(response.body()).get("orderId").longValue()).isEqualTo(Long.MAX_VALUE);
        verify(payments).register(eq(Long.MAX_VALUE), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "GET", "HEAD", "PUT", "DELETE", "PATCH", "OPTIONS"})
    void everyPaymentMethodRequiresAuthenticationBeforeMvc(String method) throws Exception {
        var response = call(method, PATH, null, "not-json");
        assertError(response, PATH, 401, "Authentication required or invalid", method.equals("HEAD"));
        verifyNoInteractions(payments);
    }

    @Test
    void malformedBearerUsesThePayment401EnvelopeWithoutProductErrors() throws Exception {
        assertError(call("POST", PATH, "invalid-feature008-token", BODY), PATH,
                401, "Authentication required or invalid", false);
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "NONE"})
    void nonAdminGetsThePayment403BeforeInvalidBodyAndQueryAreExamined(String profile) throws Exception {
        var account = profile.equals("CLIENT") ? accounts.client() : accounts.none();
        assertError(call("POST", PATH + "?amount=1", token(account), "not-json"), PATH,
                403, "Payment registration not permitted", false);
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808"})
    void invalidPathIdentityIs400BeforeTheSettlementService(String id) throws Exception {
        var path = "/admin/orders/" + id + "/payments";
        assertError(call("POST", path, token(accounts.admin()), BODY), path, 400, "Invalid payment data", false);
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"occurredAt\":null}", "{\"occurredAt\":17}",
            "{\"occurredAt\":\"2026-10-21T11:00:00.123Z\",\"amount\":\"0.01\"}"})
    void bodyReaderAndValidRejectBasicInvalidInputBeforeTheService(String body) throws Exception {
        assertError(call("POST", PATH, token(accounts.admin()), body), PATH, 400, "Invalid payment data", false);
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?amount=1", "?occurredAt=ignored"})
    void anyQueryParameterIsRejectedAndIsNotEchoedInTheErrorPath(String query) throws Exception {
        assertError(call("POST", PATH + query, token(accounts.admin()), BODY), PATH, 400, "Invalid payment data", false);
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @EnumSource(PaymentRegistrationException.Reason.class)
    void serviceCategoriesHaveTheirExactSafeContractResponse(PaymentRegistrationException.Reason reason) throws Exception {
        var rejection = new PaymentRegistrationException(reason);
        doThrow(rejection).when(payments).register(anyLong(), any(), any());
        assertError(call("POST", PATH, token(accounts.admin()), BODY), PATH,
                reason.getStatus(), rejection.getMessage(), false);
        verify(payments).register(eq(71L), any(), any());
        verifyNoMoreInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "constraint", "validation", "mapping", "null-result"})
    void internalServiceOrResultFailuresStay500WithoutProductOrInputDiagnostics(String failure) throws Exception {
        if (failure.equals("null-result")) {
            doReturn(null).when(payments).register(anyLong(), any(), any());
        } else {
            RuntimeException cause = switch (failure) {
                case "database" -> new DataAccessResourceFailureException("private DB detail");
                case "constraint" -> new ConstraintViolationException("private entity detail", Set.of());
                case "validation" -> new ValidationException("private validator detail");
                default -> new IllegalArgumentException("private mapping detail");
            };
            doThrow(cause).when(payments).register(anyLong(), any(), any());
        }
        assertError(call("POST", PATH, token(accounts.admin()), BODY), PATH,
                500, "Payment registration could not be completed", false);
        verify(payments).register(eq(71L), any(), any());
        verifyNoMoreInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void methodReturnValidationIs500WhileInputMethodValidationIs400(boolean returnValue) throws Exception {
        var failure = mock(HandlerMethodValidationException.class);
        when(failure.isForReturnValue()).thenReturn(returnValue);
        doThrow(failure).when(payments).register(anyLong(), any(), any());
        assertError(call("POST", PATH, token(accounts.admin()), BODY), PATH, returnValue ? 500 : 400,
                returnValue ? "Payment registration could not be completed" : "Invalid payment data", false);
    }

    @Test
    void accountResolutionFailureInTheJwtConverterIs500RatherThan401() throws Exception {
        doThrow(new OrderAccountResolutionException("private account detail", new IllegalStateException("private")))
                .when(accountResolution).resolve(accounts.admin().id());
        assertError(call("POST", PATH, token(accounts.admin()), BODY), PATH,
                500, "Payment registration could not be completed", false);
        verifyNoInteractions(payments);
    }

    @Test
    void scalarGuardFailureBeforeMvcIs500RatherThanAuthenticationFailure() throws Exception {
        doThrow(new PaymentRegistrationException(PaymentRegistrationException.Reason.PROCESSING_FAILURE,
                new IllegalStateException("private lookup detail"))).when(paymentAccess).requireAdmin(any());
        assertError(call("POST", PATH, token(accounts.admin()), BODY), PATH,
                500, "Payment registration could not be completed", false);
        verifyNoInteractions(payments);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH"})
    void authorizedUnsupportedMethodsRemainNative405AndExposeNoAdministrativeQuery(String method) {
        var response = call(method, PATH, token(accounts.admin()), null);
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Allow").orElse("")).contains("POST");
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(payments);
    }

    @Test
    void unsupportedRequestMediaTypeKeeps415AndItsNativeAcceptHeader() {
        var response = call("POST", PATH, token(accounts.admin()), BODY, "application/json", "text/plain");
        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.headers().firstValue("Accept").orElse("")).contains("application/json");
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(payments);
    }

    @Test
    void unacceptableResponseMediaTypeKeeps406WithoutForcingJson() {
        var response = call("POST", PATH, token(accounts.admin()), BODY, "text/plain", "application/json");
        assertThat(response.statusCode()).isEqualTo(406);
        assertThat(response.body()).isEmpty();
        verifyNoInteractions(payments);
    }

    @Test
    void neighboringPublicOrderAndProductSecurityContractsKeepTheirOwnResponses() throws Exception {
        assertThat(call("GET", "/products", null, null).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/admin/products", token(accounts.admin()), null).statusCode()).isEqualTo(200);
        var productError = call("GET", "/admin/products", null, null);
        assertThat(productError.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(productError.body()).has("errors")).isTrue();
        assertThat(mapper.readTree(productError.body()).get("message").textValue())
                .isEqualTo("Authentication required or invalid");
        var own = call("GET", "/orders", null, null);
        assertError(own, "/orders", 401, "Authentication required or invalid", false);
        assertThat(mapper.readTree(call("POST", "/orders", token(accounts.none()), "{}").body())
                .get("message").textValue()).isEqualTo("Order registration not permitted");
        verifyNoInteractions(payments);
    }

    private String token(Account account) { return tokens.get(account.id()); }

    private HttpResponse<byte[]> call(String method, String path, String token, String body) {
        return call(method, path, token, body, "application/json", "application/json");
    }

    private HttpResponse<byte[]> call(String method, String path, String token, String body, String accept, String type) {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + expectedContextPath() + path))
                .timeout(Duration.ofSeconds(30)).header("Accept", accept).header("Content-Type", type);
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return send(request.build());
    }

    private void assertError(HttpResponse<byte[]> response, String path, int status, String message, boolean head)
            throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .isEqualTo(status == 401 ? java.util.List.of("Bearer") : java.util.List.of());
        if (head) { assertThat(response.body()).isEmpty(); return; }
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", HttpStatus.valueOf(status).getReasonPhrase()).put("message", message)
                .put("path", expectedContextPath() + path);
        assertThat(mapper.readTree(response.body())).isEqualTo(expected);
    }
}
