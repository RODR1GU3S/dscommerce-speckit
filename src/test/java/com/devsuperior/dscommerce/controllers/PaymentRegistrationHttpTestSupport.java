package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.config.OrderTestConfig.ControlledClock;
import com.devsuperior.dscommerce.dto.LoginRequestDTO;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Foundation only: no test methods, payment types, fixture rows or SQL observation.
 * Concrete tests explicitly select PaymentRegistrationTestConfig.RootHttpContext or ApiHttpContext.
 * Future fixtures own setup/cleanup; request-thread transaction observation belongs to T005+.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class PaymentRegistrationHttpTestSupport {

    @Autowired protected WebApplicationContext context;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected ControlledClock clock;
    @Autowired protected DataSource dataSource;
    @Autowired private JwtDecoder decoder;
    @LocalServerPort protected int port;

    private HttpClient http;
    private String databaseUrl;

    protected abstract String expectedContextPath();

    @BeforeAll
    protected final void preparePaymentHttpTransport() throws SQLException {
        assertNoExternalTransaction();
        var environment = context.getEnvironment();
        assertThat(expectedContextPath()).isIn("", "/api");
        assertThat(context.getServletContext()).isNotNull();
        assertThat(context.getServletContext().getContextPath()).isEqualTo(expectedContextPath());
        assertThat(environment.getRequiredProperty("feature008.payment-test-context"))
                .isEqualTo(expectedContextPath().isEmpty() ? "root" : "api");
        assertThat(environment.getRequiredProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getRequiredProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
        assertThat(port).as("Real embedded server on a random port").isPositive();
        databaseUrl = environment.getRequiredProperty("spring.datasource.url");
        assertThat(databaseUrl).startsWith("jdbc:h2:mem:admin-product-test-");
        try (var pooled = dataSource.getConnection(); var independent = openIndependentConnection()) {
            assertPaymentConnection(pooled);
            assertThat(pooled.getAutoCommit()).isTrue();
            assertPaymentConnection(independent);
        }
        assertThat(environment.getRequiredProperty("spring.datasource.url")).isEqualTo(databaseUrl);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @BeforeEach
    protected final void resetPaymentReference() {
        assertNoExternalTransaction();
        clock.setInstant(OrderTestConfig.INITIAL_INSTANT);
    }

    @AfterAll
    protected final void closePaymentHttpTransport() {
        try {
            if (http != null) { http.close(); }
        } finally {
            http = null;
            if (clock != null) { clock.setInstant(OrderTestConfig.INITIAL_INSTANT); }
        }
    }

    /** Account setup must already be committed by the caller's owned fixture. No MockMvc login. */
    protected final String realHttpLogin(long accountId, String name, String password) {
        assertThat(accountId).isPositive();
        try {
            var body = mapper.writeValueAsBytes(new LoginRequestDTO(name, password));
            var response = send(jsonRequest("/login").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
            assertThat(response.statusCode()).as("Real HTTP fixture login").isEqualTo(200);
            var token = mapper.readValue(response.body(), TokenResponseDTO.class);
            assertThat(token.tokenType()).isEqualTo("Bearer");
            assertThat(token.accessToken()).isNotBlank();
            assertThat(decoder.decode(token.accessToken()).getSubject()).isEqualTo(Long.toString(accountId));
            return token.accessToken();
        } catch (IOException failure) {
            throw new AssertionError("Real HTTP fixture login must return a signed JWT", failure);
        }
    }

    protected final HttpRequest.Builder jsonRequest(String relativePath) {
        assertThat(relativePath).startsWith("/");
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + expectedContextPath() + relativePath))
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    /** Returns the actual status/body; each future scenario must assert its own expected outcome. */
    protected final HttpResponse<byte[]> send(HttpRequest request) {
        assertNoExternalTransaction();
        assertThat(http).as("Initialized real HTTP transport").isNotNull();
        assertThat(request.timeout()).as("Every request must have a finite timeout").isPresent();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Payment test HTTP request interrupted", failure);
        } catch (IOException failure) {
            throw new AssertionError("Payment test HTTP transport did not complete", failure);
        } finally {
            assertNoExternalTransaction();
        }
    }

    /** The caller must close this independent physical JDBC connection with try-with-resources. */
    protected final Connection openIndependentConnection() throws SQLException {
        assertNoExternalTransaction();
        var environment = context.getEnvironment();
        var connection = DriverManager.getConnection(databaseUrl,
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"));
        try {
            assertPaymentConnection(connection);
            assertThat(connection.getAutoCommit()).isTrue();
            return connection;
        } catch (SQLException | RuntimeException | Error failure) {
            try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    /** Use for pooled, independent and (later) observed business connections. Does not set isolation. */
    protected final void assertPaymentConnection(Connection connection) throws SQLException {
        assertThat(connection.getMetaData().getURL()).isEqualTo(databaseUrl.split(";", 2)[0]);
        assertThat(connection.getTransactionIsolation()).as("Actual JDBC READ_COMMITTED")
                .isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
    }

    /**
     * Checks the CURRENT thread. Calling before send proves only the test caller's state.
     * Future server instrumentation must call this before the service transaction interceptor too.
     */
    protected final void assertNoExternalTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    }

    /** Invoke from T005+ on the request thread INSIDE the real transaction interceptor. */
    protected final void assertOwnedPaymentTransaction(Connection connection) throws SQLException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
        assertThat(TransactionAspectSupport.currentTransactionStatus().isNewTransaction()).isTrue();
        assertThat(DataSourceUtils.isConnectionTransactional(connection, dataSource)).isTrue();
        assertThat(connection.getAutoCommit()).isFalse();
        assertPaymentConnection(connection);
    }

    /**
     * Deliberately not called during foundation setup. Absence of the future service must fail,
     * never pass via an optional lookup, mock, fake bean or direct construction of its target.
     * Proxy structure alone does not establish a physical transaction or a successful commit.
     */
    protected final Advised requirePaymentServiceProxy() {
        var service = context.getBean("paymentRegistrationService");
        assertThat(AopUtils.getTargetClass(service).getName())
                .isEqualTo("com.devsuperior.dscommerce.services.PaymentRegistrationService");
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(service).isInstanceOf(Advised.class);
        var proxy = (Advised) service;
        assertThat(Arrays.stream(proxy.getAdvisors())
                .anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor)).isTrue();
        return proxy;
    }
}
