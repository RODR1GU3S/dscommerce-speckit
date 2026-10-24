package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.services.PaymentRegistrationService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * T023 authorship only. Explicit import in isolated payment test contexts; no test is executed here.
 * Delegates to the original service proxy, shared EntityManager and JDBC driver. No replacement business result.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class PaymentFailureInjectionTestConfig {
    public static final String HEADER = "X-Feature008-Failure";
    private static final int WAIT_SECONDS = 10;

    @Bean
    public static FailureProbe paymentFailureProbe(Environment environment) { return new FailureProbe(environment); }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> paymentFailureRequestScope(FailureProbe probe) {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                var id = request.getHeader(HEADER);
                if (id == null) { chain.doFilter(request, response); return; }
                var run = probe.runs.get(id);
                if (run == null) { throw new InstrumentationFailure("Unknown/closed payment failure scope", null); }
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if (!"POST".equals(request.getMethod())
                        || !path.equals("/admin/orders/" + run.orderId + "/payments")) {
                    throw fault(run, "Failure scope used for another operation/order", null);
                }
                if (probe.current.get() != null || run.closed.get() || !run.started.compareAndSet(false, true)) {
                    throw fault(run, "Failure scope must belong to exactly one request", null);
                }
                probe.current.set(run);
                try { chain.doFilter(request, response); }
                finally {
                    probe.current.remove();
                    run.finished.countDown();
                    if (run.closed.get()) { probe.runs.remove(run.id, run); }
                }
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    public enum Point {
        NONE, SQL_BEFORE_FIRST_DML, AFTER_FIRST_DML, AFTER_FLUSH, AFTER_BODY_DTO,
        BEFORE_COMMIT, JDBC_COMMIT_BEFORE_DELEGATE
    }

    /** Same-transaction observation at the chosen point. Never treat this connection as a committed-state read. */
    @FunctionalInterface
    public interface PointObservation { void verify(Point point, Connection transactionConnection, State state) throws Exception; }

    public record Dml(String sql, long affectedRows) {
        @Override public String toString() { return "PaymentDml[affectedRows=" + affectedRows + ", sql=redacted]"; }
    }
    public record FailedDml(String sql, String sqlState) {
        @Override public String toString() { return "PaymentFailedDml[sqlState=" + sqlState + ", sql=redacted]"; }
    }

    /** Immutable copy; read on the request thread at a point, or after awaitFinished on the test thread. */
    public record State(Point point, List<String> events, List<Dml> dml, List<FailedDml> failedDml,
                        int proxyEntries, int bodyEntries, int bodyReturns, int bodyScopeFailures,
                        int proxyReturns, int proxyFailures, int flushReturns, int beforeCommits,
                        int afterCommits, int commitAttempts, int delegatedCommits, int successfulCommits,
                        int rollbackAttempts, int delegatedRollbacks, int successfulRollbacks, int completion,
                        int hits, boolean injected, boolean outsideAtEntry, boolean outsideAtExit,
                        boolean newTransaction, boolean boundConnection, boolean autoCommitOff,
                        int isolation, int sessionId, String databaseUrl, long threadId,
                        PaymentConfirmationDTO bodyDto, Throwable proxyFailure, Throwable instrumentationFailure) { }

    public static final class InjectedFailure extends RuntimeException {
        private InjectedFailure(Point point) { super("T023 test-only injection at " + point); }
    }
    public static final class InstrumentationFailure extends AssertionError {
        private InstrumentationFailure(String message, Throwable cause) { super("T023 instrumentation: " + message, cause); }
    }

    public static final class Run implements AutoCloseable {
        private final FailureProbe probe;
        private final String id = UUID.randomUUID().toString();
        private final long orderId;
        private final Point point;
        private final PointObservation observation;
        private final InjectedFailure sentinel;
        private final AtomicBoolean started = new AtomicBoolean(), closed = new AtomicBoolean();
        private final CountDownLatch finished = new CountDownLatch(1);
        private final List<String> events = new ArrayList<>();
        private final List<Dml> dml = new ArrayList<>();
        private final List<FailedDml> failedDml = new ArrayList<>();
        private boolean inProxy, inBody, observing, outsideAtEntry, outsideAtExit, newTransaction, boundConnection, autoCommitOff, injected;
        private int proxyEntries, bodyEntries, bodyReturns, bodyScopeFailures, proxyReturns, proxyFailures, flushReturns;
        private int beforeCommits, afterCommits, commitAttempts, delegatedCommits, successfulCommits;
        private int rollbackAttempts, delegatedRollbacks, successfulRollbacks, completion = -1, hits;
        private int isolation, sessionId;
        private long threadId;
        private String databaseUrl;
        private PaymentConfirmationDTO bodyDto;
        private Throwable proxyFailure;
        private volatile InstrumentationFailure instrumentationFailure;
        private ConnectionObserver connection;

        private Run(FailureProbe probe, long orderId, Point point, PointObservation observation) {
            this.probe = probe; this.orderId = orderId; this.point = point; this.observation = observation;
            sentinel = new InjectedFailure(point);
        }
        public String headerValue() { return id; }
        public InjectedFailure sentinel() { return sentinel; }

        public void awaitFinished() {
            try {
                if (!started.get() || !finished.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw fault(this, "Marked request did not finish within the bounded wait", null);
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw fault(this, "Interrupted while waiting for request completion", failure);
            }
        }

        public State state() {
            if (finished.getCount() != 0 && probe.current.get() != this) {
                throw new IllegalStateException("Await request completion before reading failure observations");
            }
            return new State(point, List.copyOf(events), List.copyOf(dml), List.copyOf(failedDml),
                    proxyEntries, bodyEntries, bodyReturns, bodyScopeFailures, proxyReturns, proxyFailures,
                    flushReturns, beforeCommits, afterCommits, commitAttempts, delegatedCommits, successfulCommits,
                    rollbackAttempts, delegatedRollbacks, successfulRollbacks, completion, hits, injected,
                    outsideAtEntry, outsideAtExit, newTransaction, boundConnection, autoCommitOff, isolation,
                    sessionId, databaseUrl, threadId, bodyDto, proxyFailure, instrumentationFailure);
        }

        /** Does not assert an HTTP result or a database baseline; consumers must check those independently. */
        public void verifyInstrumentation() {
            awaitFinished();
            if (instrumentationFailure != null) { throw instrumentationFailure; }
            if (proxyEntries != 1 || bodyEntries != 1 || proxyReturns + proxyFailures != 1
                    || !outsideAtEntry || !outsideAtExit || !newTransaction || !boundConnection
                    || !autoCommitOff || completion < 0 || inProxy || inBody) {
                throw fault(this, "Missing original transaction/proxy/completion observation", proxyFailure);
            }
            if (point != Point.NONE && (hits != 1 || !injected)) {
                throw new AssertionError("T023 requested point was not injected; inspect the application outcome", proxyFailure);
            }
            if (point == Point.NONE && (hits != 0 || injected)) {
                throw fault(this, "Unarmed positive control was injected", null);
            }
            if (proxyReturns == 1 && (bodyDto == null || bodyReturns != 1 || beforeCommits != 1 || afterCommits != 1
                    || commitAttempts != 1 || delegatedCommits != 1 || successfulCommits != 1
                    || rollbackAttempts != 0 || completion != TransactionSynchronization.STATUS_COMMITTED)) {
                throw fault(this, "Successful proxy return lacks complete original JDBC commit observation", null);
            }
            if (injected && successfulCommits != 0) {
                throw fault(this, "A pre-commit injection occurred after a successful commit", null);
            }
        }

        /** Call after the HTTP/filter/proxy has ended, before independently reading the committed database. */
        public void requireOutsideTransaction() {
            verifyInstrumentation();
            if (!noTransaction(probe.source)) { throw fault(this, "Independent observation has an external transaction", null); }
        }

        /** For a later response filter on this request thread: proxy completion may precede HTTP delivery. */
        public State stateAfterProxy() {
            var state = state();
            if (instrumentationFailure != null) { throw instrumentationFailure; }
            if (proxyEntries != 1 || proxyReturns + proxyFailures != 1 || inProxy || inBody
                    || !outsideAtExit || completion < 0 || !noTransaction(probe.source)) {
                throw fault(this, "Independent read requested before original transaction/proxy exit", null);
            }
            // Completion/commit counters still determine committed, rolled-back or unknown outcome; exit alone does not.
            return state;
        }

        @Override public void close() {
            if (!closed.compareAndSet(false, true)) { return; }
            try { verifyInstrumentation(); }
            finally { if (finished.getCount() == 0 || !started.get()) { probe.runs.remove(id, this); } }
        }
    }

    public static final class FailureProbe implements BeanPostProcessor, Ordered, DisposableBean {
        private final Environment environment;
        private final ConcurrentHashMap<String, Run> runs = new ConcurrentHashMap<>();
        private final ThreadLocal<Run> current = new ThreadLocal<>();
        private ObservedDataSource source;
        private Advised service;
        private Object serviceTarget;
        private EntityManager originalEntityManager;
        private MethodInterceptor outside, inside;
        private FailureProbe(Environment environment) { this.environment = environment; }
        @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

        /** One fresh run/header per POST. Setup/login/cleanup and unmarked traffic remain unarmed. */
        public Run open(long orderId, Point point, PointObservation observation) {
            if (orderId <= 0 || source == null || service == null || !noTransaction(source)) {
                throw new InstrumentationFailure("Open requires the real installed service outside a caller transaction", null);
            }
            String url = environment.getRequiredProperty("spring.datasource.url");
            if (!url.startsWith("jdbc:h2:mem:admin-product-test-")
                    || !"create-drop".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw new InstrumentationFailure("Explicit isolated payment initializer/create-drop required", null);
            }
            var run = new Run(this, orderId, Objects.requireNonNull(point), Objects.requireNonNull(observation));
            runs.put(run.id, run);
            return run;
        }

        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (name.equals("dataSource") && bean instanceof DataSource delegate) {
                if (source != null) { throw new InstrumentationFailure("Ambiguous DataSource decorator", null); }
                source = new ObservedDataSource(delegate, this);
                return source;
            }
            if (!name.equals("paymentRegistrationService")) { return bean; }
            if (service != null) { throw new InstrumentationFailure("Payment service instrumented more than once", null); }
            if (AopUtils.getTargetClass(bean) != PaymentRegistrationService.class || !(bean instanceof Advised advised)) {
                throw new InstrumentationFailure("Expected the original payment service and Spring proxy", null);
            }
            int txIndex = -1;
            for (int i = 0; i < advised.getAdvisors().length; i++) {
                if (advised.getAdvisors()[i].getAdvice() instanceof TransactionInterceptor) {
                    if (txIndex >= 0) { throw new InstrumentationFailure("Multiple transaction interceptors", null); }
                    txIndex = i;
                }
            }
            if (txIndex < 0 || advised.isFrozen() || !advised.getTargetSource().isStatic()) {
                throw new InstrumentationFailure("Requires one mutable original transaction proxy with a static target", null);
            }
            try { serviceTarget = advised.getTargetSource().getTarget(); }
            catch (Exception failure) { throw new InstrumentationFailure("Cannot inspect singleton service target", failure); }
            var field = ReflectionTestUtils.getField(serviceTarget, "entityManager");
            if (!(field instanceof EntityManager manager)) {
                throw new InstrumentationFailure("Cannot observe the original shared EntityManager", null);
            }
            originalEntityManager = manager;
            var decorated = (EntityManager) Proxy.newProxyInstance(EntityManager.class.getClassLoader(),
                    new Class<?>[]{EntityManager.class}, (proxy, method, args) -> {
                        var run = writing();
                        boolean flushing = run != null && run.inBody && method.getName().equals("flush") && method.getParameterCount() == 0;
                        if (flushing) { requireTransaction(run); run.events.add("FLUSH_ATTEMPT"); }
                        var result = delegate(manager, method, args); // Explicit flush must actually return first.
                        if (flushing) {
                            requireTransaction(run);
                            run.flushReturns++; run.events.add("FLUSH_RETURNED");
                            hit(run, Point.AFTER_FLUSH);
                        }
                        return result;
                    });
            inside = invocation -> {
                var run = writing();
                if (run == null || !invocation.getMethod().getName().equals("register")) { return invocation.proceed(); }
                if (++run.bodyEntries != 1) { throw fault(run, "Multiple body entries", null); }
                run.inBody = true;
                try {
                    bindOwnedTransaction(run);
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            requireTransaction(run);
                            if (readOnly) { throw fault(run, "Read-only payment completion", null); }
                            run.beforeCommits++; run.events.add("BEFORE_COMMIT");
                            hit(run, Point.BEFORE_COMMIT);
                        }
                        @Override public void afterCommit() { run.afterCommits++; run.events.add("AFTER_COMMIT"); }
                        @Override public void afterCompletion(int status) {
                            run.completion = status; run.events.add("AFTER_COMPLETION:" + status);
                        }
                    });
                    var result = invocation.proceed();
                    requireTransaction(run);
                    if (!(result instanceof PaymentConfirmationDTO dto)) { throw fault(run, "Body did not return its real DTO", null); }
                    run.bodyDto = dto; run.bodyReturns++; run.events.add("BODY_DTO_RETURNED");
                    // Exact boundary: DTO construction has finished. This is not an injection inside its constructor.
                    hit(run, Point.AFTER_BODY_DTO);
                    return result;
                } catch (Throwable failure) { run.bodyScopeFailures++; throw failure; }
                finally { run.inBody = false; }
            };
            outside = invocation -> {
                var run = current.get();
                if (run == null || !invocation.getMethod().getName().equals("register")) { return invocation.proceed(); }
                if (++run.proxyEntries != 1 || invocation.getArguments().length != 3
                        || !Long.valueOf(run.orderId).equals(invocation.getArguments()[0])) {
                    throw fault(run, "Unexpected scoped service invocation", null);
                }
                run.outsideAtEntry = noTransaction(source);
                if (!run.outsideAtEntry) { throw fault(run, "HTTP service has an external transaction", null); }
                run.inProxy = true; run.events.add("PROXY_ENTERED");
                try {
                    var result = invocation.proceed(); // Includes original transaction commit/rollback and cleanup.
                    run.proxyReturns++; run.events.add("PROXY_RETURNED");
                    return result;
                } catch (Throwable failure) {
                    run.proxyFailures++; run.proxyFailure = failure; run.events.add("PROXY_FAILED");
                    throw failure;
                } finally {
                    run.outsideAtExit = noTransaction(source);
                    run.inProxy = false; run.events.add("PROXY_EXITED");
                    if (!run.outsideAtExit) { throw fault(run, "Transaction still bound after original proxy completion", null); }
                }
            };
            ReflectionTestUtils.setField(serviceTarget, "entityManager", decorated);
            try {
                advised.addAdvice(txIndex + 1, inside);
                advised.addAdvice(txIndex, outside);
                service = advised;
            } catch (RuntimeException | Error failure) {
                advised.removeAdvice(inside); advised.removeAdvice(outside);
                ReflectionTestUtils.setField(serviceTarget, "entityManager", manager);
                throw failure;
            }
            return bean;
        }

        private Run writing() { var run = current.get(); return run != null && run.inProxy ? run : null; }

        private void bindOwnedTransaction(Run run) throws SQLException {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || !TransactionSynchronizationManager.isSynchronizationActive()
                    || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                throw fault(run, "Body lacks a writable Spring transaction", null);
            }
            run.newTransaction = TransactionAspectSupport.currentTransactionStatus().isNewTransaction();
            var connection = DataSourceUtils.getConnection(source);
            try {
                run.boundConnection = DataSourceUtils.isConnectionTransactional(connection, source);
                run.autoCommitOff = !connection.getAutoCommit();
                run.isolation = connection.getTransactionIsolation();
                if (!Proxy.isProxyClass(connection.getClass())
                        || !(Proxy.getInvocationHandler(connection) instanceof ConnectionObserver observed)
                        || observed.probe != this) { throw fault(run, "Unobserved transaction connection", null); }
                run.connection = observed;
                run.databaseUrl = connection.getMetaData().getURL();
                String expected = environment.getRequiredProperty("spring.datasource.url").split(";", 2)[0];
                if (!run.databaseUrl.equals(expected) || !run.newTransaction || !run.boundConnection
                        || !run.autoCommitOff || run.isolation != Connection.TRANSACTION_READ_COMMITTED) {
                    throw fault(run, "Wrong database/transaction/isolation ownership", null);
                }
                run.threadId = Thread.currentThread().threadId();
                try (var query = connection.prepareStatement("select session_id()")) {
                    query.setQueryTimeout(2);
                    try (var rows = query.executeQuery()) {
                        if (!rows.next() || (run.sessionId = rows.getInt(1)) <= 0 || rows.next()) {
                            throw fault(run, "Missing physical H2 session identity", null);
                        }
                    }
                }
                run.events.add("OWNED_TRANSACTION_BOUND");
            } catch (SQLException failure) { throw fault(run, "Cannot identify transaction connection", failure); }
            finally { DataSourceUtils.releaseConnection(connection, source); }
        }

        @Override public void destroy() {
            try {
                if (service != null) { service.removeAdvice(outside); service.removeAdvice(inside); }
            } finally {
                if (serviceTarget != null && originalEntityManager != null) {
                    ReflectionTestUtils.setField(serviceTarget, "entityManager", originalEntityManager);
                }
            }
            if (!runs.isEmpty()) { throw new InstrumentationFailure("Unclosed/pending failure scopes at context shutdown", null); }
        }
    }

    private static final class ObservedDataSource extends DelegatingDataSource implements AutoCloseable {
        private final FailureProbe probe;
        private ObservedDataSource(DataSource target, FailureProbe probe) { super(target); this.probe = probe; }
        @Override public Connection getConnection() throws SQLException { return observe(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return observe(super.getConnection(user, password));
        }
        private Connection observe(Connection target) throws SQLException {
            try {
                var observer = new ConnectionObserver(probe, target);
                observer.proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, observer);
                return observer.proxy;
            } catch (RuntimeException | Error failure) {
                try { target.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }
        @Override public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) { closeable.close(); }
        }
    }

    private static final class ConnectionObserver implements InvocationHandler {
        private final FailureProbe probe;
        private final Connection target;
        private Connection proxy;
        private ConnectionObserver(FailureProbe probe, Connection target) { this.probe = probe; this.target = target; }

        @Override public Object invoke(Object ignored, Method method, Object[] args) throws Throwable {
            var run = probe.writing();
            boolean owned = run != null && run.connection == this;
            String name = method.getName();
            if (method.getDeclaringClass() == Object.class) { return objectMethod(proxy, method, args); }
            if (name.equals("unwrap") && args[0] == Connection.class) { return proxy; }
            if (name.equals("isWrapperFor") && args[0] == Connection.class) { return true; }
            if (run != null && name.equals("unwrap")) { throw fault(run, "Native connection unwrap would bypass observation", null); }
            if (owned && run.observing && List.of("commit", "rollback", "close", "setAutoCommit", "setReadOnly",
                    "setTransactionIsolation", "setSavepoint", "releaseSavepoint").contains(name)) {
                throw fault(run, "Point observation must only read the transaction, never change/complete it", null);
            }
            if (owned && name.equals("commit")) {
                requireTransaction(run);
                run.commitAttempts++; run.events.add("JDBC_COMMIT_ATTEMPT");
                hitJdbc(run, Point.JDBC_COMMIT_BEFORE_DELEGATE);
                run.delegatedCommits++; run.events.add("JDBC_COMMIT_DELEGATED");
            }
            if (owned && name.equals("rollback")) {
                if (args != null && args.length != 0) { throw fault(run, "Savepoint rollback is not full transaction rollback", null); }
                run.rollbackAttempts++; run.delegatedRollbacks++; run.events.add("JDBC_ROLLBACK_DELEGATED");
            }
            Object result;
            try { result = delegate(target, method, args); }
            catch (SQLException failure) {
                if (owned && (name.equals("commit") || name.equals("rollback"))) { run.events.add("JDBC_" + name.toUpperCase(Locale.ROOT) + "_FAILED:" + failure.getSQLState()); }
                throw failure;
            }
            if (owned && name.equals("commit")) { run.successfulCommits++; run.events.add("JDBC_COMMIT_SUCCEEDED"); }
            if (owned && name.equals("rollback")) { run.successfulRollbacks++; run.events.add("JDBC_ROLLBACK_SUCCEEDED"); }
            if (result instanceof Statement statement) {
                String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                if (owned) {
                    try { statement.setQueryTimeout(WAIT_SECONDS); }
                    catch (SQLException failure) {
                        try { statement.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                        throw fault(run, "Cannot bound scoped JDBC statement", failure);
                    }
                }
                return statement(statement, sql);
            }
            return result;
        }

        private Statement statement(Statement targetStatement, String prepared) {
            Class<?> type = targetStatement instanceof CallableStatement ? CallableStatement.class
                    : targetStatement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (statementProxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) { return objectMethod(statementProxy, method, args); }
                if (method.getName().equals("getConnection")) { return proxy; }
                var run = probe.writing();
                boolean owned = run != null && run.connection == this;
                if (owned && method.getName().equals("unwrap")) { throw fault(run, "Native statement unwrap would bypass observation", null); }
                String raw = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : "";
                String sql = raw.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
                boolean mutation = List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(sql::startsWith);
                if (owned && (method.getName().equals("executeBatch") || method.getName().equals("executeLargeBatch")
                        || mutation && method.getName().equals("addBatch"))) {
                    throw fault(run, "Batched DML requires separate per-execution instrumentation; cannot infer its first mutation", null);
                }
                boolean dml = owned && mutation && List.of("execute", "executeUpdate", "executeLargeUpdate").contains(method.getName());
                if (owned && run.observing && method.getName().startsWith("execute") && !sql.startsWith("select ")) {
                    throw fault(run, "Point observation may execute only SELECT", null);
                }
                if (dml) {
                    requireTransaction(run);
                    run.events.add("DML_EXECUTE_ATTEMPT");
                    if (run.dml.isEmpty()) { hitJdbc(run, Point.SQL_BEFORE_FIRST_DML); }
                }
                Object result;
                try { result = delegate(targetStatement, method, args); }
                catch (SQLException failure) {
                    if (dml) { run.failedDml.add(new FailedDml(sql, failure.getSQLState())); run.events.add("DRIVER_DML_FAILED:" + failure.getSQLState()); }
                    throw failure;
                }
                // Only successful driver execution with a positive count is a completed mutation.
                if (dml) {
                    long affected;
                    try { affected = result instanceof Number number ? number.longValue() : targetStatement.getLargeUpdateCount(); }
                    catch (SQLException failure) { throw fault(run, "Cannot observe successful driver's DML count", failure); }
                    run.dml.add(new Dml(sql, affected)); run.events.add("DRIVER_DML_RETURNED:" + affected);
                    if (run.dml.size() == 1) {
                        if (affected <= 0) { throw fault(run, "First DML returned no definite mutation count", null); }
                        hit(run, Point.AFTER_FIRST_DML);
                    }
                }
                return result;
            });
        }
    }

    private static void requireTransaction(Run run) {
        try {
            if (run.connection == null || Thread.currentThread().threadId() != run.threadId
                    || !TransactionSynchronizationManager.isActualTransactionActive()
                    || run.connection.target.getAutoCommit()
                    || run.connection.target.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw fault(run, "Point is not on the original writable transaction connection/thread", null);
            }
        } catch (SQLException failure) { throw fault(run, "Cannot observe transaction at point", failure); }
    }

    private static void hit(Run run, Point point) {
        if (reach(run, point)) { throw run.sentinel; }
    }

    private static void hitJdbc(Run run, Point point) throws SQLException {
        if (reach(run, point)) {
            throw new SQLException("T023 synthetic pre-delegation failure at " + point, "08006", run.sentinel);
        }
    }

    private static boolean reach(Run run, Point point) {
        if (run.point != point) { return false; }
        requireTransaction(run);
        if (++run.hits != 1) { throw fault(run, "Requested point reached more than once", null); }
        run.events.add("POINT_REACHED:" + point);
        run.observing = true;
        try { run.observation.verify(point, run.connection.proxy, run.state()); }
        catch (Throwable failure) { throw fault(run, "Point observation failed; not a functional rollback result", failure); }
        finally { run.observing = false; }
        run.injected = true; run.events.add("POINT_INJECTED:" + point);
        return true;
    }

    private static InstrumentationFailure fault(Run run, String message, Throwable cause) {
        var failure = new InstrumentationFailure(message, cause);
        if (run.instrumentationFailure == null) { run.instrumentationFailure = failure; }
        return failure;
    }
    private static boolean noTransaction(DataSource source) {
        return !TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isSynchronizationActive()
                && (source == null || !TransactionSynchronizationManager.hasResource(source));
    }
    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "PaymentObservedJdbc[redacted]";
            default -> throw new IllegalStateException("Unexpected Object method");
        };
    }
    private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
