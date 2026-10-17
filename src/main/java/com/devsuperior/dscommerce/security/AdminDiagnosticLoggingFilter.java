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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Adapts the confirmed JDBC diagnostic leak before an appender can publish its SQL/throwable. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminDiagnosticLoggingFilter extends OncePerRequestFilter {
    private static final ThreadLocal<Boolean> ADMIN_REQUEST = new ThreadLocal<>();
    private static final Logger SAFE_LOG = LoggerFactory.getLogger(AdminDiagnosticLoggingFilter.class);
    private LoggerContext logging;
    private TurboFilter diagnosticAdapter;

    @PostConstruct
    public void registerDiagnosticAdapter() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext backend)) return;
        logging = backend;
        diagnosticAdapter = new TurboFilter() {
            @Override
            public FilterReply decide(Marker marker, ch.qos.logback.classic.Logger source, Level level,
                                      String format, Object[] arguments, Throwable failure) {
                if (!Boolean.TRUE.equals(ADMIN_REQUEST.get()) || format == null
                        || !source.getName().equals("org.hibernate.engine.jdbc.spi.SqlExceptionHelper")
                        || !level.isGreaterOrEqual(source.getEffectiveLevel())) return FilterReply.NEUTRAL;
                // Preserve severity. Never pass the source format, arguments or throwable to the safe logger.
                switch (level.toInt()) {
                    case Level.ERROR_INT -> SAFE_LOG.error("Administrative persistence failure");
                    case Level.WARN_INT -> SAFE_LOG.warn("Administrative persistence failure");
                    case Level.INFO_INT -> SAFE_LOG.info("Administrative persistence failure");
                    case Level.DEBUG_INT -> SAFE_LOG.debug("Administrative persistence failure");
                    case Level.TRACE_INT -> SAFE_LOG.trace("Administrative persistence failure");
                    default -> { return FilterReply.NEUTRAL; }
                }
                return FilterReply.DENY;
            }
        };
        diagnosticAdapter.setContext(logging); diagnosticAdapter.start(); logging.addTurboFilter(diagnosticAdapter);
    }

    @PreDestroy
    public void removeDiagnosticAdapter() {
        if (logging != null && diagnosticAdapter != null) {
            logging.getTurboFilterList().remove(diagnosticAdapter); diagnosticAdapter.stop();
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/admin") && !path.startsWith("/admin/")) { chain.doFilter(request, response); return; }
        Boolean previous = ADMIN_REQUEST.get();
        ADMIN_REQUEST.set(true);
        try { chain.doFilter(request, response); }
        finally { if (previous == null) ADMIN_REQUEST.remove(); else ADMIN_REQUEST.set(previous); }
    }
}
