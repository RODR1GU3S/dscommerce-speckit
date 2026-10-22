package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRequestJsonConfig.PaymentRegistrationRequestDeserializer;
import com.devsuperior.dscommerce.controllers.handlers.PaymentRegistrationExceptionHandler;
import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.dto.PaymentRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.PaymentRegistrationService;
import com.devsuperior.dscommerce.services.exceptions.PaymentRegistrationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T015 local MVC/Bean Validation only: no security filters, HTTP socket, database or settlement transaction. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaymentBoundaryValidationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ObjectMapper mapper;
    private Validator validator;
    private PaymentRegistrationService service;
    private final Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedAccountPrincipal(42L), null, List.of());

    @BeforeAll
    void prepareOnlyTheRealControllerReaderAdviceAndNativeMvcValidation() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(MvcConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        service = context.getBean(PaymentRegistrationService.class);
        mapper = context.getBean(RequestMappingHandlerAdapter.class).getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast).findFirst().orElseThrow().getObjectMapper();
        validator = context.getBean(Validator.class);
    }

    @BeforeEach
    void resetOnlyTheLocalServiceMock() { reset(service); }

    @AfterAll
    void closeOnlyThisMvcContext() { if (context != null) { context.close(); } }

    @ParameterizedTest(name = "local invalid document {index}")
    @MethodSource("com.devsuperior.dscommerce.controllers.PaymentInputCases#invalidDocuments")
    void localReaderOrBeanValidationRejectsEveryInvalidDocumentBeforeService(String input) throws Exception {
        assertError(perform("71", input), 400, "Invalid payment data");
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "local invalid occurrence {index}")
    @MethodSource("com.devsuperior.dscommerce.controllers.PaymentInputCases#invalidOccurrences")
    void localTemporalGrammarFailureIs400WithoutCallingService(String text) throws Exception {
        var result = perform("71", "{\"occurredAt\":" + mapper.writeValueAsString(text) + "}");
        assertError(result, 400, "Invalid payment data");
        assertThat(result.getResolvedException()).isInstanceOf(HttpMessageNotReadableException.class);
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "lossless local input/output {index}")
    @MethodSource("com.devsuperior.dscommerce.controllers.PaymentInputCases#acceptedOccurrences")
    void everyAcceptedR3FormBindsToTheExactInstantAndHasCanonicalConfirmation(String text, String canonical)
            throws Exception {
        var instant = Instant.parse(canonical);
        when(service.register(eq(Long.MAX_VALUE), any(), same(authentication)))
                .thenReturn(new PaymentConfirmationDTO(Long.MAX_VALUE, instant, OrderStatus.PAID));
        var result = perform(Long.toString(Long.MAX_VALUE), "{\"occurredAt\":" + mapper.writeValueAsString(text) + "}");
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getResponse().getHeader("Location")).isNull();
        var expected = mapper.createObjectNode().put("orderId", Long.MAX_VALUE)
                .put("occurredAt", canonical).put("status", "PAID");
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(expected);
        verify(service).register(Long.MAX_VALUE, new PaymentRegistrationRequestDTO(instant), authentication);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nativePositiveValidationRejectsIdsBeforeTheControllerServiceCall(String id) throws Exception {
        var result = perform(id, PaymentInputCases.GOOD);
        assertError(result, 400, "Invalid payment data");
        assertThat(result.getResolvedException()).isInstanceOf(HandlerMethodValidationException.class);
        assertThat(((HandlerMethodValidationException) result.getResolvedException()).isForReturnValue()).isFalse();
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"occurredAt\":null}"})
    void actualValidAndNotNullRejectMissingOccurrenceBeyondSuccessfulDeserialization(String input) throws Exception {
        var dto = mapper.readValue(input, PaymentRegistrationRequestDTO.class);
        assertThat(dto.occurredAt()).isNull();
        assertThat(validator.validate(dto)).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("occurredAt");
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(NotNull.class);
        });
        var result = perform("71", input);
        assertError(result, 400, "Invalid payment data");
        assertThat(result.getResolvedException()).isInstanceOfAny(
                MethodArgumentNotValidException.class, HandlerMethodValidationException.class);
        verifyNoInteractions(service);
    }

    @Test
    void temporalInput400AndInvalidInternalConfirmation500RemainSeparate() throws Exception {
        assertError(perform("71", "{\"occurredAt\":\"2026-10-21T11:00:00.123000001Z\"}"),
                400, "Invalid payment data");
        verifyNoInteractions(service);
        when(service.register(eq(71L), any(), same(authentication))).thenAnswer(call ->
                new PaymentConfirmationDTO(71L, Instant.parse("2026-10-21T11:00:00.123000001Z"), OrderStatus.PAID));
        assertError(perform("71", PaymentInputCases.GOOD), 500, "Payment registration could not be completed");
        verify(service).register(71L, new PaymentRegistrationRequestDTO(Instant.parse(PaymentInputCases.OCCURRENCE)),
                authentication);
        verifyNoMoreInteractions(service);
    }

    @Test
    void realReturnValidationExceptionMetadataIsInternal500RatherThanInput400() throws Exception {
        var method = registrationMethod();
        var result = new ParameterValidationResult(new MethodParameter(method, -1), null,
                List.of(new DefaultMessageSourceResolvable("private return detail")), null, null, null);
        var failure = new HandlerMethodValidationException(MethodValidationResult.create(
                context.getBean(AdminPaymentController.class), method, List.of(result)));
        assertThat(failure.isForReturnValue()).isTrue();
        when(service.register(eq(71L), any(), same(authentication))).thenThrow(failure);
        assertError(perform("71", PaymentInputCases.GOOD), 500, "Payment registration could not be completed");
        verify(service).register(eq(71L), any(), same(authentication));
        verifyNoMoreInteractions(service);
        // Controlled return-validation metadata, not a claim that the current return type generates it automatically.
    }

    @Test
    void persistentEntityValidationIsInternal500RatherThanConsumerInput400() throws Exception {
        when(service.register(eq(71L), any(), same(authentication)))
                .thenThrow(new jakarta.validation.ConstraintViolationException("private entity detail", Set.of()));
        assertError(perform("71", PaymentInputCases.GOOD), 500, "Payment registration could not be completed");
        verify(service).register(eq(71L), any(), same(authentication));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @CsvSource({"ALREADY_REGISTERED,409,Payment already registered", "INCOMPATIBLE_STATE,409,Order is not awaiting payment",
            "PROCESSING_FAILURE,500,Payment registration could not be completed"})
    void repetitionIncompatibilityAndInternalFailureKeepDistinctSafeCategories(String reason, int status, String message)
            throws Exception {
        when(service.register(eq(71L), any(), same(authentication)))
                .thenThrow(new PaymentRegistrationException(PaymentRegistrationException.Reason.valueOf(reason)));
        assertError(perform("71", PaymentInputCases.GOOD), status, message);
        verify(service).register(eq(71L), any(), same(authentication));
        verifyNoMoreInteractions(service);
    }

    @Test
    void controllerAnnotationsAndPureLocalParsingAndValidationNeedNoDomainIo() throws Exception {
        var method = registrationMethod();
        assertThat(method.getParameters()[0].getAnnotation(Positive.class)).isNotNull();
        assertThat(method.getParameters()[1].getAnnotation(Valid.class)).isNotNull();
        var constraints = validator.getConstraintsForClass(PaymentRegistrationRequestDTO.class)
                .getConstraintsForProperty("occurredAt").getConstraintDescriptors();
        assertThat(constraints).singleElement().satisfies(descriptor ->
                assertThat(descriptor.getAnnotation()).isInstanceOf(NotNull.class));
        var parsed = mapper.readValue(PaymentInputCases.GOOD, PaymentRegistrationRequestDTO.class);
        assertThat(parsed.occurredAt()).isEqualTo(Instant.parse(PaymentInputCases.OCCURRENCE));
        assertThat(validator.validate(parsed)).isEmpty();
        assertThat(context.getBeanNamesForType(javax.sql.DataSource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(jakarta.persistence.EntityManagerFactory.class)).isEmpty();
        assertThat(context.getBeanNamesForType(org.springframework.data.repository.Repository.class)).isEmpty();
        assertThat(context.getBeanNamesForType(java.time.Clock.class)).isEmpty();
        assertThat(context.containsBean("springSecurityFilterChain")).isFalse();
        verifyNoInteractions(service);
        // Together with inspection of the production reader and @NotNull, this bounds domain IO; not arbitrary network IO tracing.
    }

    private Method registrationMethod() throws NoSuchMethodException {
        return AdminPaymentController.class.getMethod("register", Long.class, PaymentRegistrationRequestDTO.class,
                Authentication.class, HttpServletRequest.class);
    }

    private MvcResult perform(String id, String input) throws Exception {
        return mvc.perform(post("/admin/orders/" + id + "/payments").principal(authentication)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(input)).andReturn();
    }

    private void assertError(MvcResult result, int status, String message) throws Exception {
        var response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("Location")).isNull();
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
        assertThat(response.getContentType()).contains("application/json");
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase()).put("message", message)
                .put("path", result.getRequest().getRequestURI());
        assertThat(mapper.readTree(response.getContentAsByteArray())).isEqualTo(expected);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({AdminPaymentController.class, PaymentRegistrationExceptionHandler.class})
    static class MvcConfiguration implements WebMvcConfigurer {
        @Bean PaymentRegistrationService paymentRegistrationService() { return mock(PaymentRegistrationService.class); }

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            var paymentModule = new SimpleModule().addDeserializer(PaymentRegistrationRequestDTO.class,
                    new PaymentRegistrationRequestDeserializer());
            converters.stream().filter(MappingJackson2HttpMessageConverter.class::isInstance)
                    .map(MappingJackson2HttpMessageConverter.class::cast).forEach(converter ->
                            converter.getObjectMapper().registerModule(new JavaTimeModule()).registerModule(paymentModule));
            // Explicit local registration of the real reader; no application mapper flag is changed.
        }
    }
}
