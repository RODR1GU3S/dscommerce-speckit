package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Explicit import only. The real service, transaction interceptor, repositories and JDBC driver still run. */
@TestConfiguration(proxyBeanMethods = false)
public class AdminProductFailureInjectionTestConfig {
    @Bean
    public static FailureProbe t074FailureProbe() { return new FailureProbe(); }

    public enum Point { NONE, AFTER_DML, AFTER_FLUSH, BEFORE_COMMIT, JDBC_COMMIT_BEFORE_DELEGATE }

    @FunctionalInterface
    public interface PointObservation { void verify(Connection transactionConnection) throws Exception; }

    public record Dml(String sql, int affectedRows) { }
    public record FailedDml(String sql, String sqlState) { }

    public static final class InjectedFailure extends RuntimeException {
        private InjectedFailure(Point point) { super("T074 test-only failure at " + point); }
    }

    /** One synchronous request at a time; never armed during fixture setup, independent reads or cleanup. */
    public static final class Run {
        public final Point point;
        public final InjectedFailure sentinel;
        public final List<Dml> dml = new ArrayList<>();
        public final List<FailedDml> failedDml = new ArrayList<>();
        public PointObservation afterProductDml;
        public int afterProductDmlCalls;
        public String serviceMethod;
        private final PointObservation observation;
        private boolean maintenance;
        private Connection connection;
        public int proxyEntries, bodyEntries, bodyReturns, proxyReturns, proxyFailures, flushReturns;
        public int beforeCommits, commitAttempts, delegatedCommits, successfulCommits, rollbacks, successfulRollbacks;
        public int hits, completion = -1;
        public boolean originalServiceAdvised, outsideAtEntry, outsideAtExit, bodyTransaction, bodyReadOnly;
        public boolean reachedInTransaction, reachedWithAutoCommitOff, beforeCommitReadOnly, injected;
        public Throwable observationFailure, proxyFailure;

        private Run(Point point, PointObservation observation) {
            this.point = point; this.observation = observation; sentinel = new InjectedFailure(point);
        }
    }

    public static final class FailureProbe implements BeanPostProcessor {
        private final ThreadLocal<Run> armed = new ThreadLocal<>();

        public Run arm(Point point, PointObservation observation) {
            if (armed.get() != null) throw new IllegalStateException("T074 probe already armed");
            var run = new Run(point, observation); armed.set(run); return run;
        }

        public void disarm() { armed.remove(); }
        private Run writing() { var run = armed.get(); return run != null && run.maintenance ? run : null; }

