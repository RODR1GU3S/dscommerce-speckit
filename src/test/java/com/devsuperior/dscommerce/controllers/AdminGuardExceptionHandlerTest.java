package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.controllers.handlers.GlobalExceptionHandler;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Direct advice/serialization contract only; filter and real MVC integration remain separate. */
class AdminGuardExceptionHandlerTest {

    private static final String PRIVATE_DETAIL = "T012_PRIVATE_BODY_QUERY_TOKEN_SQL";
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest(name = "admin context={0}, failure={1}")
    @MethodSource("administrativeFailures")
    void shouldWriteSafeAdministrativeGuardErrorsWithExternalPathAndEmptyErrors(String context, Failure failure) {
        var request = request(context, "/admin/products/9");

        var response = assertDoesNotThrow(() -> responseFor(failure.exception(), request));

        assertEnvelope(response, failure.status, failure.adminMessage, context + "/admin/products/9", true);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(failure.status == 401 ? "Bearer" : null);
    }

    static Stream<Arguments> administrativeFailures() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(Failure.values())
                .map(failure -> Arguments.of(context, failure)));
    }

    @ParameterizedTest(name = "outside admin {0}, failure={1}")
    @MethodSource("outsideAdministrativeNamespace")
    void shouldLeavePublicAndSimilarlyNamedRoutesOutsideAdministrativeTranslation(String path, Failure failure) {
        var exception = failure.exception();

        assertThatThrownBy(() -> responseFor(exception, request("/api", path))).isSameAs(exception);
    }

    static Stream<Arguments> outsideAdministrativeNamespace() {
        return Stream.of("/products/9", "/administrator/products/9")
                .flatMap(path -> Stream.of(Failure.AUTHENTICATION, Failure.PERMISSION, Failure.PROCESSING)
                        .map(failure -> Arguments.of(path, failure)));
    }

    @Test
    void shouldNotTreatAnAdminNamedContextAsAnAdministrativeApplicationPath() {
        var exception = Failure.PROCESSING.exception();

        assertThatThrownBy(() -> responseFor(exception, request("/admin", "/products/9"))).isSameAs(exception);
    }

    @ParameterizedTest
    @MethodSource("orderFailures")
    void shouldKeepTheExistingFourFieldOrderMessagesAndChallenge(Failure failure, String message) {
        var response = assertDoesNotThrow(() -> responseFor(failure.exception(), request("/api", "/orders")));

        assertEnvelope(response, failure.status, message, "/api/orders", false);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(failure.status == 401 ? "Bearer" : null);
    }

    static Stream<Arguments> orderFailures() {
        return Stream.of(Arguments.of(Failure.AUTHENTICATION, "Authentication required or invalid"),
                Arguments.of(Failure.PERMISSION, "Order registration not permitted"),
                Arguments.of(Failure.PROCESSING, "Order registration could not be completed"));
    }

    @ParameterizedTest
    @MethodSource("nativeClientErrors")
    void shouldLeaveNativeMvcClientErrorsIntactRatherThanTurnThemIntoInternalFailures(Exception exception) {
        var request = request("/api", "/admin/products/9");
        if (exception instanceof NoResourceFoundException) {
            assertThatThrownBy(() -> handler.handleOrderProcessing(exception, request)).isSameAs(exception);
            return;
        }
        var nativeError = (org.springframework.web.ErrorResponse) exception;
        var response = assertDoesNotThrow(() -> handler.handleOrderProcessing(exception, request));
        assertEnvelope(response, nativeError.getStatusCode().value(), "Product maintenance request not supported",
                "/api/admin/products/9", true);
        assertThat(response.getHeaders()).isEqualTo(nativeError.getHeaders());
        assertThat(response.getHeaders()).doesNotContainKeys(HttpHeaders.LOCATION, HttpHeaders.WWW_AUTHENTICATE);
    }

    @ParameterizedTest(name = "native unwritable context={0}, exception={1}")
    @MethodSource("unwritableNativeErrors")
    void shouldKeepNativeStatusAndHeadersWhenAdministrativeJsonCannotBeWritten(String context, Exception exception) {
        var request = request(context, "/admin/products/9");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE);
        var nativeError = (org.springframework.web.ErrorResponse) exception;
        var response = assertDoesNotThrow(() -> handler.handleOrderProcessing(exception, request));
        assertThat(response.getStatusCode()).isEqualTo(nativeError.getStatusCode());
        assertThat(response.getHeaders()).isEqualTo(nativeError.getHeaders());
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders()).doesNotContainKeys(HttpHeaders.LOCATION, HttpHeaders.WWW_AUTHENTICATE);
    }

    static Stream<Arguments> unwritableNativeErrors() {
        return Stream.of("", "/api").flatMap(context -> nativeClientErrors()
                .filter(exception -> !(exception instanceof NoResourceFoundException))
                .map(exception -> Arguments.of(context, exception)));
    }

    @ParameterizedTest(name = "native outside admin path={0}, exception={1}")
    @MethodSource("nativeErrorsOutsideAdmin")
    void shouldRethrowTheSameNativeErrorsOutsideTheAdministrativeContract(String path, Exception exception) {
        assertThatThrownBy(() -> handler.handleOrderProcessing(exception, request("/api", path))).isSameAs(exception);
    }

    static Stream<Arguments> nativeErrorsOutsideAdmin() {
        return Stream.of("/products/9", "/orders", "/administrator/products/9")
                .flatMap(path -> nativeClientErrors().map(exception -> Arguments.of(path, exception)));
    }

    static Stream<Exception> nativeClientErrors() {
        return Stream.of(new HttpRequestMethodNotSupportedException("PATCH", List.of("GET")),
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)),
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)),
                new NoResourceFoundException(HttpMethod.GET, PRIVATE_DETAIL));
    }

    @ParameterizedTest(name = "validation context={0}, depth={1}, failure={2}")
    @MethodSource("wrappedAdministrativeFailures")
    void shouldTranslateOnlyKnownGuardCausesThroughValidationWrappers(String context, int depth, Failure failure) {
        Exception exception = failure.exception();
        for (int i = 0; i < depth; i++) {
            exception = new ValidationException(PRIVATE_DETAIL, exception);
        }
        var wrapped = exception;

        var response = assertDoesNotThrow(() -> responseFor(wrapped, request(context, "/admin/products/9")));

        assertEnvelope(response, failure.status, failure.adminMessage, context + "/admin/products/9", true);
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo(failure.status == 401 ? "Bearer" : null);
    }

    static Stream<Arguments> wrappedAdministrativeFailures() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(1, 2)
                .flatMap(depth -> Stream.of(Failure.values()).map(failure -> Arguments.of(context, depth, failure))));
    }

    @ParameterizedTest(name = "internal context={0}, lookup={1}, wrapped={2}, deep cause={3}")
    @MethodSource("internalFailuresWithSecurityCauses")
    void shouldNeverReclassifyAnInternalFailureFromItsDeepSecurityCause(
            String context, boolean lookup, boolean wrapped, Failure securityCause) {
        var deepCause = new ValidationException(PRIVATE_DETAIL, securityCause.exception());
        Exception internal = lookup ? new OrderAccountResolutionException(PRIVATE_DETAIL, deepCause)
                : new IllegalStateException(PRIVATE_DETAIL, deepCause);
        var exception = wrapped ? new ValidationException(PRIVATE_DETAIL, internal) : internal;

        var response = assertDoesNotThrow(() -> responseFor(exception, request(context, "/admin/products/9")));

        assertEnvelope(response, 500, Failure.PROCESSING.adminMessage, context + "/admin/products/9", true);
        assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
    }

    static Stream<Arguments> internalFailuresWithSecurityCauses() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(false, true)
                .flatMap(lookup -> Stream.of(false, true).flatMap(wrapped ->
                        Stream.of(Failure.AUTHENTICATION, Failure.PERMISSION)
                                .map(cause -> Arguments.of(context, lookup, wrapped, cause)))));
    }

    @ParameterizedTest
    @MethodSource("outsideWrappedAdministrativeNamespace")
    void shouldLeaveValidationWrappersOutsideAdminUntranslated(String path, Failure failure) {
        var exception = new ValidationException(PRIVATE_DETAIL, failure.exception());

        assertThatThrownBy(() -> responseFor(exception, request("/api", path))).isSameAs(exception);
    }

    static Stream<Arguments> outsideWrappedAdministrativeNamespace() {
        return Stream.of("/products/9", "/login", "/administrator/products/9")
                .flatMap(path -> Stream.of(Failure.AUTHENTICATION, Failure.PERMISSION)
                        .map(failure -> Arguments.of(path, failure)));
    }

    @ParameterizedTest
    @MethodSource("securityCauses")
    void shouldKeepWrappedOrderFailuresAsSafeInternalErrors(Failure failure) {
        var exception = new ValidationException(PRIVATE_DETAIL, failure.exception());

        var response = assertDoesNotThrow(() -> responseFor(exception, request("/api", "/orders")));

        assertEnvelope(response, 500, "Order registration could not be completed", "/api/orders", false);
        assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
    }

    @ParameterizedTest
    @MethodSource("securityCauses")
    void shouldNotTranslateAValidationWrapperBasedOnTheContextName(Failure failure) {
        var exception = new ValidationException(PRIVATE_DETAIL, failure.exception());

        assertThatThrownBy(() -> responseFor(exception, request("/admin", "/products/9"))).isSameAs(exception);
    }

    static Stream<Failure> securityCauses() {
        return Stream.of(Failure.AUTHENTICATION, Failure.PERMISSION);
    }

    @ParameterizedTest
    @MethodSource("administrativeContexts")
    void shouldKeepValidationWithoutAKnownCauseAsSafeInternalFailure(String context) {
        var exception = new ValidationException(PRIVATE_DETAIL);

        var response = assertDoesNotThrow(() -> responseFor(exception, request(context, "/admin/products/9")));

        assertEnvelope(response, 500, Failure.PROCESSING.adminMessage, context + "/admin/products/9", true);
        assertThat(response.getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
    }

    static Stream<String> administrativeContexts() {
        return Stream.of("", "/api");
    }

    private ResponseEntity<?> responseFor(Exception exception, HttpServletRequest request) throws Exception {
        if (exception instanceof BadCredentialsException credentials) {
            return handler.handleOrderAuthentication(credentials, request);
        }
        if (exception instanceof AccessDeniedException denied) {
            return handler.handleOrderAccess(denied, request);
        }
        return handler.handleOrderProcessing(exception, request);
    }

    private static MockHttpServletRequest request(String context, String path) {
        var request = new MockHttpServletRequest("POST", context + path);
        request.setContextPath(context);
        request.setServletPath(path);
        request.setQueryString("private=" + PRIVATE_DETAIL);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + PRIVATE_DETAIL);
        return request;
    }

    private void assertEnvelope(ResponseEntity<?> response, int status, String message, String path, boolean admin) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        var expected = mapper.createObjectNode();
        expected.put("status", status);
        expected.put("error", HttpStatus.valueOf(status).getReasonPhrase());
        expected.put("message", message);
        expected.put("path", path);
        if (admin) {
            expected.putArray("errors");
        }
        JsonNode actual = mapper.valueToTree(response.getBody());
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.toString()).doesNotContain(PRIVATE_DETAIL);
    }

    enum Failure {
        AUTHENTICATION(401, "Authentication required or invalid"),
        PERMISSION(403, "Product maintenance not permitted"),
        RESOLUTION(500, "Product maintenance could not be completed"),
        PROCESSING(500, "Product maintenance could not be completed");
        final int status;
        final String adminMessage;
        Failure(int status, String adminMessage) { this.status = status; this.adminMessage = adminMessage; }

        Exception exception() {
            return switch (this) {
                case AUTHENTICATION -> new BadCredentialsException(PRIVATE_DETAIL);
                case PERMISSION -> new AccessDeniedException(PRIVATE_DETAIL);
                case RESOLUTION -> new OrderAccountResolutionException(PRIVATE_DETAIL,
                        new IllegalStateException(PRIVATE_DETAIL));
                case PROCESSING -> new IllegalStateException(PRIVATE_DETAIL);
            };
        }
    }
}
