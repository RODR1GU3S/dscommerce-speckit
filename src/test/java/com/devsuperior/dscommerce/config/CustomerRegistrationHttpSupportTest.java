package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ControlledClock;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.controllers.CustomerRegistrationHttpTestSupport;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T030 authorship only; T031 first executes these inherited cases in both real servlet contexts.
 * Exercises existing endpoints only. No registration endpoint, fake controller or security substitute.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationHttpSupportTest {

    private final Map<String, ContextIdentity> observedContexts = new LinkedHashMap<>();

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @RootHttpContext
    class RootHttpTest extends HttpCases {
        @Override protected String expectedContextPath() { return ""; }
        @Override void recordContext() { remember("", context, clock, port); }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @ApiHttpContext
    class ApiHttpTest extends HttpCases {
        @Override protected String expectedContextPath() { return "/api"; }
        @Override void recordContext() { remember("/api", context, clock, port); }
    }

    private void remember(String path, WebApplicationContext context, ControlledClock clock, int port) {
        assertThat(observedContexts.putIfAbsent(path, new ContextIdentity(context, clock, port))).isNull();
    }

    /** Container lifecycle gate, not a ninth test: discovery must include both nested concrete classes. */
    @AfterAll
    void requiresBothContextsWithDifferentResources() {
        assertThat(observedContexts).containsOnlyKeys("", "/api");
        var root = observedContexts.get("");
        var api = observedContexts.get("/api");
        assertThat(root.context() == api.context()).as("different cached application contexts").isFalse();
        assertThat(root.clock() == api.clock()).as("different controlled timelines").isFalse();
        assertThat(root.port()).isNotEqualTo(api.port());
        assertThat(root.context().getEnvironment().getRequiredProperty("spring.datasource.url"))
                .isNotEqualTo(api.context().getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertThat(root.context().getBean(javax.sql.DataSource.class)
                == api.context().getBean(javax.sql.DataSource.class)).isFalse();
        assertThat(Arrays.equals(root.context().getBean(JwtProperties.class).getSecret(),
                api.context().getBean(JwtProperties.class).getSecret()))
                .as("disposable JWT keys belong to separate contexts, without printing them").isFalse();
        var rootBefore = root.clock().instant();
        var apiBefore = api.clock().instant();
        try {
            root.clock().advance(Duration.ofDays(1));
            assertThat(api.clock().instant()).isEqualTo(apiBefore);
        } finally {
            root.clock().setInstant(rootBefore);
        }
    }

    private record ContextIdentity(WebApplicationContext context, ControlledClock clock, int port) {
        @Override public String toString() { return "RegistrationHttpContext[details=omitted]"; }
    }

    abstract static class HttpCases extends CustomerRegistrationHttpTestSupport {

        abstract void recordContext();

        @Test
        void usesTheOptInProfileRealWiringAndContextRelativeUrls() throws SQLException {
            assertNoCallerTransaction();
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).contains("test");
            assertThat(environment.getRequiredProperty("feature009.customer-registration-test-context"))
                    .isEqualTo(expectedContextPath().isEmpty() ? "root" : "api");
            assertThat(context.getServletContext()).isNotNull();
            assertThat(context.getServletContext().getContextPath()).isEqualTo(expectedContextPath());
            assertThat(environment.getRequiredProperty("server.port", Integer.class)).isZero();
            assertThat(port).isPositive();
            var target = uri("/products?page=0&size=1");
            assertThat(target.getHost()).isEqualTo("localhost");
            assertThat(target.getPort()).isEqualTo(port);
            assertThat(target.getPath()).isEqualTo(expectedContextPath() + "/products");
            assertThat(target.getQuery()).isEqualTo("page=0&size=1");
            assertThat(environment.getRequiredProperty("spring.datasource.url")).matches(
                    "jdbc:h2:mem:customer-registration-test-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                            + "[0-9a-f]{4}-[0-9a-f]{12};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
            assertThat(environment.getRequiredProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
            assertThat(environment.getRequiredProperty("spring.jpa.open-in-view")).isEqualTo("false");
            assertThat(context.containsBean("openEntityManagerInViewInterceptor")).isFalse();
            assertThat(context.getBeansOfType(ControlledClock.class)).hasSize(1);
            assertThat(context.getBean(Clock.class) == clock).isTrue();
            assertThat(clock.instant()).isEqualTo(CustomerRegistrationTestConfig.INITIAL_INSTANT);
            assertThat(context.getBeansOfType(PasswordEncoder.class)).hasSize(1);
            assertThat(context.getBean(PasswordEncoder.class) instanceof BCryptPasswordEncoder).isTrue();
            assertThat(context.getBean(JwtEncoder.class) instanceof NimbusJwtEncoder).isTrue();
            assertThat(context.getBean(JwtDecoder.class) instanceof NimbusJwtDecoder).isTrue();
            assertThat(context.getBean(FilterChainProxy.class).getFilterChains()).isNotEmpty();
            assertThat(context.getBeansOfType(CustomerRegistrationTestFixture.class)).isEmpty();
            assertThat(context.getBeansOfType(CustomerRegistrationDatabaseProbe.class)).isEmpty();
            assertThat(fixture.ownership().accountIds()).isEmpty();
            assertEmpty(database.snapshot());
            try (var pooled = dataSource.getConnection(); var independent = openIndependentConnection()) {
                assertRegistrationConnection(pooled);
                assertRegistrationConnection(independent);
                assertThat(pooled.getAutoCommit()).isTrue();
                assertThat(independent.getAutoCommit()).isTrue();
                assertThat(sessionIdOf(independent)).isNotEqualTo(sessionIdOf(pooled));
            }
            recordContext();
        }

        @Test
        void committedFixtureSupportsRealLoginAndRealSecurityFilters() {
            var account = fixture.createAccount("T030-" + UUID.randomUUID().toString().replace("-", ""), LegacyCredential.SHORT,
                    Set.of(UserRole.CLIENT));
            var committed = database.snapshot();
            var token = realHttpLogin(account);
            var decoded = context.getBean(JwtDecoder.class).decode(token);
            assertThat(Long.toString(account.id()).equals(decoded.getSubject())).isTrue();
            assertThat(account.name().equals(decoded.getClaimAsString("name"))).isTrue();
            // Existing token issuance/decoder use wall time; the protected converter uses this test Clock.
            // Align only this context's timeline with the real token's validity, never change production.
            clock.setInstant(decoded.getIssuedAt().plusSeconds(1));
            var path = "/orders?page=0&size=1";
            assertUnauthorized(send(jsonRequest(path).GET().build()));
            assertUnauthorized(send(jsonRequest(path).header("Authorization", "Bearer invalid-t030-token").GET().build()));
            var authorized = send(jsonRequest(path).header("Authorization", "Bearer " + token).GET().build());
            assertThat(authorized.statusCode()).isEqualTo(200);
            var expected = mapper.createObjectNode().put("page", 0).put("size", 1)
                    .put("totalElements", 0).put("totalPages", 0);
            expected.putArray("content");
            assertThat(expected.equals(jsonBody(authorized))).as("exact empty own-order page over real HTTP").isTrue();
            assertThat(authorized.headers().allValues("Set-Cookie")).isEmpty();
            assertUnauthorized(send(jsonRequest(path).GET().build()));
            assertUnchanged(committed, database.snapshot());
            assertNoCallerTransaction();
        }

        @Test
        void httpAndProbeSeeOnlyCommittedChangesFromAnIndependentWorker() throws Exception {
            var product = fixture.createProduct("T030 independent worker", new BigDecimal("10.00"), List.of());
            var before = database.snapshot();
            var changed = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var writerSession = new AtomicLong();
            long callerThread = Thread.currentThread().threadId();
            var jdbc = context.getBean(JdbcTemplate.class);
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            transaction.setTimeout(45);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var writer = executor.submit(() -> {
                    assertNoCallerTransaction();
                    try {
                        return transaction.execute(status -> {
                            assertThat(status.isNewTransaction()).isTrue();
                            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                            var connection = DataSourceUtils.getConnection(dataSource);
                            try {
                                assertThat(DataSourceUtils.isConnectionTransactional(connection, dataSource)).isTrue();
                                assertThat(connection.getAutoCommit()).isFalse();
                                assertRegistrationConnection(connection);
                                writerSession.set(sessionIdOf(connection));
                            } catch (SQLException failure) {
                                throw new AssertionError("Worker connection preparation failed");
                            } finally {
                                DataSourceUtils.releaseConnection(connection, dataSource);
                            }
                            assertThat(jdbc.update("update tb_product set price = ? where id = ?",
                                    new BigDecimal("12.00"), product.id())).isEqualTo(1);
                            changed.countDown();
                            awaitRelease(release);
                            return Thread.currentThread().threadId();
                        });
                    } finally {
                        assertNoCallerTransaction();
                    }
                });
                try (var independent = openIndependentConnection()) {
                    assertThat(changed.await(15, TimeUnit.SECONDS)).as("worker reached its uncommitted write").isTrue();
                    assertThat(writerSession.get()).isPositive().isNotEqualTo(sessionIdOf(independent));
                    assertProbePrice(independent, product.id(), "10.00");
                    assertUnchanged(before, database.snapshot());
                    assertHttpPrice(product.id(), "10.00");
                    assertNoCallerTransaction();
                    release.countDown();
                    assertThat(writer.get(20, TimeUnit.SECONDS)).isNotEqualTo(callerThread);
                    assertProbePrice(independent, product.id(), "12.00");
                    assertHttpPrice(product.id(), "12.00");
                }
            } finally {
                release.countDown();
                executor.shutdownNow();
                boolean terminated = false;
                try {
                    terminated = executor.awaitTermination(10, TimeUnit.SECONDS);
                    assertThat(terminated).as("finish the worker before inherited fixture cleanup").isTrue();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for the controlled worker to finish");
                } finally {
                    // A failed join is a harness failure: retain owned rows rather than delete under a live writer.
                    if (!terminated) { fixture = null; }
                }
            }
            var after = database.snapshot();
            before.tables().forEach((table, rows) -> {
                if (!"TB_PRODUCT".equals(table)) {
                    assertThat(rows.equals(after.tables().get(table))).as("worker preserves %s", table).isTrue();
                }
            });
            assertNoCallerTransaction();
        }

        @Test
        void rejectsAnEnclosingCallerTransactionBeforeHttpOrProbeAccess() {
            var before = database.snapshot();
            var request = jsonRequest("/products?page=0&size=1").GET().build();
            var caller = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            // Negative control only: the helper must reject before any HTTP request/probe is dispatched.
            caller.executeWithoutResult(status -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThatThrownBy(() -> send(request)).isInstanceOf(AssertionError.class)
                        .hasMessage("HTTP requests and workers must have no enclosing test transaction").hasNoCause();
                assertThatThrownBy(this::openIndependentConnection).isInstanceOf(AssertionError.class)
                        .hasMessage("HTTP requests and workers must have no enclosing test transaction").hasNoCause();
                status.setRollbackOnly();
            });
            assertNoCallerTransaction();
            assertUnchanged(before, database.snapshot());
        }

        private void assertUnauthorized(HttpResponse<byte[]> response) {
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().allValues("WWW-Authenticate")).containsExactly("Bearer");
            var expected = mapper.createObjectNode().put("status", 401).put("error", "Unauthorized")
                    .put("message", "Authentication required or invalid")
                    .put("path", expectedContextPath() + "/orders");
            assertThat(expected.equals(jsonBody(response))).as("exact safe security envelope without query").isTrue();
        }

        private void assertHttpPrice(long id, String price) {
            var response = send(jsonRequest("/products/" + id).GET().build());
            assertThat(response.statusCode()).isEqualTo(200);
            var body = jsonBody(response);
            assertThat(body.path("id").asLong()).isEqualTo(id);
            assertThat(body.path("price").isNumber()).isTrue();
            assertThat(body.path("price").decimalValue()).isEqualByComparingTo(price);
        }

        private static void assertProbePrice(Connection connection, long id, String price) throws SQLException {
            try (var statement = connection.prepareStatement("select price from tb_product where id = ?")) {
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBigDecimal(1)).isEqualByComparingTo(price);
                    assertThat(rows.next()).isFalse();
                }
            }
        }

        private static long sessionIdOf(Connection connection) throws SQLException {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("select session_id()")) {
                assertThat(rows.next()).isTrue();
                long id = rows.getLong(1);
                assertThat(id).isPositive();
                assertThat(rows.next()).isFalse();
                return id;
            }
        }

        private static void awaitRelease(CountDownLatch release) {
            try {
                assertThat(release.await(30, TimeUnit.SECONDS)).as("bounded worker release").isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Controlled worker interrupted");
            }
        }

        private static void assertEmpty(CustomerRegistrationDatabaseProbe.Snapshot snapshot) {
            assertThat(snapshot.tables()).hasSize(8);
            snapshot.tables().forEach((table, rows) ->
                    assertThat(rows.rows().isEmpty()).as("seed-free table %s", table).isTrue());
        }
    }
}
