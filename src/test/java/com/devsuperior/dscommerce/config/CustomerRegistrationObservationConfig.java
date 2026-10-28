package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.EventType;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * T009 authorship. Import explicitly in a context initialized by CustomerRegistrationTestConfig.
 * Intentionally a lite @Bean class: no component/configuration stereotype discoverable by scanning.
 * No business implementation, generated response, SQL/bind logging or production failpoint.
 */
@Profile("test")
public class CustomerRegistrationObservationConfig {

    public static final String HEADER = "X-Feature009-Observation";
    private static final String WORKER = "com.devsuperior.dscommerce.services.CustomerRegistrationTransaction";

    @Bean
    public static Observation customerRegistrationObservation(ConfigurableEnvironment environment) {
        return new Observation(environment);
    }

    @Bean
    public HibernatePropertiesCustomizer customerRegistrationFlushObservation(Observation observation) {
        Integrator integrator = new Integrator() {
            @Override
            public void integrate(Metadata metadata, BootstrapContext bootstrap, SessionFactoryImplementor factory) {
                var registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
                if (registry == null) { throw broken(); }
                // Keep the original Hibernate listeners; the trailing event is reached only on return.
                registry.prependListeners(EventType.FLUSH,
                        (FlushEventListener) event -> observation.hit(Point.FLUSH_BEFORE));
                registry.appendListeners(EventType.FLUSH,
                        (FlushEventListener) event -> observation.hit(Point.FLUSH_RETURNED));
                registry.prependListeners(EventType.AUTO_FLUSH,
                        (AutoFlushEventListener) event -> observation.hit(Point.AUTO_FLUSH_BEFORE));
                registry.appendListeners(EventType.AUTO_FLUSH,
                        (AutoFlushEventListener) event -> {
                            if (event.isFlushRequired()) { observation.hit(Point.AUTO_FLUSH_RETURNED); }
                            else { observation.hit(Point.AUTO_FLUSH_NOT_REQUIRED); }
                        });
            }

            @Override
            public void disintegrate(SessionFactoryImplementor factory, SessionFactoryServiceRegistry registry) { }
        };
        IntegratorProvider provider = () -> List.of(integrator);
        return properties -> {
            if (properties.putIfAbsent("hibernate.integrator_provider", provider) != null) {
                throw broken(); // Never replace another test's observer silently.
            }
        };
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> customerRegistrationObservationScope(Observation observation) {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                var id = request.getHeader(HEADER);
                if (id == null) { chain.doFilter(request, response); return; }
                var path = request.getRequestURI().substring(request.getContextPath().length());
                var attempt = observation.attempts.get(id);
                if (!"POST".equals(request.getMethod()) || !"/customers".equals(path) || attempt == null) {
                    throw broken();
                }
                attempt.enter();
                try { chain.doFilter(request, response); }
                finally { attempt.finish(); }
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    public enum Point {
        METADATA_ENTRY, METADATA_RETURNED, METADATA_FAILED,
        PROXY_ENTRY, WORKER_ENTRY, WORKER_RETURNED, PROXY_RETURNED, PROXY_FAILED,
        SQL_BEFORE, SQL_RETURNED, SQL_FAILED,
        ACCOUNT_INSERT_BEFORE, ACCOUNT_INSERT_RETURNED, ROLE_INSERT_BEFORE, ROLE_INSERT_RETURNED,
        FLUSH_BEFORE, FLUSH_RETURNED, AUTO_FLUSH_BEFORE, AUTO_FLUSH_RETURNED, AUTO_FLUSH_NOT_REQUIRED,
        BEFORE_COMMIT, AFTER_COMMIT, AFTER_COMPLETION,
        JDBC_COMMIT_BEFORE_DELEGATE, JDBC_COMMIT_DELEGATED, JDBC_COMMIT_RETURNED, JDBC_COMMIT_FAILED,
        JDBC_ROLLBACK_BEFORE_DELEGATE, JDBC_ROLLBACK_RETURNED, JDBC_ROLLBACK_FAILED
    }

    public enum SqlKind { SELECT, INSERT, UPDATE, DELETE, OTHER }
    public enum Table { ACCOUNT, ACCOUNT_ROLE, OTHER }

    /** No SQL text, parameters, DTO, entity, exception, name, email, password or hash is retained. */
    public record Event(long sequence, Point point, long threadId, long transactionId, long connectionId, int sessionId,
                        boolean transactionActive, boolean readOnly, boolean autoCommit, int isolation,
                        SqlKind sqlKind, Table table, int completion) { }

    /** Assertion failure means instrument preparation failed, not a RED business criterion. */
    public static final class InstrumentationFailure extends AssertionError {
        private InstrumentationFailure() { super("Customer registration test instrumentation is incomplete or misused"); }
    }

    private static InstrumentationFailure broken() { return new InstrumentationFailure(); }

    public static final class Observation implements BeanPostProcessor, Ordered, DisposableBean {
        private final ConfigurableEnvironment environment;
        private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
        private final ThreadLocal<Attempt> current = new ThreadLocal<>();
        private final AtomicLong connections = new AtomicLong();
        private ObservedDataSource source;
        private Advised worker;
        private MethodInterceptor outside;
        private MethodInterceptor inside;
        private Advised accounts;
        private MethodInterceptor metadata;

        private Observation(ConfigurableEnvironment environment) {
            this.environment = environment;
            requireContext();
        }

        private void requireContext() {
            var settings = environment.getPropertySources().get(CustomerRegistrationTestConfig.PROPERTY_SOURCE_NAME);
            var url = environment.getProperty("spring.datasource.url", "");
            if (!environment.acceptsProfiles(Profiles.of("test")) || settings == null
                    || !url.equals(settings.getProperty("spring.datasource.url"))
                    || !url.startsWith("jdbc:h2:mem:customer-registration-test-")
                    || !"create-drop".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw broken();
            }
        }

        @Override public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

        /** Does not demand or manufacture the future worker; inspect workerInstalled when required. */
        public Attempt open() {
            requireContext();
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isSynchronizationActive() || source == null) { throw broken(); }
            var attempt = new Attempt(this);
            attempts.put(attempt.id, attempt);
            return attempt;
        }

        public boolean workerInstalled() { return worker != null; }

        void requireOwned(Attempt attempt) {
            if (attempt == null || attempt.observation != this || attempts.get(attempt.id) != attempt) { throw broken(); }
        }

        private void hit(Point point) {
            var attempt = current.get();
            if (attempt != null) { attempt.hit(point, attempt.connection, Sql.NONE, -1); }
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            if ("dataSource".equals(name) && bean instanceof DataSource delegate) {
                if (source != null) { throw broken(); }
                source = new ObservedDataSource(delegate, this);
                return source;
            }
            if (bean instanceof UserAccountRepository && bean instanceof Advised advised) {
                if (accounts != null || advised.isFrozen()) { throw broken(); }
                metadata = invocation -> {
                    var attempt = current.get();
                    if (attempt == null || !invocation.getMethod().getName().equals("registrationIdentityMetadata")) {
                        return invocation.proceed();
                    }
                    hit(Point.METADATA_ENTRY);
                    try {
                        var result = invocation.proceed(); // Includes the original read-only transaction completion.
                        hit(Point.METADATA_RETURNED);
                        return result;
                    } catch (Throwable failure) {
                        hit(Point.METADATA_FAILED);
                        throw failure;
                    }
                };
                advised.addAdvice(0, metadata);
                accounts = advised;
                return bean;
            }
            if (!WORKER.equals(AopUtils.getTargetClass(bean).getName())) { return bean; }
            if (worker != null || !(bean instanceof Advised advised) || advised.isFrozen()) { throw broken(); }
            int index = -1;
            for (int i = 0; i < advised.getAdvisors().length; i++) {
                if (advised.getAdvisors()[i].getAdvice() instanceof TransactionInterceptor) {
                    if (index >= 0) { throw broken(); }
                    index = i;
                }
            }
            if (index < 0) { throw broken(); }
            outside = invocation -> {
                var attempt = current.get();
                if (attempt == null || invocation.getMethod().getDeclaringClass() == Object.class) {
                    return invocation.proceed();
                }
                if (TransactionSynchronizationManager.isActualTransactionActive()
                        || TransactionSynchronizationManager.hasResource(source) || ++attempt.proxyEntries != 1) {
                    throw broken();
                }
                attempt.workerActive = true;
                hit(Point.PROXY_ENTRY);
                try {
                    var result = invocation.proceed(); // Original Spring transaction interceptor commits here.
                    hit(Point.PROXY_RETURNED);
                    return result;
                } catch (Throwable failure) {
                    hit(Point.PROXY_FAILED);
                    throw failure;
                } finally {
                    attempt.workerActive = false;
                    if (TransactionSynchronizationManager.isActualTransactionActive()
                            || TransactionSynchronizationManager.hasResource(source)) { throw broken(); }
                }
            };
            inside = invocation -> {
                var attempt = current.get();
                if (attempt == null || invocation.getMethod().getDeclaringClass() == Object.class) {
                    return invocation.proceed();
                }
                if (!TransactionAspectSupport.currentTransactionStatus().isNewTransaction()
                        || !TransactionSynchronizationManager.isActualTransactionActive()
                        || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) { throw broken(); }
                var connection = DataSourceUtils.getConnection(source);
                try {
                    if (!DataSourceUtils.isConnectionTransactional(connection, source)
                            || !Proxy.isProxyClass(connection.getClass())
                            || !(Proxy.getInvocationHandler(connection) instanceof ConnectionObserver observer)
                            || observer.observation != this) { throw broken(); }
                    observer.bind(attempt);
                } finally { DataSourceUtils.releaseConnection(connection, source); }
                hit(Point.WORKER_ENTRY);
                var result = invocation.proceed();
                hit(Point.WORKER_RETURNED); // A returned DTO is deliberately not inspected or retained.
                return result;
            };
            try {
                advised.addAdvice(index + 1, inside);
                advised.addAdvice(index, outside);
                worker = advised;
            } catch (RuntimeException | Error failure) {
                advised.removeAdvice(inside);
                advised.removeAdvice(outside);
                throw failure;
            }
            return bean;
        }

        @Override
        public void destroy() {
            if (worker != null) { worker.removeAdvice(outside); worker.removeAdvice(inside); }
            if (accounts != null) { accounts.removeAdvice(metadata); }
            if (!attempts.isEmpty()) { throw broken(); }
        }
    }

    /** One attempt, one request/call thread. Configure callbacks before enter; always close after join. */
    public static final class Attempt implements AutoCloseable {
        private final Observation observation;
        private final String id = UUID.randomUUID().toString();
        private final EnumMap<Point, List<Runnable>> actions = new EnumMap<>(Point.class);
        private final List<Event> events = new ArrayList<>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private volatile boolean started;
        private boolean closed;
        private long owner;
        private int proxyEntries;
        private boolean workerActive;
        private ConnectionObserver connection;
        private ConnectionObserver activeConnection;
        private boolean synchronizationBound;
        private long transactionId;

        private Attempt(Observation observation) { this.observation = observation; }
        public String headerValue() { return id; }

        /** May coordinate bounded latches outside any monitor; never block on a second completed INSERT. */
        public synchronized void on(Point point, Runnable action) {
            if (started || closed) { throw broken(); }
            switch (Objects.requireNonNull(point)) {
                case JDBC_COMMIT_DELEGATED, JDBC_COMMIT_FAILED, JDBC_ROLLBACK_FAILED,
                        AFTER_COMPLETION, PROXY_FAILED, SQL_FAILED,
                        METADATA_ENTRY, METADATA_RETURNED, METADATA_FAILED -> throw broken();
                default -> { }
            }
            actions.computeIfAbsent(Objects.requireNonNull(point), ignored -> new ArrayList<>())
                    .add(Objects.requireNonNull(action));
        }

        /** Target the original worker, while still recording preceding metadata transactions unchanged. */
        public void onWorker(Point point, Runnable action) {
            Objects.requireNonNull(action);
            on(point, () -> { if (workerActive) { action.run(); } });
        }

        public <T> T call(Callable<T> operation) throws Exception {
            enter();
            try { return operation.call(); }
            finally { finish(); }
        }

        private synchronized void enter() {
            if (started || closed || observation.current.get() != null) { throw broken(); }
            started = true;
            owner = Thread.currentThread().threadId();
            observation.current.set(this);
        }

        private void finish() {
            observation.current.remove();
            finished.countDown();
        }

        private void hit(Point point, ConnectionObserver connection, Sql sql, int completion) {
            if (Thread.currentThread().threadId() != owner) { throw broken(); }
            events.add(new Event(events.size() + 1L, point, owner, transactionId,
                    connection == null ? 0 : connection.id, connection == null ? 0 : connection.sessionId,
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    connection == null || connection.autoCommitAtBind,
                    connection == null ? -1 : connection.isolationAtBind,
                    sql.kind, sql.table, completion));
            for (var action : actions.getOrDefault(point, List.of())) { action.run(); }
        }

        public void awaitFinished() {
            try {
                if (!finished.await(10, TimeUnit.SECONDS)) { throw broken(); }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw broken();
            }
        }

        /** Read on the owner thread at a callback or after awaitFinished; no throwable is retained. */
        public List<Event> events() {
            if (finished.getCount() != 0 && observation.current.get() != this) { throw broken(); }
            return List.copyOf(events);
        }

        @Override
        public synchronized void close() {
            if (closed) { return; }
            if (started && finished.getCount() != 0) { throw broken(); }
            closed = true;
            observation.attempts.remove(id, this);
            actions.clear();
        }

        @Override public String toString() { return "CustomerRegistrationAttempt[content=omitted]"; }
    }

    private static final class ObservedDataSource extends DelegatingDataSource implements AutoCloseable {
        private final Observation observation;
        private ObservedDataSource(DataSource delegate, Observation observation) { super(delegate); this.observation = observation; }
        @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return wrap(super.getConnection(user, password));
        }
        private Connection wrap(Connection target) throws SQLException {
            try {
                var observer = new ConnectionObserver(target, observation);
                observer.proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, observer);
                return observer.proxy;
            } catch (RuntimeException | Error failure) {
                try { target.close(); } catch (SQLException ignored) { /* Keep the original failure. */ }
                throw failure;
            }
        }
        @Override public void close() throws Exception {
            if (getTargetDataSource() instanceof AutoCloseable closeable) { closeable.close(); }
        }
    }

    private static final class ConnectionObserver implements java.lang.reflect.InvocationHandler {
        private final Connection target;
        private final Observation observation;
        private final long id;
        private Connection proxy;
        private int sessionId;
        private boolean autoCommitAtBind;
        private int isolationAtBind = -1;

        private ConnectionObserver(Connection target, Observation observation) {
            this.target = target;
            this.observation = observation;
            id = observation.connections.incrementAndGet();
        }

        private void bind(Attempt attempt) throws SQLException {
            autoCommitAtBind = target.getAutoCommit();
            isolationAtBind = target.getTransactionIsolation();
            if (sessionId == 0) {
                var expected = observation.environment.getRequiredProperty("spring.datasource.url").split(";", 2)[0];
                if (!expected.equals(target.getMetaData().getURL())) { throw broken(); }
                // Read identity on the ORIGINAL connection, without recursive instrumentation or bind capture.
                try (var statement = target.createStatement()) {
                    statement.setQueryTimeout(2);
                    try (var rows = statement.executeQuery("select session_id()")) {
                        if (!rows.next() || (sessionId = rows.getInt(1)) <= 0 || rows.next()) { throw broken(); }
                    }
                }
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                if (attempt.synchronizationBound) {
                    if (attempt.activeConnection != this) { throw broken(); }
                    return;
                }
                if (autoCommitAtBind || isolationAtBind != Connection.TRANSACTION_READ_COMMITTED) {
                    throw broken();
                }
                attempt.connection = this;
                attempt.activeConnection = this;
                attempt.synchronizationBound = true;
                attempt.transactionId++;
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        attempt.hit(Point.BEFORE_COMMIT, ConnectionObserver.this, Sql.NONE, -1);
                    }
                    @Override public void afterCommit() {
                        attempt.hit(Point.AFTER_COMMIT, ConnectionObserver.this, Sql.NONE, -1);
                    }
                    @Override public void afterCompletion(int status) {
                        try { attempt.hit(Point.AFTER_COMPLETION, ConnectionObserver.this, Sql.NONE, status); }
                        finally {
                            attempt.synchronizationBound = false;
                            attempt.activeConnection = null;
                        }
                    }
                });
            }
        }

        @Override
        public Object invoke(Object ignored, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) { return identity(proxy, method, args); }
            var name = method.getName();
            var attempt = observation.current.get();
            if (attempt != null && (name.equals("commit") || name.equals("rollback")) && method.getParameterCount() == 0) {
                bind(attempt);
                boolean commit = name.equals("commit");
                attempt.hit(commit ? Point.JDBC_COMMIT_BEFORE_DELEGATE : Point.JDBC_ROLLBACK_BEFORE_DELEGATE,
                        this, Sql.NONE, -1);
                // DELEGATED has no user callback: the next operation is always the original driver call.
                if (commit) { attempt.hit(Point.JDBC_COMMIT_DELEGATED, this, Sql.NONE, -1); }
                Object result;
                try { result = delegate(target, method, args); }
                catch (Throwable failure) {
                    attempt.hit(commit ? Point.JDBC_COMMIT_FAILED : Point.JDBC_ROLLBACK_FAILED, this, Sql.NONE, -1);
                    throw failure;
                }
                attempt.hit(commit ? Point.JDBC_COMMIT_RETURNED : Point.JDBC_ROLLBACK_RETURNED, this, Sql.NONE, -1);
                return result;
            }
            var result = delegate(target, method, args);
            if (result instanceof Statement statement && (name.equals("createStatement")
                    || name.equals("prepareStatement") || name.equals("prepareCall"))) {
                var sql = args != null && args.length > 0 && args[0] instanceof String text ? describe(text) : Sql.NONE;
                return statement(statement, sql);
            }
            return result;
        }

        private Statement statement(Statement targetStatement, Sql prepared) {
            Class<?> type = targetStatement instanceof CallableStatement ? CallableStatement.class
                    : targetStatement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            var batch = new ArrayList<Sql>();
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (self, method, args) -> {
                if (method.getDeclaringClass() == Object.class) { return identity(self, method, args); }
                var name = method.getName();
                if (name.equals("getConnection")) { return proxy; }
                var sql = args != null && args.length > 0 && args[0] instanceof String text ? describe(text) : prepared;
                if (name.equals("addBatch")) {
                    var result = delegate(targetStatement, method, args);
                    batch.add(sql);
                    return result;
                }
                if (name.equals("clearBatch")) {
                    var result = delegate(targetStatement, method, args);
                    batch.clear();
                    return result;
                }
                var attempt = observation.current.get();
                if (attempt == null || !name.startsWith("execute")) { return delegate(targetStatement, method, args); }
                bind(attempt);
                boolean batched = name.equals("executeBatch") || name.equals("executeLargeBatch");
                var operations = batched ? List.copyOf(batch) : List.of(sql);
                for (var operation : operations) { beforeSql(attempt, operation); }
                Object result;
                try { result = delegate(targetStatement, method, args); }
                catch (Throwable failure) {
                    attempt.hit(Point.SQL_FAILED, this, Sql.NONE, -1);
                    throw failure; // BatchUpdateException may contain partial success; never invent returned inserts.
                } finally { if (batched) { batch.clear(); } }
                for (int i = 0; i < operations.size(); i++) {
                    long count = result instanceof int[] counts ? (i < counts.length ? counts[i] : Statement.EXECUTE_FAILED)
                            : result instanceof long[] counts ? (i < counts.length ? counts[i] : Statement.EXECUTE_FAILED) : 1;
                    if (count == Statement.EXECUTE_FAILED) { attempt.hit(Point.SQL_FAILED, this, operations.get(i), -1); }
                    else { afterSql(attempt, operations.get(i)); }
                }
                return result;
            });
        }

        private void beforeSql(Attempt attempt, Sql sql) {
            attempt.hit(Point.SQL_BEFORE, this, sql, -1);
            if (sql.kind == SqlKind.INSERT && sql.table == Table.ACCOUNT) {
                attempt.hit(Point.ACCOUNT_INSERT_BEFORE, this, sql, -1);
            } else if (sql.kind == SqlKind.INSERT && sql.table == Table.ACCOUNT_ROLE) {
                attempt.hit(Point.ROLE_INSERT_BEFORE, this, sql, -1);
            }
        }

        private void afterSql(Attempt attempt, Sql sql) {
            attempt.hit(Point.SQL_RETURNED, this, sql, -1);
            if (sql.kind == SqlKind.INSERT && sql.table == Table.ACCOUNT) {
                attempt.hit(Point.ACCOUNT_INSERT_RETURNED, this, sql, -1);
            } else if (sql.kind == SqlKind.INSERT && sql.table == Table.ACCOUNT_ROLE) {
                // Table identity only: the actual CLIENT value still requires the independent database probe.
                attempt.hit(Point.ROLE_INSERT_RETURNED, this, sql, -1);
            }
        }
    }

    private record Sql(SqlKind kind, Table table) {
        private static final Sql NONE = new Sql(SqlKind.OTHER, Table.OTHER);
    }

    private static final Pattern SQL = Pattern.compile(
            "(?is)^\\s*(?:/\\*.*?\\*/\\s*)*(insert\\s+into|update|delete\\s+from)\\s+(?:public\\.)?([a-z_][a-z0-9_]*).*");
    private static final Pattern SELECT = Pattern.compile(
            "(?is)^\\s*(?:/\\*.*?\\*/\\s*)*(select)\\b.*?\\bfrom\\s+(?:public\\.)?([a-z_][a-z0-9_]*).*");

    /** Only enum classifications survive this call; comments/literals/binds never enter the event store. */
    private static Sql describe(String text) {
        var match = SQL.matcher(text);
        if (!match.matches()) {
            match = SELECT.matcher(text);
            if (!match.matches()) { return Sql.NONE; }
        }
        var operation = match.group(1).toLowerCase(Locale.ROOT);
        var kind = operation.startsWith("insert") ? SqlKind.INSERT : operation.startsWith("delete") ? SqlKind.DELETE
                : operation.equals("update") ? SqlKind.UPDATE : SqlKind.SELECT;
        var table = switch (match.group(2).toLowerCase(Locale.ROOT)) {
            case "tb_user_account" -> Table.ACCOUNT;
            case "tb_user_account_role" -> Table.ACCOUNT_ROLE;
            default -> Table.OTHER;
        };
        return new Sql(kind, table);
    }

    private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getTargetException(); }
    }

    private static Object identity(Object self, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> self == args[0];
            case "hashCode" -> System.identityHashCode(self);
            case "toString" -> "CustomerRegistrationJdbc[content=omitted]";
            default -> throw broken();
        };
    }
}
