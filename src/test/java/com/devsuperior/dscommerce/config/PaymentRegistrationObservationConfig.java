package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.config.OrderTestConfig.ControlledClock;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Explicit import alongside PaymentRegistrationTestConfig, in isolated 008 test contexts only.
 * Preparation, not a test or a payment implementation. No fake service or replacement proxy.
 * Each window/attempt must be closed in finally/try-with-resources, before fixture cleanup.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class PaymentRegistrationObservationConfig {

    public static final String HEADER = "X-Feature008-Observation";
    private static final String SERVICE = "com.devsuperior.dscommerce.services.PaymentRegistrationService";
    private static final int JDBC_TIMEOUT_SECONDS = 10;

    /** Keep the original concrete ControlledClock injectable by T003; only Clock injection is decorated. */
    @Bean
    public static BeanFactoryPostProcessor paymentObservationContext(ConfigurableEnvironment environment) {
        return factory -> {
            if (!factory.containsBeanDefinition("paymentTestClock")) {
                throw new IllegalStateException("Import PaymentRegistrationTestConfig explicitly first");
            }
            factory.getBeanDefinition("paymentTestClock").setPrimary(false);
            // Local to this explicitly imported test context. No global profile or URL is changed.
            var initSql = "set lock_timeout 20000";
            var existing = environment.getProperty("spring.datasource.hikari.connection-init-sql");
            if (existing != null && !existing.equals(initSql)) {
                throw new IllegalStateException("Conflicting test connection initialization SQL");
            }
            environment.getPropertySources().addFirst(new MapPropertySource("paymentObservationContext",
                    Map.of("spring.datasource.hikari.connection-init-sql", initSql)));
        };
    }

    @Bean
    public static Observation paymentObservation(ConfigurableListableBeanFactory factory) {
        return new Observation(factory);
    }

    @Bean
    @Primary
    public Clock paymentObservedClock(@Qualifier("paymentTestClock") ControlledClock delegate,
                                       Observation observation) {
        return new BusinessClock(delegate, observation);
    }

    @Bean
    public HibernatePropertiesCustomizer paymentScopedSqlObservation(Observation observation) {
        return properties -> {
            var previous = properties.putIfAbsent("hibernate.session_factory.statement_inspector", observation);
            if (previous != null && previous != observation) {
                throw new IllegalStateException("Conflicting SQL inspector in the payment observation context");
            }
        };
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> paymentObservationRequestScope(Observation observation) {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                var window = observation.active.get();
                var id = request.getHeader(HEADER);
                var attempt = window == null || id == null ? null : window.requests.get(id);
                if (attempt == null) { chain.doFilter(request, response); return; }
                if (attempt.started.incrementAndGet() != 1) {
                    throw new IllegalStateException("Use a fresh observation attempt for each HTTP request");
                }
                observation.current.set(attempt);
                try { chain.doFilter(request, response); }
                finally { observation.current.remove(); attempt.finished.countDown(); }
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    public enum Phase { PROXY_ENTRY, BODY_ENTRY, REFERENCE_READ, ORDER_LOCK_SQL, PROXY_RETURN }
    public enum SqlScope { OUTSIDE_SERVICE, SERVICE_BODY, PROXY_BOUNDARY }

    /** Inspected SQL only, without bind values; this is not proof of execution or lock acquisition. */
    public record SqlEvent(String sql, SqlScope scope, boolean transactionActive, long threadId) {
        @Override public String toString() { return "PaymentSqlEvent[scope=" + scope + ", sql=redacted]"; }
    }

    public static final class Observation implements BeanPostProcessor, StatementInspector, Ordered {
        private final ConfigurableListableBeanFactory factory;
        private final AtomicReference<Window> active = new AtomicReference<>();
        private final ThreadLocal<Attempt> current = new ThreadLocal<>();
        private volatile boolean serviceInstalled;

        private Observation(ConfigurableListableBeanFactory factory) { this.factory = factory; }

        @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

        /** Opening a window does not claim the still-absent service exists; assertions below require it. */
        public Window openWindow(Duration timeout) {
            if (timeout == null || timeout.isZero() || timeout.isNegative()
                    || timeout.compareTo(Duration.ofSeconds(10)) > 0) {
                throw new IllegalArgumentException("Observation timeout must be positive and at most 10 seconds");
            }
            var window = new Window(this, timeout);
            if (!active.compareAndSet(null, window)) {
                window.workers.shutdownNow();
                throw new IllegalStateException("Close the previous observation window first");
            }
            return window;
        }

        @Override
        public String inspect(String sql) {
            var attempt = current.get();
            if (attempt != null) {
                var scope = attempt.inBody ? SqlScope.SERVICE_BODY
                        : attempt.inProxy ? SqlScope.PROXY_BOUNDARY : SqlScope.OUTSIDE_SERVICE;
                attempt.sql.add(new SqlEvent(sql, scope,
                        TransactionSynchronizationManager.isActualTransactionActive(), Thread.currentThread().threadId()));
                if (attempt.inBody && sql != null && sql.matches("(?is).*\\btb_order\\b.*\\bfor\\s+update\\b.*")) {
                    attempt.hit(Phase.ORDER_LOCK_SQL);
                }
            }
            return sql;
        }

        private void clockRead(Instant value) {
            var attempt = current.get();
            // Filter/JWT/fixture clock calls, including ones in the same request, are not business reads.
            if (attempt != null && attempt.inBody) {
                if (attempt.clockCalls.incrementAndGet() == 1) { attempt.reference = value; }
                attempt.hit(Phase.REFERENCE_READ);
            }
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            if (!name.equals("paymentRegistrationService")) { return bean; }
            if (!SERVICE.equals(AopUtils.getTargetClass(bean).getName()) || !(bean instanceof Advised advised)) {
                throw new IllegalStateException("Payment observation requires the real Spring service proxy");
            }
            int transactionIndex = -1;
            for (int index = 0; index < advised.getAdvisors().length; index++) {
                if (advised.getAdvisors()[index].getAdvice() instanceof TransactionInterceptor) {
                    if (transactionIndex != -1) { throw new IllegalStateException("Ambiguous transaction interceptor"); }
                    transactionIndex = index;
                }
            }
            if (transactionIndex < 0) { throw new IllegalStateException("Missing real transaction interceptor"); }
            MethodInterceptor outside = invocation -> {
                var attempt = current.get();
                if (attempt == null || invocation.getMethod().getDeclaringClass() == Object.class) {
                    return invocation.proceed();
                }
                var source = factory.getBean(DataSource.class);
                attempt.proxyEntries++;
                attempt.outsideAtEntry = noTransaction(source);
                attempt.inProxy = true;
                try {
                    attempt.hit(Phase.PROXY_ENTRY);
                    var result = invocation.proceed();
                    attempt.proxyReturned = true;
                    attempt.outsideAtExit = noTransaction(source);
                    attempt.hit(Phase.PROXY_RETURN);
                    return result;
                } catch (Throwable failure) {
                    attempt.proxyFailed = true;
                    throw failure;
                } finally {
                    attempt.outsideAtExit = noTransaction(source);
                    attempt.inProxy = false;
                    attempt.proxyFinished = true;
                }
            };
            MethodInterceptor inside = invocation -> {
                var attempt = current.get();
                if (attempt == null || invocation.getMethod().getDeclaringClass() == Object.class) {
                    return invocation.proceed();
                }
                attempt.bodyEntries++;
                attempt.transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
                attempt.readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                if (attempt.transactionActive) {
                    attempt.newTransaction = TransactionAspectSupport.currentTransactionStatus().isNewTransaction();
                    var source = factory.getBean(DataSource.class);
                    var connection = DataSourceUtils.getConnection(source);
                    try {
                        attempt.boundConnection = DataSourceUtils.isConnectionTransactional(connection, source);
                        attempt.isolation = connection.getTransactionIsolation();
                        attempt.autoCommitOff = !connection.getAutoCommit();
                    } finally { DataSourceUtils.releaseConnection(connection, source); }
                }
                attempt.inBody = true;
                try { attempt.hit(Phase.BODY_ENTRY); return invocation.proceed(); }
                finally { attempt.inBody = false; }
            };
            // Observe both sides of the ORIGINAL interceptor; do not manufacture a substitute proxy.
            advised.addAdvice(transactionIndex, outside);
            advised.addAdvice(transactionIndex + 2, inside);
            serviceInstalled = true;
            return bean;
        }
    }

    /** Delegates the exact instant, without truncation or an extra read; raw ControlledClock stays mutable. */
    private static final class BusinessClock extends Clock {
        private final Clock delegate;
        private final Observation observation;
        private BusinessClock(Clock delegate, Observation observation) {
            this.delegate = delegate; this.observation = observation;
        }
        @Override public ZoneId getZone() { return delegate.getZone(); }
        @Override public Clock withZone(ZoneId zone) { return new BusinessClock(delegate.withZone(zone), observation); }
        @Override public Instant instant() {
            var value = delegate.instant();
            observation.clockRead(value);
            return value;
        }
    }

    public static final class Attempt {
        private final Window window;
        private final String id = UUID.randomUUID().toString();
        private final AtomicInteger started = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);
        private final Map<Phase, Gate> gates = new ConcurrentHashMap<>();
        private final List<SqlEvent> sql = new CopyOnWriteArrayList<>();
        private final AtomicInteger clockCalls = new AtomicInteger();
        private volatile Instant reference;
        private volatile boolean inProxy, inBody, outsideAtEntry, outsideAtExit, proxyReturned, proxyFailed, proxyFinished;
        private volatile boolean transactionActive, readOnly, newTransaction, boundConnection, autoCommitOff;
        private volatile int proxyEntries, bodyEntries, isolation = -1;

        private Attempt(Window window) { this.window = window; }
        public String id() { return id; }
        public Instant reference() { return reference; }
        public int clockCalls() { return clockCalls.get(); }
        public List<SqlEvent> sql() { return List.copyOf(sql); }
        public boolean proxyReturned() { return proxyReturned; }
        public boolean proxyFailed() { return proxyFailed; }

        /** Install BEFORE submitting the request. Each attempt has independent gates. */
        public Gate pauseAt(Phase phase) {
            window.requireOpen();
            if (started.get() != 0) { throw new IllegalStateException("Install gates before the request starts"); }
            var gate = new Gate(window.timeout);
            if (gates.putIfAbsent(Objects.requireNonNull(phase), gate) != null) {
                throw new IllegalStateException("Gate already installed for this phase");
            }
            return gate;
        }

        private void hit(Phase phase) {
            var gate = gates.get(phase);
            if (gate != null) { gate.hit(); }
        }

        public void awaitFinished() { await(finished, window.timeout, "marked HTTP request completion"); }

        /** Invoke after asserting the expected HTTP status, not as setup for the initial missing-route RED. */
        public void assertOwnedTransactionObserved() {
            if (!window.observation.serviceInstalled) { throw new AssertionError("Payment service was not installed"); }
            if (started.get() != 1 || proxyEntries != 1 || bodyEntries != 1 || !proxyFinished
                    || !outsideAtEntry || !outsideAtExit || !transactionActive || readOnly || !newTransaction
                    || !boundConnection || !autoCommitOff || isolation != Connection.TRANSACTION_READ_COMMITTED
                    || clockCalls.get() != 1 || reference == null) {
                throw new AssertionError("Missing owned READ_COMMITTED transaction or single business Clock evidence");
            }
        }

        @Override public String toString() { return "PaymentAttempt[sqlEvents=" + sql.size() + ", result=redacted]"; }
    }

    public static final class Gate implements AutoCloseable {
        private final Duration timeout;
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private Gate(Duration timeout) { this.timeout = timeout; }
        private void hit() { reached.countDown(); await(released, timeout, "test gate release"); }
        public void awaitReached() { await(reached, timeout, "observed phase"); }
        public void release() { released.countDown(); }
        @Override public void close() { release(); }
    }

    /** One owner manages lifecycle/registrations; submitted requests may run concurrently. */
    public static final class Window implements AutoCloseable {
        private final Observation observation;
        private final Duration timeout;
        private final Map<String, Attempt> requests = new ConcurrentHashMap<>();
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Future<?>> work = new CopyOnWriteArrayList<>();
        private final List<IndependentTransaction> transactions = new CopyOnWriteArrayList<>();
        private final AtomicBoolean closed = new AtomicBoolean();

        private Window(Observation observation, Duration timeout) { this.observation = observation; this.timeout = timeout; }
        private void requireOpen() {
            if (closed.get()) { throw new IllegalStateException("Observation window is closed"); }
        }
        public Attempt newRequest() {
            requireOpen();
            var attempt = new Attempt(this);
            requests.put(attempt.id, attempt);
            return attempt;
        }
        public <T> Future<T> submit(Callable<T> operation) {
            requireOpen();
            var future = workers.submit(operation);
            work.add(future);
            return future;
        }
        public <T> T awaitResult(Future<T> future) throws InterruptedException, ExecutionException, TimeoutException {
            if (!work.contains(future)) { throw new IllegalArgumentException("Work must belong to this window"); }
            try { return future.get(timeout.toNanos(), TimeUnit.NANOSECONDS); }
            catch (TimeoutException failure) { future.cancel(true); throw failure; }
        }

        /** Driver connection, never the application's pool or thread-bound transaction. */
        public IndependentTransaction independentTransaction(PaymentRegistrationDatabaseProbe database) throws SQLException {
            requireOpen();
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Open auxiliary transactions outside a caller transaction");
            }
            var connection = database.openConnection();
            try {
                if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                    throw new IllegalStateException("Independent connection must use READ_COMMITTED");
                }
                try (var statement = connection.createStatement()) {
                    statement.setQueryTimeout(JDBC_TIMEOUT_SECONDS);
                    // H2 session only, not a global property or another context's database.
                    statement.execute("set lock_timeout 10000");
                }
                connection.setAutoCommit(false);
                var transaction = new IndependentTransaction(connection);
                transactions.add(transaction);
                return transaction;
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }

        /** Close on the owning test thread, after awaiting requests; this is also failure-path cleanup. */
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) { return; }
            Throwable cleanupFailure = null;
            try {
                requests.values().forEach(attempt -> attempt.gates.values().forEach(Gate::release));
                // Release held JDBC locks before waiting for work that might be blocked on those locks.
                for (var transaction : transactions) {
                    try { transaction.close(); }
                    catch (SQLException | RuntimeException failure) { cleanupFailure = collect(cleanupFailure, failure); }
                }
                workers.shutdown();
                try {
                    if (!workers.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                        work.forEach(future -> future.cancel(true));
                        workers.shutdownNow();
                        if (!workers.awaitTermination(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                            cleanupFailure = collect(cleanupFailure,
                                    new AssertionError("Payment observation workers did not terminate"));
                        }
                    }
                } catch (InterruptedException failure) {
                    work.forEach(future -> future.cancel(true));
                    workers.shutdownNow();
                    Thread.currentThread().interrupt();
                    cleanupFailure = collect(cleanupFailure, failure);
                }
                // Never hide a worker failure merely because its thread terminated normally.
                for (var future : work) {
                    try { future.get(0, TimeUnit.NANOSECONDS); }
                    catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        cleanupFailure = collect(cleanupFailure, failure);
                    } catch (ExecutionException | TimeoutException | java.util.concurrent.CancellationException failure) {
                        cleanupFailure = collect(cleanupFailure, failure);
                    }
                }
                // Client completion and servlet-filter completion are separate observations.
                for (var attempt : requests.values()) {
                    if (attempt.started.get() != 0) {
                        try { attempt.awaitFinished(); }
                        catch (AssertionError failure) { cleanupFailure = collect(cleanupFailure, failure); }
                    }
                }
            } finally {
                workers.shutdownNow();
                observation.active.compareAndSet(this, null);
            }
            if (cleanupFailure != null) { throw new AssertionError("Payment observation cleanup failed", cleanupFailure); }
        }
    }

    public static final class IndependentTransaction implements AutoCloseable {
        private final Connection connection;
        private boolean completed, closed;
        private IndependentTransaction(Connection connection) { this.connection = connection; }
        public PreparedStatement prepare(String sql) throws SQLException {
            if (closed || completed) { throw new IllegalStateException("Auxiliary transaction is no longer active"); }
            var statement = connection.prepareStatement(sql);
            try { statement.setQueryTimeout(JDBC_TIMEOUT_SECONDS); return statement; }
            catch (SQLException failure) {
                try { statement.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }
        public void commit() throws SQLException {
            requireActive(); connection.commit(); completed = true;
        }
        public void rollback() throws SQLException {
            requireActive(); connection.rollback(); completed = true;
        }
        private void requireActive() {
            if (closed || completed) { throw new IllegalStateException("Auxiliary transaction is no longer active"); }
        }
        @Override public void close() throws SQLException {
            if (closed) { return; }
            closed = true;
            try { if (!completed) { connection.rollback(); } }
            finally { connection.close(); }
        }
    }

    private static boolean noTransaction(DataSource source) {
        return !TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isSynchronizationActive()
                && !TransactionSynchronizationManager.hasResource(source);
    }

    private static Throwable collect(Throwable first, Throwable next) {
        if (first == null) { return next; }
        first.addSuppressed(next);
        return first;
    }

    private static void await(CountDownLatch latch, Duration timeout, String label) {
        try {
            if (!latch.await(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new AssertionError("Observation timeout waiting for " + label);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Observation interrupted waiting for " + label, failure);
        }
    }
}
