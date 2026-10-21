package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.OwnOrderQueryAccessService;
import com.devsuperior.dscommerce.services.OwnOrderQueryService;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe.Snapshot;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit import in the two failure contexts only; no production bean or global policy is replaced. */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class OwnOrderQueryFailureInjectionTestConfig {

    public static final String HEADER = "X-Feature007-Failure-Case";

    @Bean
    public static FailureProbe ownOrderQueryFailureProbe() { return new FailureProbe(); }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> ownOrderFailureRequestScope(FailureProbe probe) {
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                       FilterChain chain) throws ServletException, IOException {
                var attempt = probe.armed.get();
                var path = request.getRequestURI().substring(request.getContextPath().length());
                boolean selected = attempt != null && attempt.id.equals(request.getHeader(HEADER))
                        && (path.equals("/orders") || path.startsWith("/orders/"));
                if (!selected) { chain.doFilter(request, response); return; }
                probe.current.set(attempt);
                attempt.requests++;
                try { chain.doFilter(request, response); }
                finally { probe.current.remove(); attempt.finished.countDown(); }
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    public enum Point {
        NONE, INITIAL_LOOKUP, FILTER_SCALAR, LATE_SCALAR, ID_PAGE, COUNT, LIST_FETCH, DETAIL_FETCH,
        CALCULATION, MAPPING, COMPLETION, LATE_REMOVED, LATE_NO_ROLE
    }

    /** One explicitly marked HTTP request; setup, login, independent snapshots and cleanup never arm it. */
    public static final class Attempt {
        public final Point point;
        public final String id = UUID.randomUUID().toString();
        public final CountDownLatch finished = new CountDownLatch(1);
        public volatile Runnable lateMutation;
        public volatile Snapshot afterMutation;
        public volatile int requests, hits, initialLookups, filterScalars, lateScalars, guards;
        public volatile int serviceEntries, bodyEntries, bodyReturns, proxyReturns, beforeCommits, completion = -1;
        public volatile int idSqlExecuted, countSqlExecuted, listFetchSqlExecuted, detailFetchSqlExecuted;
        public volatile boolean insideService, transactionAtHit, bodyReadOnly, outsideAtEntry, outsideAtExit;
        private String repositoryPhase;
        private Attempt(Point point) { this.point = point; }
    }

    public static final class FailureProbe implements BeanPostProcessor {
        private final AtomicReference<Attempt> armed = new AtomicReference<>();
        private final ThreadLocal<Attempt> current = new ThreadLocal<>();

        public Attempt arm(Point point) {
            var attempt = new Attempt(point);
            if (!armed.compareAndSet(null, attempt)) { throw new IllegalStateException("T024 probe already armed"); }
            return attempt;
        }

        public void disarm() { armed.set(null); }

        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) {
                return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                        (proxy, method, args) -> {
                            var value = delegate(source, method, args);
                            return value instanceof Connection connection ? connection(connection) : value;
                        });
            }
            if (bean instanceof OwnOrderQueryService) {
                var advised = advised(bean);
                advised.addAdvice((MethodInterceptor) invocation -> {
                    var a = current.get();
                    if (a == null || !query(invocation.getMethod().getName())) { return invocation.proceed(); }
                    a.bodyEntries++;
                    a.bodyReadOnly = TransactionSynchronizationManager.isActualTransactionActive()
                            && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            a.beforeCommits++;
                            if (a.point == Point.COMPLETION) { throw fail(a); }
                        }
                        @Override public void afterCompletion(int status) { a.completion = status; }
                    });
                    var result = invocation.proceed();
                    a.bodyReturns++;
                    return result;
                });
                advised.addAdvice(0, (MethodInterceptor) invocation -> {
                    var a = current.get();
                    if (a == null || !query(invocation.getMethod().getName())) { return invocation.proceed(); }
                    a.serviceEntries++;
                    a.outsideAtEntry = !TransactionSynchronizationManager.isActualTransactionActive();
                    a.insideService = true;
                    try { var value = invocation.proceed(); a.proxyReturns++; return value; }
                    finally { a.insideService = false; a.outsideAtExit = !TransactionSynchronizationManager.isActualTransactionActive(); }
                });
            } else if (bean instanceof OwnOrderQueryAccessService) {
                advised(bean).addAdvice(0, (MethodInterceptor) invocation -> {
                    var a = current.get();
                    if (a != null && invocation.getMethod().getName().equals("requireAccountId")) {
                        a.guards++;
                        if (a.point == Point.LATE_REMOVED || a.point == Point.LATE_NO_ROLE) {
                            hit(a);
                            if (a.lateMutation == null) { throw new IllegalStateException("T024 missing committed preparation"); }
                            a.lateMutation.run();
                        }
                    }
                    return invocation.proceed();
                });
            } else if (bean instanceof UserAccountRepository) {
                advised(bean).addAdvice(0, (MethodInterceptor) invocation -> {
                    var a = current.get();
                    if (a == null) { return invocation.proceed(); }
                    var method = invocation.getMethod().getName();
                    if (method.equals("findByIdWithRoles")) {
                        a.initialLookups++;
                        if (a.point == Point.INITIAL_LOOKUP) { throw fail(a); }
                    }
                    if (method.equals("findEffectiveAccountRolesById")) {
                        if (a.insideService) { a.lateScalars++; } else { a.filterScalars++; }
                        if (a.point == (a.insideService ? Point.LATE_SCALAR : Point.FILTER_SCALAR)) { throw fail(a); }
                    }
                    return invocation.proceed();
                });
            } else if (bean instanceof OrderRepository) {
                advised(bean).addAdvice(0, (MethodInterceptor) invocation -> {
                    var a = current.get();
                    if (a == null) { return invocation.proceed(); }
                    a.repositoryPhase = invocation.getMethod().getName();
                    try {
                        var value = invocation.proceed();
                        if (a.point == Point.CALCULATION || a.point == Point.MAPPING) {
                            if (value instanceof List<?> orders && a.repositoryPhase.equals("findOwnOrdersWithItemsByIds")) {
                                return orders.stream().map(order -> decorateOrder((Order) order, a)).toList();
                            }
                            if (value instanceof Optional<?> order && a.repositoryPhase.equals("findOwnOrderWithItemsById")) {
                                return order.map(root -> decorateOrder((Order) root, a));
                            }
                        }
                        return value;
                    } finally { a.repositoryPhase = null; }
                });
            }
            return bean;
        }

        private Connection connection(Connection source) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        var value = delegate(source, method, args);
                        if (value instanceof PreparedStatement statement && method.getName().equals("prepareStatement")) {
                            return statement(statement, (String) args[0]);
                        }
                        return value;
                    });
        }

        private PreparedStatement statement(PreparedStatement source, String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        var value = delegate(source, method, args);
                        var a = current.get();
                        if (a != null && method.getName().equals("executeQuery") && a.repositoryPhase != null) {
                            var normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
                            Point reached = null;
                            if (a.repositoryPhase.equals("findOwnOrderIds") && normalized.contains("from tb_order ")) {
                                if (normalized.matches(".*select count\\s*\\(.*")) { a.countSqlExecuted++; reached = Point.COUNT; }
                                else { a.idSqlExecuted++; reached = Point.ID_PAGE; }
                            } else if (a.repositoryPhase.equals("findOwnOrdersWithItemsByIds") && normalized.contains("tb_order_item")) {
                                a.listFetchSqlExecuted++; reached = Point.LIST_FETCH;
                            } else if (a.repositoryPhase.equals("findOwnOrderWithItemsById") && normalized.contains("tb_order_item")) {
                                a.detailFetchSqlExecuted++; reached = Point.DETAIL_FETCH;
                            }
                            if (reached == a.point) {
                                // The driver really executed this SELECT, including count, before the controlled read failure.
                                if (value instanceof ResultSet rows) { rows.close(); }
                                hit(a);
                                throw new SQLException("T024 controlled private SQL diagnostic", "08006");
                            }
                        }
                        return value;
                    });
        }

        private static Order decorateOrder(Order root, Attempt a) {
            return decorate(root, invocation -> {
                if (a.point == Point.CALCULATION && invocation.getMethod().getName().equals("getTotal")) { throw fail(a); }
                if (a.point == Point.MAPPING && invocation.getMethod().getName().equals("getItems")) {
                    return root.getItems().stream().map(item -> decorate(item, line -> {
                        if (line.getMethod().getName().equals("getProduct")) {
                            return decorate(item.getProduct(), product -> {
                                if (product.getMethod().getName().equals("getName")) { throw fail(a); }
                                return product.proceed();
                            });
                        }
                        return line.proceed();
                    })).toList();
                }
                return invocation.proceed();
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T decorate(T target, MethodInterceptor interceptor) {
            var factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvice(interceptor);
            return (T) factory.getProxy();
        }

        private static Advised advised(Object bean) {
            if (!(bean instanceof Advised advised)) { throw new IllegalStateException("T024 requires real Spring proxies"); }
            return advised;
        }
        private static boolean query(String method) { return method.equals("list") || method.equals("detail"); }
        private static void hit(Attempt a) {
            a.hits++;
            a.transactionAtHit = TransactionSynchronizationManager.isActualTransactionActive();
        }
        private static RuntimeException fail(Attempt a) {
            hit(a);
            var diagnostic = "T024 controlled private SQL stack JWT hash diagnostic";
            return switch (a.point) {
                case CALCULATION -> new ArithmeticException(diagnostic);
                case MAPPING -> new IllegalStateException(diagnostic);
                case COMPLETION -> new IllegalStateException(diagnostic);
                default -> new DataAccessResourceFailureException(diagnostic);
            };
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
