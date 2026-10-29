package com.devsuperior.dscommerce.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;

/** Replaces JDBC diagnostics before appenders, only during synchronous customer registration. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CustomerRegistrationDiagnosticLoggingFilter extends OncePerRequestFilter {

    private static final ThreadLocal<Boolean> REGISTRATION_REQUEST = new ThreadLocal<>();
    private static final PathPattern REGISTRATION_PATH = PathPatternParser.defaultInstance.parse("/customers");
    private LoggerContext logging;
    private TurboFilter diagnosticAdapter;

    @PostConstruct
    public void registerDiagnosticAdapter() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext backend)) { return; }
        logging = backend;
        diagnosticAdapter = createDiagnosticAdapter(LoggerFactory.getLogger(getClass()));
        diagnosticAdapter.setContext(logging);
        diagnosticAdapter.start();
        logging.addTurboFilter(diagnosticAdapter);
    }

    static TurboFilter createDiagnosticAdapter(Logger safeLog) {
        return new TurboFilter() {
            @Override
            public FilterReply decide(Marker marker, ch.qos.logback.classic.Logger source, Level level,
                                      String format, Object[] arguments, Throwable failure) {
                if (!Boolean.TRUE.equals(REGISTRATION_REQUEST.get())
                        || !"org.hibernate.engine.jdbc.spi.SqlExceptionHelper".equals(source.getName())
                        || !level.isGreaterOrEqual(source.getEffectiveLevel())) { return FilterReply.NEUTRAL; }
                // Do not forward message, arguments, marker or throwable, even with a null message.
                // The replacement remains an observable event at the original severity.
                switch (level.toInt()) {
                    case Level.ERROR_INT -> safeLog.error("Customer registration persistence failure");
                    case Level.WARN_INT -> safeLog.warn("Customer registration persistence failure");
                    case Level.INFO_INT -> safeLog.info("Customer registration persistence failure");
                    case Level.DEBUG_INT -> safeLog.debug("Customer registration persistence failure");
                    case Level.TRACE_INT -> safeLog.trace("Customer registration persistence failure");
                    default -> { return FilterReply.NEUTRAL; }
                }
                return FilterReply.DENY;
            }
        };
    }

    @PreDestroy
    public void removeDiagnosticAdapter() {
        if (logging != null && diagnosticAdapter != null) {
            logging.getTurboFilterList().remove(diagnosticAdapter);
            diagnosticAdapter.stop();
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equals(request.getMethod()) || !isRegistrationPath(request)) {
            chain.doFilter(request, response);
            return;
        }
        Boolean previous = REGISTRATION_REQUEST.get();
        REGISTRATION_REQUEST.set(true);
        try { chain.doFilter(request, response); }
        finally {
            if (previous == null) { REGISTRATION_REQUEST.remove(); }
            else { REGISTRATION_REQUEST.set(previous); }
        }
    }

    private static boolean isRegistrationPath(HttpServletRequest request) {
        try {
            // Match decoded segments with the same HTTP path parser used by MVC. Each parse starts
            // from the raw URI; never decode a String twice or change MVC's request-path cache.
            return REGISTRATION_PATH.matches(RequestPath.parse(request.getRequestURI(), request.getContextPath())
                    .pathWithinApplication());
        } catch (IllegalArgumentException malformedPath) {
            // Leave malformed-path rejection to the existing server/firewall/MVC boundaries.
            return false;
        }
    }
}