        @Override
        public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) {
                return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                        (proxy, method, args) -> {
                            var result = delegate(source, method, args);
                            return result instanceof Connection connection ? connection(connection) : result;
                        });
            }
            if (bean instanceof ProductMaintenanceService) {
                // Refuse to create a substitute proxy: that could hide missing production transaction wiring.
                if (!(bean instanceof Advised advised)) throw new IllegalStateException("T074 requires the real service proxy");
                MethodInterceptor inside = invocation -> {
                    var run = writing();
                    if (run == null || !maintenanceMethod(invocation.getMethod().getName())) return invocation.proceed();
                    run.bodyEntries++;
                    run.bodyTransaction = TransactionSynchronizationManager.isActualTransactionActive();
                    run.bodyReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void beforeCommit(boolean readOnly) {
                            run.beforeCommits++; run.beforeCommitReadOnly = readOnly;
                            if (run.point == Point.BEFORE_COMMIT) { reach(run, run.connection); throw run.sentinel; }
                        }
                        @Override public void afterCompletion(int status) { run.completion = status; }
                    });
                    var result = invocation.proceed();
                    if (!run.serviceMethod.equals("deleteProduct") && !(result instanceof AdminProductDetailDTO)) {
                        throw new IllegalStateException("T074 expected body DTO");
                    }
                    run.bodyReturns++;
                    return result;
                };
                // Last in the existing advisor chain: observe the body while the real transaction is active.
                advised.addAdvice(inside);
                MethodInterceptor outside = invocation -> {
                    var run = armed.get();
                    if (run == null || !maintenanceMethod(invocation.getMethod().getName())) return invocation.proceed();
                    run.serviceMethod = invocation.getMethod().getName();
                    run.originalServiceAdvised = true; run.proxyEntries++;
                    run.outsideAtEntry = noTransaction(); run.maintenance = true;
                    try { var result = invocation.proceed(); run.proxyReturns++; return result; }
                    catch (Throwable failure) { run.proxyFailures++; run.proxyFailure = failure; throw failure; }
                    finally { run.outsideAtExit = noTransaction(); run.maintenance = false; }
                };
                advised.addAdvice(0, outside);
            } else if (bean instanceof ProductRepository) {
                if (!(bean instanceof Advised advised)) throw new IllegalStateException("T074 requires the real repository proxy");
                advised.addAdvice(0, (MethodInterceptor) invocation -> {
                    var result = invocation.proceed();
                    var run = writing();
                    if (run != null && List.of("flush", "saveAndFlush").contains(invocation.getMethod().getName())) {
                        run.flushReturns++;
                        if (run.point == Point.AFTER_FLUSH) { reach(run, run.connection); throw run.sentinel; }
                    }
                    return result;
                });
            }
            return bean;
        }

        private Connection connection(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        var run = writing();
                        if (run != null && method.getName().equals("commit")) {
                            run.commitAttempts++;
                            if (run.point == Point.JDBC_COMMIT_BEFORE_DELEGATE) {
                                reach(run, target);
                                // The real driver has NOT been called. This does not simulate a confirmed commit.
                                throw new SQLException("T074 simulated pre-delegation commit failure", "08006", run.sentinel);
                            }
                            run.delegatedCommits++;
                        }
                        if (run != null && method.getName().equals("rollback")) run.rollbacks++;
                        var result = delegate(target, method, args);
                        if (run != null && method.getName().equals("commit")) run.successfulCommits++;
                        if (run != null && method.getName().equals("rollback")) run.successfulRollbacks++;
                        if (result instanceof Statement statement) {
                            String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                            return statement(statement, sql, target);
                        }
                        return result;
                    });
        }

        private Statement statement(Statement target, String prepared, Connection connection) {
            Class<?> type = target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                var run = writing();
                String raw = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                String sql = raw == null ? "" : raw.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(Locale.ROOT);
                boolean dml = run != null && method.getName().startsWith("execute")
                        && List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(sql::startsWith);
                Object result;
                try { result = delegate(target, method, args); }
                catch (SQLException failure) {
                    if (dml) run.failedDml.add(new FailedDml(sql, failure.getSQLState()));
                    throw failure;
                }
                // Record only after successful execution by H2. A prepared statement alone is not DML proof.
                if (dml) {
                    int affected = result instanceof Number number ? number.intValue() : -1;
                    run.dml.add(new Dml(sql, affected)); run.connection = connection;
                    boolean productWrite = sql.startsWith("update tb_product ") || sql.startsWith("insert into tb_product ");
                    if (productWrite && run.afterProductDml != null && run.afterProductDmlCalls == 0) {
                        run.afterProductDmlCalls++;
                        try { run.afterProductDml.verify(connection); }
                        catch (Throwable failure) {
                            run.observationFailure = failure;
                            throw new IllegalStateException("T076 external reference-loss preparation failed", failure);
                        }
                    }
                    boolean firstMutation = productWrite || (run.serviceMethod.equals("deleteProduct")
                            && sql.startsWith("delete from tb_product_category "));
                    if (run.point == Point.AFTER_DML && firstMutation) {
                        reach(run, connection); throw run.sentinel;
                    }
                }
                return result;
            });
        }

        private static boolean maintenanceMethod(String name) {
            return List.of("createProduct", "replaceProduct", "deleteProduct").contains(name);
        }

        private static void reach(Run run, Connection connection) {
            run.hits++;
            run.reachedInTransaction = TransactionSynchronizationManager.isActualTransactionActive();
            try {
                if (connection == null) throw new IllegalStateException("T074 no real DML connection observed");
                run.reachedWithAutoCommitOff = !connection.getAutoCommit();
                run.observation.verify(connection);
            } catch (Throwable failure) {
                // Instrumentation/assertion failures must not masquerade as a functional rollback result.
                run.observationFailure = failure;
                throw new IllegalStateException("T074 point observation failed", failure);
            }
            run.injected = true;
            System.out.printf("T074_REACHED point=%s realDml=%d flushReturns=%d bodyReturns=%d beforeCommits=%d commitAttempts=%d delegatedCommits=%d tx=%s autoCommitOff=%s%n",
                    run.point, run.dml.size(), run.flushReturns, run.bodyReturns, run.beforeCommits,
                    run.commitAttempts, run.delegatedCommits, run.reachedInTransaction, run.reachedWithAutoCommitOff);
        }

        private static boolean noTransaction() {
            return !TransactionSynchronizationManager.isActualTransactionActive()
                    && !TransactionSynchronizationManager.isSynchronizationActive();
        }

        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException wrapped) { throw wrapped.getCause(); }
        }
    }
}
