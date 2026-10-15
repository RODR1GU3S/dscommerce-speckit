package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.CategoryData;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T052 only: two real HTTP writers overlap; database blocker metadata distinguishes waiting from scheduling. */
@SpringBootTest(classes = DscommerceApplication.class, properties = {
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.jdbc.batch_size=0",
        "spring.datasource.hikari.connection-init-sql=SET LOCK_TIMEOUT 30000"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductConcurrencyIntegrationTest.ProbeConfig.class)
class AdminProductConcurrencyIntegrationTest {
    private static final int WAIT_SECONDS = 15;
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private Probe probe;
    @Autowired private ProductMaintenanceService maintenance;
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe database;
    private MockMvc mvc;
    private ExecutorService workers;
    private final List<Future<MvcResult>> requests = new ArrayList<>();
    private final List<Long> orders = new ArrayList<>();
    private List<Slot> slots;
    private Snapshot baseline;
    private long target;
    private long buyer;
    private Version original;

    @BeforeEach
    void prepareCommittedPrivateCatalogAndHistoricalOrderWithRealAuthentication() throws Exception {
        outsideTransaction(); probe.active = false;
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context);
        requests.clear(); orders.clear();
        workers = Executors.newFixedThreadPool(2);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        var adminA = fixture.createAccount("T052 admin A", "Disposable-T052", Set.of(UserRole.ADMIN));
        var adminB = fixture.createAccount("T052 admin B", "Disposable-T052", Set.of(UserRole.ADMIN));
        var buyerAccount = fixture.prepareBuyer(); buyer = buyerAccount.id();
        var old = fixture.createCategory("Original compartilhada");
        var a1 = fixture.createCategory("A esquerda"); var a2 = fixture.createCategory("A direita");
        var b1 = fixture.createCategory("B unica");
        var product = fixture.createProduct("Produto original", "Descricao original completa",
                "https://example.invalid/original-t052.jpg", new BigDecimal("10.00"));
        target = product.id();
        var other = fixture.createProduct("Produto independente", "Descricao independente completa",
                "https://example.invalid/other-t052.jpg", new BigDecimal("25.00"));
        fixture.associate(target, old.id()); fixture.associate(other.id(), old.id());
        original = new Version(new ProductRow(target, product.name(), product.description(), product.image(), product.price()), List.of(old));
        slots = List.of(new Slot("A", fixture.login(adminA), new Version(new ProductRow(target, "Produto A completo",
                        "Descricao A substituida integralmente", "https://example.invalid/a-t052.jpg", new BigDecimal("12.00")), List.of(a1, a2))),
                new Slot("B", fixture.login(adminB), new Version(new ProductRow(target, "Produto B completo",
                        "Descricao B substituida integralmente", "https://example.invalid/b-t052.jpg", new BigDecimal("19.00")), List.of(b1))));
        assertThat(database.roles(adminA.id())).containsExactly(UserRole.ADMIN);
        assertThat(database.roles(adminB.id())).containsExactly(UserRole.ADMIN);
        var response = mvc.perform(post("/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(buyerAccount))
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[{\"productId\":" + target + ",\"quantity\":2}]}"))
                .andReturn();
        database.snapshot().orders().forEach(order -> orders.add(order.id()));
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        baseline = database.snapshot();
        assertThat(baseline.products()).hasSize(2); assertThat(baseline.categories()).hasSize(4);
        assertThat(baseline.associations()).hasSize(2);
        assertHistory(baseline);
        assertThat(maintenance).isInstanceOf(Advised.class);
        assertThat(((Advised) maintenance).getAdvisors()).anyMatch(a -> a.getAdvice() instanceof TransactionInterceptor);
        probe.events.clear(); probe.active = true;
    }

    @ParameterizedTest(name = "first lock holder={0}, both HTTP writers overlap")
    @ValueSource(strings = {"A", "B"})
    void serializesCompleteReplacementsAfterRealBlockingAndReleasesBothLocks(String firstName) throws Exception {
        var first = slots.stream().filter(slot -> slot.name.equals(firstName)).findFirst().orElseThrow();
        var second = slots.stream().filter(slot -> slot != first).findFirst().orElseThrow();
        var firstRequest = submit(first);
        await(first.acquired, "first lock acquired");
        assertThat(first.loaded).isEqualTo(original);
        var secondRequest = submit(second); // first transaction remains open and owns the product lock
        await(second.lockSqlEntered, "second JDBC FOR UPDATE entered");
        assertThat(first.session.get()).isPositive();
        assertThat(second.session.get()).isPositive().isNotEqualTo(first.session.get());
        assertBlocked(first, second, "after-first-lookup");
        assertThat(second.acquired.getCount()).isEqualTo(1);
        assertThat(firstRequest.isDone()).isFalse(); assertThat(secondRequest.isDone()).isFalse();
        assertThat(first.dml).isEmpty(); assertThat(second.dml).isEmpty();
        assertThat(database.snapshot()).isEqualTo(baseline);

        first.allowMutation.countDown();
        await(first.flushed, "first real flush returned");
        assertThat(first.flushedVersion).isEqualTo(first.desired);
        assertAssociationWrites(first);
        assertThat(first.commits.get()).isZero(); assertThat(first.completion.get()).isEqualTo(-1);
        assertBlocked(first, second, "after-first-flush");
        assertThat(second.acquired.getCount()).isEqualTo(1); assertThat(second.dml).isEmpty();
        assertThat(database.snapshot()).as("first flushed state remains uncommitted").isEqualTo(baseline);

        first.allowCommit.countDown();
        await(second.acquired, "second acquires only after first database commit releases lock");
        var firstResponse = firstRequest.get(WAIT_SECONDS, TimeUnit.SECONDS);
        assertResponse(firstResponse, first.desired);
        assertCommitted(first);
        assertThat(second.loaded).as("second transaction sees all first values and exact associations").isEqualTo(first.desired);
        assertThat(second.dml).isEmpty(); assertThat(secondRequest.isDone()).isFalse();
        assertThat(database.snapshot()).isEqualTo(expected(first.desired));
        // Driver commit releases the DB lock before its Java wrapper necessarily records its return.
        assertThat(probe.index(first, "COMMIT_BEGIN")).isLessThan(probe.index(second, "LOCK_ACQUIRED"));
        assertThat(probe.index(first, "FLUSH_RETURN")).isLessThan(probe.index(second, "LOCK_ACQUIRED"));

        second.allowMutation.countDown();
        await(second.flushed, "second real flush returned");
        assertThat(second.flushedVersion).isEqualTo(second.desired);
        assertAssociationWrites(second);
        assertThat(second.commits.get()).isZero(); assertThat(second.completion.get()).isEqualTo(-1);
        assertThat(database.snapshot()).as("second flush does not publish a mixed or new state before commit").isEqualTo(expected(first.desired));
        second.allowCommit.countDown();
        assertResponse(secondRequest.get(WAIT_SECONDS, TimeUnit.SECONDS), second.desired);
        assertCommitted(second);
        outsideTransaction();
        var after = database.snapshot();
        assertThat(after).isEqualTo(expected(second.desired));
        assertHistory(after);
        assertFinalLockAvailable();
        assertThat(database.snapshot()).isEqualTo(after);
        assertThat(database.accountIds()).hasSize(3);
        for (var slot : slots) {
            assertThat(slot.entries.get()).isEqualTo(1); assertThat(slot.returns.get()).isEqualTo(1);
            assertThat(slot.flushReturns.get()).isEqualTo(1);
            assertThat(slot.dml).allMatch(sql -> sql.startsWith("update tb_product ")
                    || sql.startsWith("delete from tb_product_category ") || sql.startsWith("insert into tb_product_category "));
        }
        System.out.printf("T052_CONCURRENCY first=%s second=%s sessions=%d,%d blockedAtLookup=true blockedAfterFlush=true versions=ORIGINAL->%s->%s dml=%d,%d commits=2 rollbacks=0 finalLockAvailable=true history=20.00 independentSnapshot=true%n",
                first.name, second.name, first.session.get(), second.session.get(), first.name, second.name, first.dml.size(), second.dml.size());
        System.out.println("T052_TRACE " + probe.events);
    }

    private Future<MvcResult> submit(Slot slot) throws Exception {
        var body = mapper.createObjectNode().put("name", slot.desired.row().name()).put("description", slot.desired.row().description())
                .put("image", slot.desired.row().image()).put("price", slot.desired.row().price().toPlainString());
        var ids = body.putArray("categoryIds");
        slot.desired.categories().forEach(category -> ids.add(category.id()));
        ids.add(slot.desired.categories().get(0).id()); // duplicates must not create extra pairs during either replacement
        var bytes = mapper.writeValueAsBytes(body);
        var future = workers.submit(() -> {
            outsideTransaction(); probe.writer.set(slot);
            try {
                String route = "/admin/products/" + target;
                var result = mvc.perform(put("/api" + route).contextPath("/api").servletPath(route)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + slot.token).contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON).content(bytes)).andReturn();
                outsideTransaction(); return result;
            } finally { probe.writer.remove(); }
        });
        requests.add(future); return future;
    }

    private void assertBlocked(Slot holder, Slot contender, String phase) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String lastState = "absent"; Integer lastBlocker = null;
        try (var connection = database.openConnection(); var query = connection.prepareStatement(
                "SELECT SESSION_STATE, BLOCKER_ID, EXECUTING_STATEMENT FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?")) {
            query.setQueryTimeout(2); query.setInt(1, contender.session.get());
            while (System.nanoTime() < deadline) {
                try (var rows = query.executeQuery()) {
                    if (rows.next()) {
                        lastState = rows.getString(1); int blocker = rows.getInt(2); lastBlocker = rows.wasNull() ? null : blocker;
                        if ("BLOCKED".equals(lastState) && Integer.valueOf(holder.session.get()).equals(lastBlocker)) {
                            assertThat(rows.getString(3).toLowerCase(Locale.ROOT)).contains("tb_product", "for update");
                            System.out.printf("T052_BLOCK phase=%s holder=%d contender=%d state=%s blocker=%d%n",
                                    phase, holder.session.get(), contender.session.get(), lastState, lastBlocker);
                            return;
                        }
                    }
                }
                // Bounded polling of actual H2 blocker metadata; elapsed time alone never proves blocking.
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            }
        }
        throw new AssertionError("Expected real JDBC lock wait, observed state=" + lastState + ", blocker=" + lastBlocker);
    }

    private Snapshot expected(Version version) {
        var products = baseline.products().stream().map(row -> row.id() == target ? version.row() : row).toList();
        var pairs = new HashSet<>(baseline.associations()); pairs.removeIf(pair -> pair.productId() == target);
        version.categories().forEach(category -> pairs.add(new ProductCategory(target, category.id())));
        return new Snapshot(products, baseline.categories(), pairs, baseline.orders(), baseline.items());
    }
    private void assertResponse(MvcResult result, Version version) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        var row = version.row();
        var expected = mapper.createObjectNode().put("id", row.id()).put("name", row.name()).put("description", row.description())
                .put("image", row.image()).put("price", row.price().toPlainString());
        var categories = expected.putArray("categories");
        version.categories().forEach(category -> categories.addObject().put("id", category.id()).put("name", category.name()));
        // Compare JSON representations, without requiring Jackson's IntNode/LongNode implementation choice.
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray()))
                .isEqualTo(mapper.readTree(mapper.writeValueAsBytes(expected)));
    }
    private void assertCommitted(Slot slot) {
        assertThat(slot.commits.get()).isEqualTo(1); assertThat(slot.rollbacks.get()).isZero();
        assertThat(slot.completion.get()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
    }
    private void assertAssociationWrites(Slot slot) {
        assertThat(slot.dml.stream().filter(sql -> sql.startsWith("update tb_product "))).hasSize(1);
        assertThat(slot.dml).anyMatch(sql -> sql.startsWith("delete from tb_product_category "));
        assertThat(slot.dml.stream().filter(sql -> sql.startsWith("insert into tb_product_category "))).hasSize(slot.desired.categories().size());
    }
    private void assertHistory(Snapshot snapshot) {
        assertThat(snapshot.orders()).hasSize(1); assertThat(snapshot.orders().get(0).buyerId()).isEqualTo(buyer);
        assertThat(snapshot.items()).hasSize(1);
        var item = snapshot.items().get(0);
        assertThat(item.orderId()).isEqualTo(snapshot.orders().get(0).id()); assertThat(item.productId()).isEqualTo(target);
        assertThat(item.position()).isZero(); assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("20.00"));
    }
    private void assertFinalLockAvailable() throws Exception {
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var query = connection.prepareStatement("SELECT id FROM tb_product WHERE id = ? FOR UPDATE NOWAIT")) {
                query.setQueryTimeout(1); query.setLong(1, target);
                try (var rows = query.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(target); }
            } finally { connection.rollback(); }
        }
    }
    private static void await(CountDownLatch latch, String stage) throws InterruptedException {
        assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).as(stage).isTrue();
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmpty() {
        var state = database.snapshot();
        assertThat(state.products()).isEmpty(); assertThat(state.categories()).isEmpty(); assertThat(state.associations()).isEmpty();
        assertThat(state.orders()).isEmpty(); assertThat(state.items()).isEmpty(); assertThat(database.accountIds()).isEmpty();
    }
    @AfterEach
    void releaseAndJoinBothWritersBeforeOwnedCleanup() throws Exception {
        if (slots != null) slots.forEach(slot -> { slot.allowMutation.countDown(); slot.allowCommit.countDown(); });
        if (workers != null) {
            workers.shutdown();
            if (!workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)) {
                workers.shutdownNow(); assertThat(workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            }
            for (var request : requests) request.get(WAIT_SECONDS, TimeUnit.SECONDS);
        }
        probe.active = false; outsideTransaction();
        if (fixture == null) return;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var items = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var order = connection.prepareStatement("delete from tb_order where id = ?");
                 var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?")) {
                for (long id : orders) { items.setLong(1, id); items.executeUpdate(); order.setLong(1, id); order.executeUpdate(); }
                pairs.setLong(1, target); pairs.executeUpdate(); connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }

    private record Version(ProductRow row, List<CategoryData> categories) {
        Version { categories = categories.stream().sorted(Comparator.comparing(CategoryData::name).thenComparingLong(CategoryData::id)).toList(); }
        static Version capture(Product product) {
            return new Version(new ProductRow(product.getId(), product.getName(), product.getDescription(), product.getImage(), product.getPrice()),
                    product.getCategories().stream().map(category -> new CategoryData(category.getId(), category.getName())).toList());
        }
    }
    private static final class Slot {
        final String name;
        final String token;
        final Version desired;
        final CountDownLatch lockSqlEntered = new CountDownLatch(1), acquired = new CountDownLatch(1), allowMutation = new CountDownLatch(1),
                flushed = new CountDownLatch(1), allowCommit = new CountDownLatch(1);
        final AtomicInteger session = new AtomicInteger(), entries = new AtomicInteger(), returns = new AtomicInteger(),
                flushReturns = new AtomicInteger(), commits = new AtomicInteger(), rollbacks = new AtomicInteger(), completion = new AtomicInteger(-1);
        final List<String> dml = new CopyOnWriteArrayList<>();
        volatile Product entity;
        volatile Version loaded;
        volatile Version flushedVersion;
        Slot(String name, String token, Version desired) { this.name = name; this.token = token; this.desired = desired; }
    }
    private record Event(String writer, String stage) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig { @Bean static Probe t052Probe() { return new Probe(); } }

    /** Test-only, transparent Spring/JDBC observation; barriers never replace business results. */
    public static class Probe implements BeanPostProcessor {
        volatile boolean active;
        final ThreadLocal<Slot> writer = new ThreadLocal<>();
        private final ThreadLocal<Boolean> writing = ThreadLocal.withInitial(() -> false);
        final List<Event> events = new CopyOnWriteArrayList<>();
        private synchronized void event(Slot slot, String stage) { events.add(new Event(slot.name, stage)); }
        int index(Slot slot, String stage) { int index = events.indexOf(new Event(slot.name, stage)); assertThat(index).isNotNegative(); return index; }
        private Slot current() { return active && writing.get() ? writer.get() : null; }

        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) {
                return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    var result = delegate(source, method, args); return result instanceof Connection connection ? connection(connection) : result;
                });
            }
            boolean service = bean instanceof ProductMaintenanceService;
            boolean repository = bean instanceof ProductRepository;
            if (!(service || repository)) return bean;
            MethodInterceptor observer = invocation -> {
                var slot = writer.get(); String method = invocation.getMethod().getName();
                if (!active || slot == null) return invocation.proceed();
                if (service && method.equals("replaceProduct")) {
                    outsideTransaction(); writing.set(true); slot.entries.incrementAndGet(); event(slot, "SERVICE_ENTER");
                    try { var result = invocation.proceed(); outsideTransaction(); slot.returns.incrementAndGet(); event(slot, "SERVICE_RETURN"); return result; }
                    finally { writing.remove(); }
                }
                if (repository && method.equals("findAdminByIdForUpdate") && current() != null) {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int status) { slot.completion.set(status); event(slot, "COMPLETION_" + status); }
                    });
                    var found = (java.util.Optional<?>) invocation.proceed();
                    slot.entity = (Product) found.orElseThrow(); slot.loaded = Version.capture(slot.entity);
                    event(slot, "LOCK_ACQUIRED"); slot.acquired.countDown();
                    await(slot.allowMutation, slot.name + " allowed to mutate"); return found;
                }
                if (repository && method.equals("flush") && current() != null) {
                    var result = invocation.proceed(); slot.flushReturns.incrementAndGet(); slot.flushedVersion = Version.capture(slot.entity);
                    event(slot, "FLUSH_RETURN"); slot.flushed.countDown();
                    await(slot.allowCommit, slot.name + " allowed to complete"); return result;
                }
                return invocation.proceed();
            };
            if (bean instanceof Advised advised) { advised.addAdvice(0, observer); return bean; }
            var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true); proxy.addAdvice(observer); return proxy.getProxy();
        }
        private Connection connection(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                var slot = current(); String name = method.getName();
                if (slot != null && name.equals("commit")) event(slot, "COMMIT_BEGIN");
                var result = delegate(target, method, args);
                if (slot != null && name.equals("commit")) { slot.commits.incrementAndGet(); event(slot, "COMMIT_RETURN"); }
                if (slot != null && name.equals("rollback")) { slot.rollbacks.incrementAndGet(); event(slot, "ROLLBACK_RETURN"); }
                if (result instanceof Statement statement) {
                    String prepared = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                    return statement(statement, prepared, target);
                }
                return result;
            });
        }
        private Statement statement(Statement target, String prepared, Connection connection) {
            Class<?> type = target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                var slot = current();
                String sql = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                boolean observed = slot != null && sql != null && method.getName().startsWith("execute");
                String normalized = sql == null ? "" : sql.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(Locale.ROOT);
                if (observed) {
                    assertThat(connection.getAutoCommit()).isFalse();
                    assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
                    try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT SESSION_ID()")) {
                        assertThat(rows.next()).isTrue(); int session = rows.getInt(1);
                        slot.session.compareAndSet(0, session); assertThat(slot.session.get()).isEqualTo(session);
                    }
                    if (normalized.startsWith("select ") && normalized.contains("tb_product") && normalized.contains("for update")) {
                        event(slot, "LOCK_SQL_ENTER"); slot.lockSqlEntered.countDown();
                    }
                }
                var result = delegate(target, method, args);
                if (observed && List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(normalized::startsWith)) {
                    slot.dml.add(normalized); event(slot, "DML_RETURN");
                }
                return result;
            });
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); } catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
