package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig.Failures;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ControlledClock;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.context.support.GenericWebApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;

/** T010 authorship only. These infrastructure oracles are first executed by T011, never here. */
class CustomerRegistrationSupportTest {

    /** Explicit configuration/initializer, with Boot's test-component exclusion for context runners. */
    static WebApplicationContextRunner isolatedRunner() {
        return baseRunner().withInitializer(new CustomerRegistrationTestConfig())
                .withPropertyValues("server.servlet.context-path=", "feature009.customer-registration-test-context=root");
    }

    private static WebApplicationContextRunner baseRunner() {
        return new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=test")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(new TestComponentExclusion())
                .withUserConfiguration(DscommerceApplication.class);
    }

    private static WebApplicationContextRunner uninitializedControlRunner() {
        // Never let an intentionally uninitialized context use the shared profile's database URL.
        return baseRunner().withPropertyValues("spring.datasource.url=jdbc:h2:mem:t010-control-" + UUID.randomUUID(),
                "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
                "spring.jpa.properties.hibernate.hbm2ddl.import_files=", "spring.h2.console.enabled=false");
    }

    static CustomerRegistrationDatabaseProbe probe(ConfigurableApplicationContext context) {
        return new CustomerRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
    }

    static void assertEmpty(Snapshot snapshot) {
        assertThat(snapshot.tables()).hasSize(8);
        snapshot.tables().forEach((table, data) -> assertThat(data.rows().isEmpty()).as("empty table %s", table).isTrue());
    }

