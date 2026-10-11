package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** PR #4: real HTTP, login, security and service; observe framework errors without resolving them. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.jpa.open-in-view=false", "server.address=127.0.0.1", "logging.level.root=WARN"})
@ActiveProfiles("test")
@Import({OrderTestConfig.class, OrderFrameworkHttpErrorIntegrationTest.ObservationConfig.class})
class OrderFrameworkHttpErrorIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderTestFixture fixture;
    @Autowired private OrderTestConfig.ControlledClock clock;
    @Autowired private FrameworkErrorObserver observer;
    private JdbcTemplate independent;
    private Long buyer;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void prepareIndependentObservation() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        assertThat(url).startsWith("jdbc:h2:mem:order-test-");
        independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertThat(independent.getDataSource()).isNotSameAs(jdbc.getDataSource());
        clock.setInstant(Instant.now());
        observer.seen.set(null);
    }

    @AfterEach
    void cleanOnlyOwnedFixturesAfterObservation() {
        if (buyer != null) {
            jdbc.update("delete from tb_order_item where order_id in (select id from tb_order where buyer_id = ?)", buyer);
            jdbc.update("delete from tb_order where buyer_id = ?", buyer);
        }
        fixture.cleanUp();
        observer.seen.set(null);
    }

    @Test
    void shouldPreserveUnsupportedMediaTypeStatusForAnAuthorizedOrderWithoutWriting() throws Exception {
        var name = "framework-http-" + UUID.randomUUID();
        var password = UUID.randomUUID().toString();
        buyer = fixture.createAccount(name, password, Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("framework HTTP product", new BigDecimal("10.00"));
        assertThat(independent.queryForList("select role from tb_user_account_role where user_account_id = ?",
                String.class, buyer)).containsExactly("CLIENT");

        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            var loginBody = mapper.createObjectNode().put("name", name).put("password", password).toString();
            var login = post(client, "/login", "application/json", loginBody, null);
            assertThat(login.statusCode()).as("real login preparation, not the investigated behavior").isEqualTo(200);
            var loginJson = mapper.readTree(login.body());
            assertThat(loginJson.path("tokenType").asText()).isEqualTo("Bearer");
            var token = loginJson.path("accessToken").asText();
            assertThat(token.isBlank()).isFalse(); // Never print credentials, token or response containing a token.
            clock.setInstant(Instant.now());
            var body = "{\"items\":[{\"productId\":" + product + ",\"quantity\":2}]}";

            // Same real JWT/account/body, only Content-Type differs in the investigated request.
            var control = post(client, "/orders", "application/json", body, token);
            assertThat(control.statusCode()).as("security and valid order positive control").isEqualTo(201);
            var confirmation = mapper.readTree(control.body());
            assertThat(confirmation.path("accountId").asLong()).isEqualTo(buyer);
            assertThat(confirmation.path("total").asText()).isEqualTo("20.00");
            var before = counts();
            assertThat(before).isEqualTo(new Counts(1, 1));
            observer.seen.set(null);

            var response = post(client, "/orders", "text/plain", body, token);
            var after = counts(); // Fresh JDBC connection, before cleanup and before the failing status assertion.
            var observation = observer.seen.get();
            System.out.printf("ORDER_FRAMEWORK_HTTP control=%d actual=%d expected=415 exception=%s "
                            + "frameworkStatus=%d authorized=%s controller=%s beforeOrders=%d beforeItems=%d "
                            + "afterOrders=%d afterItems=%d independent=true%n",
                    control.statusCode(), response.statusCode(), observation == null ? "NONE" : observation.exception(),
                    observation == null ? -1 : observation.frameworkStatus(),
                    observation != null && observation.authorized(), observation == null ? "NONE" : observation.controller(),
                    before.orders(), before.items(), after.orders(), after.items());
            assertAll("framework status and no writes, after real authenticated HTTP",
                    () -> assertThat(observation).as("real MVC exception, not a security/preparation error").isNotNull(),
                    () -> assertThat(observation).isEqualTo(new Observation(
                            "HttpMediaTypeNotSupportedException", 415, true, "OrderController.register")),
                    () -> assertThat(after).as("no new committed order or item").isEqualTo(before),
                    () -> assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(),
                    () -> assertThat(response.statusCode()).as("preserve HTTP 415 Unsupported Media Type").isEqualTo(415));
        }
    }

    private HttpResponse<String> post(HttpClient client, String path, String contentType, String body, String token)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", contentType).header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Counts counts() {
        return independent.execute((ConnectionCallback<Counts>) connection -> {
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(DataSourceUtils.isConnectionTransactional(connection, independent.getDataSource())).isFalse();
            long orders;
            long items;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select count(*) from tb_order")) {
                rows.next();
                orders = rows.getLong(1);
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select count(*) from tb_order_item")) {
                rows.next();
                items = rows.getLong(1);
            }
            return new Counts(orders, items);
        });
    }

    record Counts(long orders, long items) { }
    record Observation(String exception, int frameworkStatus, boolean authorized, String controller) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean FrameworkErrorObserver frameworkErrorObserver() { return new FrameworkErrorObserver(); }
    }

    /** Return null so the existing production exception resolvers decide the response unchanged. */
    static class FrameworkErrorObserver implements HandlerExceptionResolver, Ordered {
        private final AtomicReference<Observation> seen = new AtomicReference<>();

        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                             Object handler, Exception exception) {
            if ("POST".equals(request.getMethod()) && "/orders".equals(request.getRequestURI())
                    && exception instanceof HttpMediaTypeNotSupportedException frameworkError) {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                var authorized = authentication != null && authentication.isAuthenticated()
                        && authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal
                        && authentication.getAuthorities().stream().anyMatch(authority ->
                        Set.of("ROLE_CLIENT", "ROLE_ADMIN").contains(authority.getAuthority()));
                var controller = handler instanceof HandlerMethod method
                        ? method.getBeanType().getSimpleName() + "." + method.getMethod().getName() : "NONE";
                seen.set(new Observation(exception.getClass().getSimpleName(),
                        frameworkError.getStatusCode().value(), authorized, controller));
            }
            return null;
        }
    }
}
