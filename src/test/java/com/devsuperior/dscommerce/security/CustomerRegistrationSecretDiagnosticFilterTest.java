package com.devsuperior.dscommerce.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.slf4j.MarkerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Private logger context keeps deliberate positive controls away from production/console appenders. */
class CustomerRegistrationSecretDiagnosticFilterTest {
    private static final String SAFE_MESSAGE = "Customer registration persistence failure";
    private final CustomerRegistrationDiagnosticLoggingFilter filter = new CustomerRegistrationDiagnosticLoggingFilter();
    private final String canary = "T068-" + UUID.randomUUID();
    private LoggerContext logging;
    private Logger source;
    private Logger safe;
    private ListAppender<ILoggingEvent> capture;
    private TurboFilter adapter;

    @BeforeEach
    void prepareIsolatedRealLoggersAndTheProductionAdapter() {
        logging = new LoggerContext();
        logging.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.TRACE);
        source = logging.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
        safe = logging.getLogger(CustomerRegistrationDiagnosticLoggingFilter.class);
        capture = new ListAppender<>();
        capture.setContext(logging);
        capture.start();
        logging.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(capture);
        adapter = CustomerRegistrationDiagnosticLoggingFilter.createDiagnosticAdapter(safe);
        adapter.setContext(logging);
        adapter.start();
        logging.addTurboFilter(adapter);
    }

    @AfterEach
    void closeOnlyThePrivateLoggingContext() {
        logging.getTurboFilterList().remove(adapter);
        adapter.stop();
        capture.stop();
        logging.stop();
    }

    static Stream<Arguments> routes() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(
                Arguments.of(context, "POST", "/customers", true),
                Arguments.of(context, "POST", "/%63ustomers", true),
                Arguments.of(context, "GET", "/customers", false),
                Arguments.of(context, "GET", "/%63ustomers", false),
                Arguments.of(context, "POST", "/customers/extra", false),
                Arguments.of(context, "POST", "/%63ustomers/extra", false),
                Arguments.of(context, "POST", "/%2563ustomers", false),
                Arguments.of(context, "POST", "/login", false),
                Arguments.of(context, "POST", "/admin/products", false)));
    }

    @ParameterizedTest(name = "route {index}: context={0} method={1} path={2} protected={3}")
    @MethodSource("routes")
    void onlySignupReplacesMessagesArgumentsMarkersAndThrowableChains(String context, String method,
                                                                    String path, boolean protectedRoute) throws Exception {
        filter.doFilter(request(context, method, path), new MockHttpServletResponse(), (req, response) -> emitSurfaces());
        assertThat(capture.list.size() == 5).as("each source event remains observable").isTrue();
        if (protectedRoute) {
            assertSafeEvents(Level.ERROR, 5);
        } else {
            assertThat(capture.list.stream().allMatch(this::containsCanary))
                    .as("unrelated routes remain observable with their original private controls").isTrue();
        }
        capture.list.clear();
        source.error(canary);
        assertThat(capture.list.size() == 1 && containsCanary(capture.list.getFirst()))
                .as("request scope removed after normal completion").isTrue();
    }

    static Stream<Level> severities() {
        return Stream.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
    }

    @ParameterizedTest(name = "severity {0}")
    @MethodSource("severities")
    void replacementPreservesSeverityAndCannotCarryArgumentsOrExceptions(Level level) throws Exception {
        filter.doFilter(request("", "POST", "/customers"), new MockHttpServletResponse(), (req, response) -> {
            switch (level.toInt()) {
                case Level.TRACE_INT -> source.trace("{}", canary, sensitiveFailure());
                case Level.DEBUG_INT -> source.debug("{}", canary, sensitiveFailure());
                case Level.INFO_INT -> source.info("{}", canary, sensitiveFailure());
                case Level.WARN_INT -> source.warn("{}", canary, sensitiveFailure());
                case Level.ERROR_INT -> source.error("{}", canary, sensitiveFailure());
                default -> throw new AssertionError("Unknown severity fixture");
            }
        });
        assertSafeEvents(level, 1);
        assertThat(source.getLevel() == null && safe.getLevel() == null
                && logging.getLogger(Logger.ROOT_LOGGER_NAME).getLevel() == Level.TRACE)
                .as("adapter does not change configured levels").isTrue();
    }

    @ParameterizedTest(name = "cleanup context={0}")
    @ValueSource(strings = {"", "/api"})
    void exceptionalCompletionRestoresScopeAndPropagatesTheOriginalFailure(String context) throws Exception {
        var sentinel = new ServletException("controlled chain failure");
        ServletException observed = null;
        try {
            filter.doFilter(request(context, "POST", "/customers"), new MockHttpServletResponse(), (req, response) -> {
                source.error(canary);
                throw sentinel;
            });
        } catch (ServletException failure) { observed = failure; }
        assertThat(observed == sentinel).as("filter never changes request failure").isTrue();
        assertSafeEvents(Level.ERROR, 1);
        capture.list.clear();
        source.error(canary);
        assertThat(capture.list.size() == 1 && containsCanary(capture.list.getFirst()))
                .as("request scope removed after failure").isTrue();
    }

    @Test
    void unrelatedLoggersRemainObservableInsideSignup() throws Exception {
        var unrelated = logging.getLogger("T068.private.other.channel");
        filter.doFilter(request("", "POST", "/customers"), new MockHttpServletResponse(),
                (req, response) -> unrelated.error(canary));
        assertThat(capture.list.size() == 1 && containsCanary(capture.list.getFirst())
                && unrelated.getName().equals(capture.list.getFirst().getLoggerName()))
                .as("adapter only handles the proven JDBC source channel").isTrue();
    }

    @Test
    void disabledSourceEventDoesNotCreateAnEnabledReplacement() throws Exception {
        source.setLevel(Level.ERROR);
        filter.doFilter(request("", "POST", "/customers"), new MockHttpServletResponse(),
                (req, response) -> source.warn(canary));
        assertThat(capture.list.isEmpty()).as("source logger threshold is respected").isTrue();
    }

    private void emitSurfaces() {
        source.error(canary);
        source.error("private argument {}", canary);
        source.error("private exception", sensitiveFailure());
        source.error((String) null, sensitiveFailure());
        source.error(MarkerFactory.getDetachedMarker(canary), "private marker");
    }

    private IllegalStateException sensitiveFailure() {
        var failure = new IllegalStateException(canary, new IllegalArgumentException(canary));
        failure.addSuppressed(new IllegalArgumentException(canary));
        return failure;
    }

    private boolean containsCanary(ILoggingEvent event) {
        return String.valueOf(event.getMessage()).contains(canary)
                || String.valueOf(event.getFormattedMessage()).contains(canary)
                || java.util.Arrays.toString(event.getArgumentArray()).contains(canary)
                || String.valueOf(event.getMarkerList()).contains(canary)
                || (event.getThrowableProxy() != null && ThrowableProxyUtil.asString(event.getThrowableProxy()).contains(canary));
    }

    private void assertSafeEvents(Level expectedLevel, int expectedCount) {
        assertThat(capture.list.size() == expectedCount).as("replacement event count").isTrue();
        assertThat(capture.list.stream().allMatch(event -> SAFE_MESSAGE.equals(event.getMessage())
                && SAFE_MESSAGE.equals(event.getFormattedMessage()) && event.getLevel() == expectedLevel
                && safe.getName().equals(event.getLoggerName()) && event.getArgumentArray() == null
                && event.getThrowableProxy() == null && event.getMarkerList() == null && !containsCanary(event)))
                .as("fixed message with original severity, no source arguments, marker or throwable").isTrue();
    }

    private static MockHttpServletRequest request(String context, String method, String path) {
        var request = new MockHttpServletRequest(method, context + path);
        request.setContextPath(context);
        return request;
    }
}