    static long sessionId(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("select session_id()")) {
            assertThat(rows.next()).isTrue();
            long result = rows.getLong(1);
            assertThat(result).isPositive();
            assertThat(rows.next()).isFalse();
            return result;
        }
    }

    static void assertRuntime(Connection connection, String expectedUrl) throws SQLException {
        assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("H2");
        assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("2.2.224");
        assertThat(connection.getMetaData().getDriverVersion()).startsWith("2.2.224");
        assertThat(connection.getMetaData().getURL()).isEqualTo(expectedUrl.split(";", 2)[0]);
        // Observe the original defaults before any transaction helper sets its own requirements.
        assertThat(connection.getAutoCommit()).isTrue();
        assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    void initializerRejectsMissingTestProfileWithoutChangingTheEnvironment() {
        try (var context = new GenericWebApplicationContext()) {
            context.getEnvironment().setActiveProfiles("production-control");
            assertThatThrownBy(() -> new CustomerRegistrationTestConfig().initialize(context))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("CustomerRegistrationTestConfig requires the test profile");
            assertThat(context.getEnvironment().getActiveProfiles()).containsExactly("production-control");
            assertThat(context.getEnvironment().getPropertySources()
                    .contains(CustomerRegistrationTestConfig.PROPERTY_SOURCE_NAME)).isFalse();
        }
    }

    @Test
    void initializerRejectsDuplicateInitializationWithoutReplacingItsUrlOrKey() {
        try (var context = new GenericWebApplicationContext()) {
            context.getEnvironment().setActiveProfiles("test");
            var initializer = new CustomerRegistrationTestConfig();
            initializer.initialize(context);
            var url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
            var key = context.getEnvironment().getRequiredProperty("security.jwt.secret-base64");
            assertThatThrownBy(() -> initializer.initialize(context)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("Initialize the customer registration context only once");
            assertThat(context.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(url);
            assertThat(key.equals(context.getEnvironment().getProperty("security.jwt.secret-base64")))
                    .as("the disposable key is not replaced or printed").isTrue();
        }
    }

    @Test
    void explicitInitializerStartsSeedFreeWithLocalClockAndTheExistingSecurityBeans() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).contains("test");
            assertThat(environment.getProperty("spring.datasource.url")).matches(
                    "jdbc:h2:mem:customer-registration-test-[0-9a-f-]{36};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
            assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
            assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
            assertThat(environment.getProperty("spring.h2.console.enabled")).isEqualTo("false");
            assertThat(context).hasSingleBean(ControlledClock.class).hasSingleBean(PasswordEncoder.class)
                    .hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
            assertThat(context.getBean(Clock.class)).isSameAs(context.getBean(ControlledClock.class));
            assertThat(context.getBean(Clock.class).instant()).isEqualTo(CustomerRegistrationTestConfig.INITIAL_INSTANT);
            assertThat(context.getBean(PasswordEncoder.class)).isInstanceOf(BCryptPasswordEncoder.class);
            assertThat(context.getBean(JwtProperties.class).getSecret().length).isEqualTo(32);
            assertThat(context.getBeansOfType(Observation.class)).isEmpty();
            assertThat(context.getBeansOfType(Failures.class)).isEmpty();
            assertEmpty(probe(context).snapshot());
        });
    }

    @Test
    void runtimeConnectionsHaveTheExpectedH2VersionIsolationAndDistinctPhysicalSessions() {
        isolatedRunner().run(context -> {
            assertThat(context).hasNotFailed();
            var url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
            var source = context.getBean(DataSource.class);
            var database = probe(context);
            try (var first = source.getConnection(); var second = source.getConnection();
                 var independent = database.openConnection()) {
                assertRuntime(first, url);
                assertRuntime(second, url);
                assertRuntime(independent, url);
                assertThat(List.of(sessionId(first), sessionId(second), sessionId(independent)))
                        .doesNotHaveDuplicates();
            }
        });
    }

    @Test
    void rootAndApiContextsKeepDifferentDatabasesAndClocksEvenWhenOneContextCloses() {
        isolatedRunner().run(root -> {
            assertThat(root).hasNotFailed();
            var rootProbe = probe(root);
            var rootClock = root.getBean(ControlledClock.class);
            try (var fixture = new CustomerRegistrationTestFixture(root)) {
                fixture.createCategory("T010 root ownership control");
                var rootBefore = rootProbe.snapshot();
                var rootUrl = root.getEnvironment().getRequiredProperty("spring.datasource.url");
                rootClock.advance(Duration.ofDays(1));
                isolatedRunner().withPropertyValues("server.servlet.context-path=/api",
                        "feature009.customer-registration-test-context=api").run(api -> {
                    assertThat(api).hasNotFailed();
                    assertThat(api.getEnvironment().getProperty("server.servlet.context-path")).isEqualTo("/api");
                    assertThat(root.getEnvironment().getProperty("server.servlet.context-path")).isEmpty();
                    assertThat(api.getEnvironment().getProperty("spring.datasource.url")).isNotEqualTo(rootUrl);
                    assertThat(api.getBean(ControlledClock.class)).isNotSameAs(rootClock);
                    assertThat(api.getBean(Clock.class).instant()).isEqualTo(CustomerRegistrationTestConfig.INITIAL_INSTANT);
                    assertThat(rootClock.withZone(ZoneOffset.ofHours(-3)).instant())
                            .isEqualTo(CustomerRegistrationTestConfig.INITIAL_INSTANT.plus(Duration.ofDays(1)));
                    assertEmpty(probe(api).snapshot());
                    assertUnchanged(rootBefore, rootProbe.snapshot());
                });
                // Closing /api must not execute create-drop against the still-open root database.
                assertUnchanged(rootBefore, rootProbe.snapshot());
                assertThat(root.getEnvironment().getProperty("spring.datasource.url")).isEqualTo(rootUrl);
            }
            assertEmpty(rootProbe.snapshot());
        });
    }

    @Test
    void composedHttpAnnotationsDeclareDistinctContextsAndExplicitTestInitialization() {
        assertHttpDeclaration(CustomerRegistrationTestConfig.RootHttpContext.class, "", "root");
        assertHttpDeclaration(CustomerRegistrationTestConfig.ApiHttpContext.class, "/api", "api");
    }

    @Test
    void componentScanDoesNotInstallCustomerSupportInAnUninitializedControlContext() {
        uninitializedControlRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getPropertySources()
                    .contains(CustomerRegistrationTestConfig.PROPERTY_SOURCE_NAME)).isFalse();
            assertThat(context.getBeansOfType(ControlledClock.class)).isEmpty();
            assertThat(context.getBeansOfType(Observation.class)).isEmpty();
            assertThat(context.getBeansOfType(Failures.class)).isEmpty();
            assertThat(context.getBeansOfType(CustomerRegistrationTestFixture.class)).isEmpty();
            assertThat(context.getBeansOfType(CustomerRegistrationDatabaseProbe.class)).isEmpty();
            assertThat(context.getBeansOfType(CustomerRegistrationObservationConfig.class)).isEmpty();
            assertThat(context.getBeansOfType(CustomerRegistrationFailureInjectionConfig.class)).isEmpty();
        });
    }

    @Test
    void explicitObservationImportsRejectAContextWithoutTheCustomerInitializer() {
        uninitializedControlRunner().withUserConfiguration(ExplicitImports.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(CustomerRegistrationObservationConfig.InstrumentationFailure.class);
        });
    }

    @Test
    void explicitObservationImportsSeeTheOriginalJdbcCommitAndStopRecordingAfterScopeEnds() {
        isolatedRunner().withUserConfiguration(ExplicitImports.class).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(Observation.class).hasSingleBean(Failures.class);
            var failures = context.getBean(Failures.class);
            var database = probe(context);
            try (var fixture = new CustomerRegistrationTestFixture(context); var attempt = failures.openControl()) {
                var category = attempt.call(() -> fixture.createCategory("T010 original commit control"));
                attempt.awaitFinished();
                var events = attempt.events();
                assertThat(events).extracting(Event::point).containsSubsequence(Point.SQL_BEFORE, Point.SQL_RETURNED,
                        Point.BEFORE_COMMIT, Point.JDBC_COMMIT_BEFORE_DELEGATE, Point.JDBC_COMMIT_DELEGATED,
                        Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.AFTER_COMPLETION);
                var committed = events.stream().filter(event -> event.point() == Point.AFTER_COMPLETION).toList();
                assertThat(committed).hasSize(1);
                assertThat(committed.getFirst().completion()).isEqualTo(STATUS_COMMITTED);
                assertThat(committed.getFirst().sessionId()).isPositive();
                assertThat(committed.getFirst().connectionId()).isPositive();
                assertThat(committed.getFirst().transactionId()).isEqualTo(1);
                assertThat(events.stream().filter(event -> event.point() == Point.SQL_BEFORE)
                        .allMatch(event -> event.transactionActive() && !event.autoCommit()
                                && event.isolation() == Connection.TRANSACTION_READ_COMMITTED)).isTrue();
                try (var connection = database.openConnection(); var statement = connection.prepareStatement(
                        "select count(*) from tb_category where id = ?")) {
                    assertThat(sessionId(connection)).isNotEqualTo(committed.getFirst().sessionId());
                    statement.setLong(1, category.id());
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getLong(1)).isEqualTo(1);
                    }
                }
                fixture.cleanUp(); // Outside the attempt: cleanup must not extend its observations.
                assertThat(attempt.events()).isEqualTo(events);
                assertEmpty(database.snapshot());
            }
        });
    }

    @Test
    void explicitFailureScopeThrowsItsSentinelWithoutExecutingTheSelectedRead() {
        isolatedRunner().withUserConfiguration(ExplicitImports.class).run(context -> {
            assertThat(context).hasNotFailed();
            var failures = context.getBean(Failures.class);
            var before = probe(context).snapshot();
            try (var attempt = failures.openControl()) {
                var fault = failures.failOnce(attempt, Point.SQL_BEFORE);
                assertThatThrownBy(() -> attempt.call(() -> context.getBean(JdbcTemplate.class)
                        .queryForObject("select count(*) from tb_category", Long.class))).isSameAs(fault.sentinel());
                attempt.awaitFinished();
                fault.assertReached();
                assertThat(fault.hits()).isEqualTo(1);
                assertThat(attempt.events()).extracting(Event::point).containsExactly(Point.SQL_BEFORE);
                assertUnchanged(before, probe(context).snapshot());
            }
        });
    }

    private static void assertHttpDeclaration(Class<?> annotation, String path, String label) {
        var boot = AnnotatedElementUtils.findMergedAnnotation(annotation, SpringBootTest.class);
        var profiles = AnnotatedElementUtils.findMergedAnnotation(annotation, ActiveProfiles.class);
        var configuration = AnnotatedElementUtils.findMergedAnnotation(annotation, ContextConfiguration.class);
        assertThat(boot).isNotNull();
        assertThat(profiles).isNotNull();
        assertThat(configuration).isNotNull();
        assertThat(boot.classes()).containsExactly(DscommerceApplication.class);
        assertThat(boot.webEnvironment()).isEqualTo(SpringBootTest.WebEnvironment.RANDOM_PORT);
        assertThat(boot.properties()).contains("server.servlet.context-path=" + path,
                "feature009.customer-registration-test-context=" + label);
        assertThat(profiles.profiles()).containsExactly("test");
        assertThat(configuration.initializers()).containsExactly(CustomerRegistrationTestConfig.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class})
    static class ExplicitImports { }
}
