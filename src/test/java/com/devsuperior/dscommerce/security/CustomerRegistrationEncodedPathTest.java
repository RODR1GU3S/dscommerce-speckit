package com.devsuperior.dscommerce.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.controllers.CustomerRegistrationHttpTestSupport;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.services.CustomerRegistrationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

/** Real HTTP diagnosis: no firewall, server, mapping or service result is replaced. */
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationEncodedPathTest {
    private static final String HEADER = "X-Feature009-Encoded-Path";

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @RootHttpContext
    class RootHttpTest extends PathCases {
        @Override protected String expectedContextPath() { return ""; }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @ApiHttpContext
    class ApiHttpTest extends PathCases {
        @Override protected String expectedContextPath() { return "/api"; }
    }

    @Import(ProbeConfig.class)
    abstract static class PathCases extends CustomerRegistrationHttpTestSupport {
        @Autowired private Probes probes;
        @SpyBean private CustomerRegistrationService service;
        private final Set<Long> created = new LinkedHashSet<>();
        private boolean finished = true;

        @BeforeEach
        void observeTheOriginalServiceWithoutChangingItsInputOrResult() {
            doAnswer(call -> {
                var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
                var request = attributes.getRequest();
                var probe = probes.pending.get(request.getHeader(HEADER));
                if (probe != null) {
                    probe.serviceCalls++;
                    probe.servletPath = request.getServletPath();
                    probe.pattern = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
                    if (probe.diagnostic != null) { probe.diagnostic.emitInside(); }
                }
                return call.callRealMethod();
            }).when(service).register(any(CustomerRegistrationRequestDTO.class));
        }

        @ParameterizedTest(name = "HTTP path {0}")
        @ValueSource(strings = {"/customers", "/%63ustomers"})
        void rawEncodedTargetReachesTheOriginalRegistrationAndCommits(String relativePath) throws SQLException {
            register(relativePath, new Probe());
        }

        @ParameterizedTest(name = "protected path {0} severity {1}")
        @MethodSource("diagnostics")
        void ormDiagnosticsAreSanitizedBeforeAppendersForBothRawSpellings(String relativePath, Level severity)
                throws SQLException {
            try (var diagnostic = new PrivateDiagnostic(severity)) {
                diagnostic.assertDetectorWorks();
                var probe = new Probe();
                probe.diagnostic = diagnostic;
                register(relativePath, probe);
                assertThat(diagnostic.inside.size()).as("one controlled ORM source event reached the appender").isEqualTo(1);
                System.out.printf("P2_LOG_ASSERT context=%s raw=%s level=%s reached=true%n",
                        expectedContextPath().isEmpty() ? "ROOT" : "api", relativePath, severity);
                assertThat(diagnostic.inside.stream().allMatch(event ->
                        CustomerRegistrationDiagnosticLoggingFilter.class.getName().equals(event.getLoggerName())
                                && "Customer registration persistence failure".equals(event.getMessage())
                                && "Customer registration persistence failure".equals(event.getFormattedMessage())
                                && event.getLevel() == severity && event.getArgumentArray() == null
                                && event.getMarkerList() == null && event.getThrowableProxy() == null
                                && !diagnostic.containsCanary(event)))
                        .as("fixed message and severity without source secrets before appenders").isTrue();
                assertThat(diagnostic.outside.size() == 1 && diagnostic.containsCanary(diagnostic.outside.getFirst()))
                        .as("same server thread restores the unprotected scope after signup").isTrue();
                System.out.printf("P2_LOG_PROTECTED context=%s raw=%s level=%s assertions=complete%n",
                        expectedContextPath().isEmpty() ? "ROOT" : "api", relativePath, severity);
            }
        }

        static Stream<Arguments> diagnostics() {
            return Stream.of("/customers", "/%63ustomers")
                    .flatMap(path -> Stream.of(Level.WARN, Level.ERROR).map(level -> Arguments.of(path, level)));
        }

        private void register(String relativePath, Probe probe) throws SQLException {
            String tag = UUID.randomUUID().toString();
            probes.pending.put(tag, probe);
            String name = "Path." + UUID.randomUUID().toString().replace("-", "");
            var payload = mapper.createObjectNode().put("name", name).put("fullName", "Path Person")
                    .put("email", name + "@example.test").put("phone", "+12345678")
                    .put("birthDate", "2000-02-29").put("password", "Path-password-009");
            var request = jsonRequest(relativePath).header(HEADER, tag)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(payload))).build();
            assertThat(request.uri().getRawPath()).isEqualTo(expectedContextPath() + relativePath);
            finished = false;
            try {
                var response = send(request);
                await(probe.finished);
                finished = true;
                if (response.statusCode() == 201) { created.add(jsonBody(response).path("id").asLong()); }
                System.out.printf("P2_PATH context=%s raw=%s servlet=%s pattern=%s serviceCalls=%d status=%d%n",
                        expectedContextPath().isEmpty() ? "ROOT" : "api", probe.rawUri, probe.servletPath,
                        probe.pattern, probe.serviceCalls, response.statusCode());
                assertThat(probe.rawUri).as("server retains the client's encoded target").isEqualTo(request.uri().getRawPath());
                assertThat(response.statusCode()).as("unmodified firewall/server/MVC permits signup").isEqualTo(201);
                assertThat(probe.serviceCalls).isEqualTo(1);
                assertThat(probe.servletPath).isEqualTo("/customers");
                assertThat(probe.pattern).isEqualTo("/customers");
                long id = jsonBody(response).path("id").asLong();
                assertThat(id).isPositive();
                try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                        "select a.name, r.role from tb_user_account a join tb_user_account_role r "
                                + "on r.user_account_id = a.id where a.id = ?")) {
                    statement.setLong(1, id);
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next() && name.equals(rows.getString(1))
                                && "CLIENT".equals(rows.getString(2)) && !rows.next()).isTrue();
                    }
                }
            } finally { if (finished) { probes.pending.remove(tag); } }
        }

        @AfterEach
        void cleanOnlyCompletedOwnedRegistrations() throws SQLException {
            reset(service);
            if (!finished) { fixture = null; throw new AssertionError("Server completion unproved; cleanup withheld"); }
            try (var connection = openIndependentConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (long id : created) {
                        for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?",
                                "delete from tb_user_account where id = ?")) {
                            try (var statement = connection.prepareStatement(sql)) {
                                statement.setLong(1, id); statement.executeUpdate();
                            }
                        }
                    }
                    connection.commit(); created.clear();
                } catch (SQLException | RuntimeException | Error failure) { connection.rollback(); throw failure; }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig {
        @Bean Probes encodedPathProbes() { return new Probes(); }

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> encodedPathCompletion(Probes probes) {
            var filter = new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                        throws ServletException, IOException {
                    String tag = request.getHeader(HEADER);
                    var probe = tag == null ? null : probes.pending.get(tag);
                    if (probe == null) { chain.doFilter(request, response); return; }
                    probe.rawUri = request.getRequestURI();
                    try { chain.doFilter(request, response); }
                    finally {
                        try { if (probe.diagnostic != null) { probe.diagnostic.emitOutside(); } }
                        finally { probe.finished.countDown(); }
                    }
                }
            };
            var bean = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return bean;
        }
    }

    static final class Probes { private final ConcurrentHashMap<String, Probe> pending = new ConcurrentHashMap<>(); }
    static final class Probe {
        private final CountDownLatch finished = new CountDownLatch(1);
        private String rawUri;
        private String servletPath;
        private String pattern;
        private int serviceCalls;
        private PrivateDiagnostic diagnostic;
    }

    /** Isolated real Logback source channel and production adapter: deliberate canaries never reach console. */
    static final class PrivateDiagnostic implements AutoCloseable {
        private final LoggerContext logging = new LoggerContext();
        private final ListAppender<ILoggingEvent> capture = new ListAppender<>();
        private final String canary = "P2-private-" + UUID.randomUUID();
        private final Level severity;
        private final Logger source;
        private final TurboFilter adapter;
        private List<ILoggingEvent> inside = List.of();
        private List<ILoggingEvent> outside = List.of();

        PrivateDiagnostic(Level severity) {
            this.severity = severity;
            logging.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.TRACE);
            source = logging.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
            capture.setContext(logging); capture.start();
            logging.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(capture);
            adapter = CustomerRegistrationDiagnosticLoggingFilter.createDiagnosticAdapter(
                    logging.getLogger(CustomerRegistrationDiagnosticLoggingFilter.class));
            adapter.setContext(logging); adapter.start(); logging.addTurboFilter(adapter);
        }

        private void emit() {
            var failure = new SQLException(canary, new IllegalArgumentException(canary));
            failure.addSuppressed(new IllegalStateException(canary));
            var marker = MarkerFactory.getDetachedMarker(canary);
            if (severity == Level.WARN) { source.warn(marker, "Controlled ORM diagnostic {}", canary, failure); }
            else { source.error(marker, "Controlled ORM diagnostic {}", canary, failure); }
        }

        void assertDetectorWorks() {
            emit();
            assertThat(capture.list.size() == 1 && containsCanary(capture.list.getFirst()))
                    .as("private positive control detects the sensitive source").isTrue();
            capture.list.clear();
        }

        void emitInside() { emit(); inside = List.copyOf(capture.list); capture.list.clear(); }
        void emitOutside() { emit(); outside = List.copyOf(capture.list); capture.list.clear(); }

        boolean containsCanary(ILoggingEvent event) {
            return String.valueOf(event.getMessage()).contains(canary)
                    || String.valueOf(event.getFormattedMessage()).contains(canary)
                    || Arrays.toString(event.getArgumentArray()).contains(canary)
                    || String.valueOf(event.getMarkerList()).contains(canary)
                    || (event.getThrowableProxy() != null && ThrowableProxyUtil.asString(event.getThrowableProxy()).contains(canary));
        }

        @Override public void close() {
            logging.getTurboFilterList().remove(adapter); adapter.stop(); capture.stop(); logging.stop();
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(40, TimeUnit.SECONDS)).as("server filter completed").isTrue(); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError("HTTP wait interrupted"); }
    }
}
