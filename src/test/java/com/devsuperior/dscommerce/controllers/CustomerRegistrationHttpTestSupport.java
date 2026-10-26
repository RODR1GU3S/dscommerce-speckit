package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ControlledClock;
import com.devsuperior.dscommerce.dto.LoginRequestDTO;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Account;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.ConfigurableWebApplicationContext;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;

/**
 * T029 foundation only: no test methods, future registration endpoint or application replacements.
 * Concrete tests opt in with CustomerRegistrationTestConfig.RootHttpContext or ApiHttpContext;
 * this base registers no Spring context, security mock or transaction around requests/workers.
 * Finish/join every request worker in the scenario's finally block before inherited fixture cleanup.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class CustomerRegistrationHttpTestSupport {

    @Autowired protected ConfigurableWebApplicationContext context;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected ControlledClock clock;
    @Autowired protected DataSource dataSource;
    @Autowired private JwtDecoder decoder;
    @LocalServerPort protected int port;

    protected CustomerRegistrationDatabaseProbe database;
    protected CustomerRegistrationTestFixture fixture;

    private HttpClient http;
    private String databaseUrl;

    protected abstract String expectedContextPath();

    @BeforeAll
    protected final void prepareCustomerRegistrationHttpTransport() throws SQLException {
        assertNoCallerTransaction();
        var environment = context.getEnvironment();
        require(environment.acceptsProfiles(Profiles.of("test")), "HTTP support requires the test profile");
        require(environment.getPropertySources().contains(CustomerRegistrationTestConfig.PROPERTY_SOURCE_NAME),
                "HTTP support requires the explicit registration initializer");
        require("".equals(expectedContextPath()) || "/api".equals(expectedContextPath()),
                "Use a root or /api registration context");
        require(context.getServletContext() != null
                        && expectedContextPath().equals(context.getServletContext().getContextPath()),
                "Unexpected registration servlet context path");
        require((expectedContextPath().isEmpty() ? "root" : "api").equals(
                        environment.getRequiredProperty("feature009.customer-registration-test-context")),
                "Unexpected registration context identity");
        require("create-drop".equals(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")),
                "Registration tests require create-drop");
        require("never".equals(environment.getRequiredProperty("spring.sql.init.mode"))
                        && environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files").isEmpty(),
                "Registration tests must disable SQL seeds and Hibernate imports");
        require("false".equals(environment.getRequiredProperty("spring.jpa.open-in-view")),
                "Registration tests require OSIV disabled");
        require(context.getBean(Clock.class) == clock, "Use the context's own controlled Clock");
        require(port > 0, "Use a real embedded server on a random port");

        databaseUrl = environment.getRequiredProperty("spring.datasource.url");
        database = new CustomerRegistrationDatabaseProbe(databaseUrl);
        try (var pooled = dataSource.getConnection(); var independent = database.openConnection()) {
            assertRegistrationConnection(pooled);
            require(pooled.getAutoCommit(), "Setup connection must have no enclosing transaction");
            assertRegistrationConnection(independent);
            require(sessionId(pooled) != sessionId(independent), "Probe must use an independent physical connection");
        }
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @BeforeEach
    protected final void prepareCustomerRegistrationFixture() {
        assertNoCallerTransaction();
        clock.setInstant(CustomerRegistrationTestConfig.INITIAL_INSTANT);
        // Construction writes nothing. Setup commits through the fixture's own REQUIRES_NEW template.
        fixture = new CustomerRegistrationTestFixture(context);
    }

    @AfterEach
    protected final void cleanCustomerRegistrationFixture() {
        try {
            if (fixture != null) { fixture.close(); }
        } finally {
            fixture = null;
            if (clock != null) { clock.setInstant(CustomerRegistrationTestConfig.INITIAL_INSTANT); }
        }
    }

    @AfterAll
    protected final void closeCustomerRegistrationHttpTransport() {
        try {
            if (http != null) { http.close(); }
        } finally {
            http = null;
        }
    }

    /** No login during setup: each scenario explicitly chooses whether/when to obtain a real JWT. */
    protected final String realHttpLogin(Account account) {
        return realHttpLogin(account.id(), account.name(), account.password());
    }

    /** Also accepts a newly committed registration; password is serialized exactly, never normalized. */
    protected final String realHttpLogin(long accountId, String name, String password) {
        require(accountId > 0, "Real HTTP login requires a committed account ID");
        var response = send(jsonRequest("/login").POST(HttpRequest.BodyPublishers.ofByteArray(
                jsonBytes(new LoginRequestDTO(name, password)))).build());
        require(response.statusCode() == 200, "Real HTTP fixture login must return 200");
        try {
            var token = mapper.readValue(response.body(), TokenResponseDTO.class);
            require(token != null && "Bearer".equals(token.tokenType())
                            && token.accessToken() != null && !token.accessToken().isBlank(),
                    "Real HTTP login must return a bearer credential");
            require(Long.toString(accountId).equals(decoder.decode(token.accessToken()).getSubject()),
                    "Real HTTP login must identify the committed account");
            return token.accessToken();
        } catch (IOException | JwtException failure) {
            // Neither body, JWT, credentials nor parser/decoder causes belong in diagnostics.
            throw new AssertionError("Real HTTP fixture login could not be verified");
        }
    }

    /** Builder stays configurable for the scenario's method, bearer, headers and raw/malformed body. */
    protected final HttpRequest.Builder jsonRequest(String relativePath) {
        return HttpRequest.newBuilder(uri(relativePath)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("Content-Type", "application/json");
    }

    protected final URI uri(String relativePath) {
        require(relativePath != null && relativePath.startsWith("/") && !relativePath.startsWith("//"),
                "Use a path relative to the selected servlet context");
        try {
            var target = URI.create("http://localhost:" + port + expectedContextPath() + relativePath);
            requireLocalTarget(target);
            return target;
        } catch (IllegalArgumentException failure) {
            throw new AssertionError("Invalid local HTTP test path");
        }
    }

    /** Actual responses, including non-2xx, remain available for exact assertions in future scenarios. */
    protected final HttpResponse<byte[]> send(HttpRequest request) {
        assertNoCallerTransaction();
        require(http != null, "Initialize the real registration HTTP transport first");
        requireLocalTarget(request.uri());
        require(request.timeout().isPresent(), "Every HTTP request needs a finite timeout");
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Registration test HTTP request interrupted");
        } catch (IOException failure) {
            throw new AssertionError("Registration test HTTP transport did not complete");
        } finally {
            assertNoCallerTransaction();
        }
    }

    /** Safe serialization/parsing failures never attach a DTO, body or Jackson source/cause. */
    protected final byte[] jsonBytes(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (IOException failure) {
            throw new AssertionError("HTTP test JSON could not be serialized");
        }
    }

    protected final JsonNode jsonBody(HttpResponse<byte[]> response) {
        try {
            return mapper.readTree(response.body());
        } catch (IOException failure) {
            throw new AssertionError("HTTP test response must contain valid JSON");
        }
    }

    /** Opens a fresh driver connection, never an EntityManager/cache or a caller-bound connection. */
    protected final Connection openIndependentConnection() throws SQLException {
        assertNoCallerTransaction();
        return database.openConnection();
    }

    protected final void assertRegistrationConnection(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        require(databaseUrl.equals(context.getEnvironment().getRequiredProperty("spring.datasource.url"))
                        && databaseUrl.split(";", 2)[0].equals(metadata.getURL()),
                "Keep the registration database isolated and stable");
        require("H2".equals(metadata.getDatabaseProductName())
                        && metadata.getDatabaseProductVersion().startsWith("2.2.224"),
                "Use the approved H2 runtime");
        require(connection.getTransactionIsolation() == Connection.TRANSACTION_READ_COMMITTED,
                "Observe actual READ_COMMITTED isolation");
    }

    /** Checks only the calling thread; server/worker transaction and commit proofs need observation. */
    protected final void assertNoCallerTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive()
                        && !TransactionSynchronizationManager.isSynchronizationActive()
                        && !TransactionSynchronizationManager.hasResource(dataSource),
                "HTTP requests and workers must have no enclosing test transaction");
    }

    private void requireLocalTarget(URI target) {
        var path = target.normalize().getPath();
        require("http".equals(target.getScheme()) && "localhost".equals(target.getHost())
                        && target.getPort() == port && target.getUserInfo() == null && target.getFragment() == null
                        && path != null && (expectedContextPath().isEmpty()
                        || path.startsWith(expectedContextPath() + "/")),
                "Send HTTP only to the selected local registration server and context");
    }

    private static long sessionId(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select session_id()")) {
            require(rows.next(), "Expected an H2 session identity");
            long id = rows.getLong(1);
            require(id > 0 && !rows.next(), "Expected exactly one positive H2 session identity");
            return id;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
