package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.AdminProductFailureInjectionTestConfig.FailureProbe;
import com.devsuperior.dscommerce.config.AdminProductFailureInjectionTestConfig.Point;
import com.devsuperior.dscommerce.config.AdminProductFailureInjectionTestConfig.Run;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.security.AdminDiagnosticLoggingFilter;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.io.IOException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T074/US5.5/V11: four distinct PUT failure boundaries, not a physical crash or post-commit recovery proof. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductFailureInjectionTestConfig.class)
class AdminProductRollbackIntegrationTest {
    private static final String NAME = "T074 replacement";
    private static final String DESCRIPTION = "T074 complete replacement description";
    private static final String IMAGE = "https://example.invalid/t074-new.jpg";
    private static final BigDecimal PRICE = new BigDecimal("12.00");
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private FailureProbe probe;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private OrderService orders;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private ProductData target;
    private ProductData other;
    private long oldCategory, newCategory;
    private String token;
    private final Set<Long> ownedOrders = new HashSet<>();
    private final Set<Long> ownedCreatedProducts = new HashSet<>();

    @BeforeEach
    void prepareCommittedRowsAndHistoryWithoutArmingFailureInjection() {
        outsideTransaction(); probe.disarm(); ownedOrders.clear(); ownedCreatedProducts.clear();
        assertThat(AopUtils.isAopProxy(maintenance)).isTrue();
        assertThat(AopUtils.isAopProxy(orders)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty(); fixture = new AdminProductTestFixture(context);
        var admin = fixture.createAccount("T074 admin", "Disposable-T074", Set.of(UserRole.ADMIN));
        token = fixture.login(admin);
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        oldCategory = fixture.createCategory("T074 old category").id();
        newCategory = fixture.createCategory("T074 new category").id();
        target = fixture.createProduct("T074 original", "T074 original description", "https://example.invalid/t074-old.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("T074 unrelated", "T074 unrelated description", "https://example.invalid/t074-other.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), oldCategory); fixture.associate(other.id(), newCategory);
        var buyer = fixture.prepareBuyer();
        try {
            var order = orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(target.id(), 2))),
                    new AuthenticatedAccountPrincipal(buyer.id()));
            assertThat(order.total()).isEqualTo("20.00");
        } finally { database.snapshot().orders().forEach(order -> ownedOrders.add(order.id())); }
        var state = database.snapshot();
        assertThat(state.products()).hasSize(2); assertThat(state.categories()).hasSize(2);
        assertThat(state.associations()).contains(new ProductCategory(target.id(), oldCategory), new ProductCategory(other.id(), newCategory));
        assertThat(state.orders()).hasSize(1); assertThat(state.items()).hasSize(1);
        var item = state.items().getFirst();
        assertThat(item.productId()).isEqualTo(target.id()); assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("20.00"));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(AdminDiagnosticLoggingFilter.class),
                context.getBean(FilterChainProxy.class)).build();
        outsideTransaction();
    }

    @ParameterizedTest(name = "T074 PUT failure={0}")
    @EnumSource(value = Point.class, mode = EnumSource.Mode.EXCLUDE, names = "NONE")
    void rollsBackRealWritesAtEachDistinctFailureBoundary(Point point) throws Exception {
        var before = database.snapshot();
        var run = probe.arm(point, connection -> observeUncommittedChange(point, connection, before));
        MvcResult result;
        Snapshot after;
        try { result = replace(); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }

        // Establish that setup/instrumentation succeeded before interpreting any functional assertion.
        assertThat(run.observationFailure).as("T074 observation failure is infrastructure, never functional RED").isNull();
        assertThat(run.hits).as("the selected failure boundary was reached exactly once").isEqualTo(1);
        assertThat(run.injected).isTrue(); assertThat(run.reachedInTransaction).isTrue();
        assertThat(run.reachedWithAutoCommitOff).isTrue(); assertProxyAndRealUpdate(run);
        assertThat(run.proxyFailures).isEqualTo(1); assertThat(run.proxyReturns).isZero();
        assertThat(causeContains(run.proxyFailure, run.sentinel)).as("the injected failure escaped through the real proxy").isTrue();
        boolean afterBody = point == Point.BEFORE_COMMIT || point == Point.JDBC_COMMIT_BEFORE_DELEGATE;
        assertThat(run.bodyReturns).isEqualTo(afterBody ? 1 : 0);
        assertThat(run.flushReturns).isEqualTo(point == Point.AFTER_DML ? 0 : 1);
        assertThat(run.beforeCommits).isEqualTo(afterBody ? 1 : 0);
        assertThat(run.beforeCommitReadOnly).isFalse();
        assertThat(run.commitAttempts).isEqualTo(point == Point.JDBC_COMMIT_BEFORE_DELEGATE ? 1 : 0);
        assertThat(run.delegatedCommits).isZero(); assertThat(run.successfulCommits).isZero();
        System.out.printf("T074_OUTCOME point=%s status=%d rollbackCalls=%d rollbackSucceeded=%d completion=%d snapshotEqual=%s proxyReturned=%d%n",
                point, result.getResponse().getStatus(), run.rollbacks, run.successfulRollbacks, run.completion, after.equals(before), run.proxyReturns);

        assertThat(after).as("all five table snapshots, read independently after the proxy and before cleanup").isEqualTo(before);
        if (point == Point.JDBC_COMMIT_BEFORE_DELEGATE) {
            // A framework may report UNKNOWN after a commit exception even when JDBC rollback is observed.
            // Pool cleanup can also roll back beneath the wrapper; the independent snapshot is the state oracle.
            assertThat(run.completion).isIn(TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_UNKNOWN);
        } else {
            assertThat(run.rollbacks).isPositive(); assertThat(run.successfulRollbacks).isPositive();
            assertThat(run.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        assertSafe500(result);
        System.out.printf("T074_PROOF point=%s independentSnapshot=true noPartialRows=true safe500=true originalProxy=true noOuterTransaction=true%n", point);
    }

    @Test
    void unarmedFailurePointAllowsTheSameRealReplacementToCommitVisibly() throws Exception {
        var before = database.snapshot(); var run = probe.arm(Point.NONE, connection -> { });
        MvcResult result;
        Snapshot after;
        try { result = replace(); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }
        assertProxyAndRealUpdate(run);
        assertThat(run.hits).isZero(); assertThat(run.injected).isFalse();
        assertThat(run.bodyReturns).isEqualTo(1); assertThat(run.proxyReturns).isEqualTo(1); assertThat(run.proxyFailures).isZero();
        assertThat(run.flushReturns).isEqualTo(1); assertThat(run.beforeCommits).isEqualTo(1);
        assertThat(run.commitAttempts).isEqualTo(1); assertThat(run.delegatedCommits).isEqualTo(1);
        assertThat(run.successfulCommits).isEqualTo(1); assertThat(run.rollbacks).isZero();
        assertThat(run.completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var products = before.products().stream().map(row -> row.id() == target.id()
                ? new ProductRow(target.id(), NAME, DESCRIPTION, IMAGE, PRICE) : row).toList();
        var pairs = before.associations().stream().filter(pair -> pair.productId() != target.id()).collect(Collectors.toSet());
        pairs.add(new ProductCategory(target.id(), newCategory));
        assertThat(after).isEqualTo(new Snapshot(products, before.categories(), pairs, before.orders(), before.items()));
        var response = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(response.path("id").longValue()).isEqualTo(target.id()); assertThat(response.path("price").asText()).isEqualTo("12.00");
        System.out.println("T074_CONTROL PUT status=200 driverCommit=1 independentStateVisible=true historyPreserved=true");
    }

    private void observeUncommittedChange(Point point, Connection connection, Snapshot before) throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(connection.getAutoCommit()).isFalse();
        try (var independent = database.openConnection()) { assertThat(independent).isNotSameAs(connection); }
        assertThat(database.snapshot()).as("the other connection still sees the committed fixture at the failure point").isEqualTo(before);
        try (var query = connection.prepareStatement("select name, description, image, price from tb_product where id=?")) {
            query.setLong(1, target.id());
            try (var row = query.executeQuery()) {
                assertThat(row.next()).isTrue(); assertThat(row.getString("name")).isEqualTo(NAME);
                assertThat(row.getString("description")).isEqualTo(DESCRIPTION); assertThat(row.getString("image")).isEqualTo(IMAGE);
                assertThat(row.getBigDecimal("price")).isEqualTo(PRICE); assertThat(row.next()).isFalse();
            }
        }
        if (point != Point.AFTER_DML) {
            try (var query = connection.prepareStatement("select category_id from tb_product_category where product_id=?")) {
                query.setLong(1, target.id());
                try (var row = query.executeQuery()) {
                    assertThat(row.next()).isTrue(); assertThat(row.getLong(1)).isEqualTo(newCategory); assertThat(row.next()).isFalse();
                }
            }
        }
    }

    private void assertProxyAndRealUpdate(Run run) {
        assertThat(run.originalServiceAdvised).isTrue(); assertThat(run.proxyEntries).isEqualTo(1); assertThat(run.bodyEntries).isEqualTo(1);
        assertThat(run.outsideAtEntry).isTrue(); assertThat(run.outsideAtExit).isTrue();
        assertThat(run.bodyTransaction).isTrue(); assertThat(run.bodyReadOnly).isFalse();
        assertThat(run.dml).anySatisfy(event -> { assertThat(event.sql()).startsWith("update tb_product "); assertThat(event.affectedRows()).isEqualTo(1); });
    }

    private MvcResult replace() throws Exception {
        outsideTransaction();
        var body = mapper.createObjectNode().put("name", NAME).put("description", DESCRIPTION).put("image", IMAGE).put("price", "12.00");
        body.putArray("categoryIds").add(newCategory);
        return mvc.perform(put("/admin/products/" + target.id()).servletPath("/admin/products/" + target.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andReturn();
    }

    private void assertSafe500(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        var bytes = result.getResponse().getContentAsByteArray(); var body = mapper.readTree(bytes);
        var fields = new HashSet<String>(); body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(body.path("status").intValue()).isEqualTo(500); assertThat(body.path("error").asText()).isEqualTo("Internal Server Error");
        assertThat(body.path("message").asText()).isEqualTo("Product maintenance could not be completed");
        assertThat(body.path("path").asText()).isEqualTo("/admin/products/" + target.id()); assertThat(body.path("errors").isArray()).isTrue();
        assertThat(body.path("errors")).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains(token)).as("token is not echoed").isFalse();
    }

    private static boolean causeContains(Throwable failure, Throwable expected) {
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        while (failure != null && seen.add(failure)) { if (failure == expected) return true; failure = failure.getCause(); }
        return false;
    }

    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    enum Operation { POST, PUT, DELETE }

    static Stream<Arguments> t076FailureCases() {
        return Stream.of(Operation.POST, Operation.DELETE).flatMap(operation ->
                Stream.of(Point.AFTER_DML, Point.AFTER_FLUSH, Point.BEFORE_COMMIT, Point.JDBC_COMMIT_BEFORE_DELEGATE)
                        .map(point -> Arguments.of(operation, point)));
    }

    @ParameterizedTest(name = "T076 {0} failure={1}")
    @MethodSource("t076FailureCases")
    void t076PostAndDeleteRollback(Operation operation, Point point) throws Exception {
        var before = database.snapshot();
        var run = probe.arm(point, connection -> t076ObserveMutation(operation, point, connection, before));
        MvcResult result;
        Snapshot after;
        try { result = t076Perform(operation, false, List.of(newCategory)); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }
        assertThat(run.observationFailure).as("T076 preparation/instrumentation must succeed before interpreting rollback").isNull();
        assertThat(run.hits).isEqualTo(1); assertThat(run.injected).isTrue();
        assertThat(run.reachedInTransaction).isTrue(); assertThat(run.reachedWithAutoCommitOff).isTrue();
        t076AssertRealProxy(run);
        assertThat(run.dml).anySatisfy(event -> {
            assertThat(event.sql()).startsWith(operation == Operation.POST ? "insert into tb_product " : "delete from tb_product_category ");
            assertThat(event.affectedRows()).isEqualTo(1);
        });
        assertThat(run.proxyFailures).isEqualTo(1); assertThat(run.proxyReturns).isZero();
        assertThat(causeContains(run.proxyFailure, run.sentinel)).isTrue();
        boolean bodyCompleted = point == Point.BEFORE_COMMIT || point == Point.JDBC_COMMIT_BEFORE_DELEGATE;
        assertThat(run.bodyReturns).isEqualTo(bodyCompleted ? 1 : 0);
        assertThat(run.flushReturns).isEqualTo(point == Point.AFTER_DML ? 0 : 1);
        assertThat(run.beforeCommits).isEqualTo(bodyCompleted ? 1 : 0);
        assertThat(run.commitAttempts).isEqualTo(point == Point.JDBC_COMMIT_BEFORE_DELEGATE ? 1 : 0);
        assertThat(run.delegatedCommits).isZero(); assertThat(run.successfulCommits).isZero();
        assertThat(after).as("independent five-table snapshot before cleanup").isEqualTo(before);
        if (point == Point.JDBC_COMMIT_BEFORE_DELEGATE) {
            assertThat(run.completion).isIn(TransactionSynchronization.STATUS_ROLLED_BACK, TransactionSynchronization.STATUS_UNKNOWN);
        } else {
            assertThat(run.successfulRollbacks).isPositive();
            assertThat(run.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        t076AssertError(result, t076Route(operation, false), 500, null, null);
        System.out.printf("T076_ROLLBACK operation=%s point=%s dml=%d flush=%d bodyCompleted=%d commitsDelegated=%d explicitRollbacks=%d completion=%d snapshotEqual=true safe500=true%n",
                operation, point, run.dml.size(), run.flushReturns, run.bodyReturns, run.delegatedCommits, run.successfulRollbacks, run.completion);
    }

    @ParameterizedTest(name = "T076 positive commit {0}")
    @EnumSource(value = Operation.class, names = {"POST", "DELETE"})
    void t076PostAndDeleteCommitControls(Operation operation) throws Exception {
        var before = database.snapshot(); var run = probe.arm(Point.NONE, connection -> { });
        MvcResult result;
        Snapshot after;
        try { result = t076Perform(operation, false, List.of(newCategory)); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }
        t076AssertRealProxy(run); t076AssertCommitted(run);
        assertThat(result.getResponse().getStatus()).isEqualTo(t076Success(operation));
        t076AssertCommittedState(operation, before, after);
        if (operation == Operation.POST) {
            long created = after.products().stream().filter(row -> !before.products().stream().map(ProductRow::id).toList().contains(row.id()))
                    .findFirst().orElseThrow().id();
            var body = mapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(body.path("id").longValue()).isEqualTo(created);
            assertThat(body.path("price").asText()).isEqualTo("12.00");
            assertThat(java.net.URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION)).getPath()).isEqualTo("/admin/products/" + created);
        } else assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        System.out.printf("T076_CONTROL operation=%s status=%d commit=1 independentStateVisible=true historyPreserved=true%n", operation, t076Success(operation));
    }

    @ParameterizedTest(name = "T076 rejection before DML {0}")
    @EnumSource(Operation.class)
    void t076RejectsBeforeDml(Operation operation) throws Exception {
        var before = database.snapshot(); var run = probe.arm(Point.NONE, connection -> { });
        MvcResult result;
        Snapshot after;
        try { result = t076Perform(operation, true, List.of(newCategory)); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }
        assertThat(run.dml).isEmpty(); assertThat(run.failedDml).isEmpty(); assertThat(run.flushReturns).isZero();
        assertThat(run.commitAttempts).isZero(); assertThat(run.successfulCommits).isZero(); assertThat(run.hits).isZero();
        assertThat(after).isEqualTo(before);
        if (operation == Operation.DELETE) {
            t076AssertRealProxy(run); assertThat(run.proxyFailures).isEqualTo(1);
            assertThat(run.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
            t076AssertError(result, t076Route(operation, true), 409, null, null);
        } else {
            assertThat(run.proxyEntries).as("invalid price rejected by the real @Valid boundary before maintenance").isZero();
            t076AssertError(result, t076Route(operation, true), 400, "price", "NOT_POSITIVE");
        }
        System.out.printf("T076_PRE_DML operation=%s status=%d realDml=0 snapshotEqual=true rollbackAfterWriteClaim=false%n", operation, result.getResponse().getStatus());
    }

    @ParameterizedTest(name = "T076 category lost after real product DML {0}")
    @EnumSource(value = Operation.class, names = {"POST", "PUT"})
    void t076ReferenceLostAfterProductDml(Operation operation) throws Exception {
        long lost = fixture.createCategory("T076 temporary selection").id();
        var before = database.snapshot();
        var expected = new Snapshot(before.products(), before.categories().stream().filter(row -> row.id() != lost).toList(),
                before.associations(), before.orders(), before.items());
        var run = probe.arm(Point.NONE, connection -> { });
        run.afterProductDml = connection -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(connection.getAutoCommit()).isFalse();
            // Another committed connection removes an initially valid, unused reference only after real product DML.
            try (var independent = database.openConnection(); var remove = independent.prepareStatement("delete from tb_category where id=?")) {
                assertThat(independent.getAutoCommit()).isTrue(); remove.setLong(1, lost);
                assertThat(remove.executeUpdate()).isEqualTo(1);
            }
            assertThat(database.snapshot()).as("external reference deletion persists; application's uncommitted product does not").isEqualTo(expected);
        };
        MvcResult result;
        Snapshot after;
        try { result = t076Perform(operation, false, List.of(oldCategory, lost)); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }
        assertThat(run.observationFailure).as("reference-loss setup must not masquerade as functional RED").isNull();
        assertThat(run.afterProductDmlCalls).isEqualTo(1); assertThat(run.injected).isFalse();
        t076AssertRealProxy(run);
        assertThat(run.dml).anySatisfy(event -> {
            assertThat(event.sql()).startsWith(operation == Operation.POST ? "insert into tb_product " : "update tb_product ");
            assertThat(event.affectedRows()).isEqualTo(1);
        });
        assertThat(run.failedDml).anySatisfy(event -> {
            assertThat(event.sql()).startsWith("insert into tb_product_category "); assertThat(event.sqlState()).isEqualTo("23506");
        });
        assertThat(run.proxyFailures).isEqualTo(1); assertThat(run.proxyReturns).isZero(); assertThat(run.bodyReturns).isZero();
        assertThat(run.commitAttempts).isZero(); assertThat(run.successfulRollbacks).isPositive();
        assertThat(run.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(after).as("only the independent category deletion remains; no partial application write").isEqualTo(expected);
        t076AssertError(result, t076Route(operation, false), 400, "categoryIds", "CATEGORY_NOT_FOUND");
        System.out.printf("T076_REFERENCE operation=%s afterProductDml=true jdbcState=23506 externalDeletionPreserved=true applicationRollback=true snapshotChecked=true status=400%n", operation);
    }

    @ParameterizedTest(name = "T076 servlet transmission completion failure after confirmed commit {0}")
    @EnumSource(Operation.class)
    void t076TransportFailureAfterConfirmedCommit(Operation operation) throws Exception {
        var before = database.snapshot(); var run = probe.arm(Point.NONE, connection -> { });
        var transmission = new IOException("T076 test-only transmission completion failure");
        var preparationFailure = new Throwable[1]; var committedResponse = new boolean[1]; var status = new int[1];
        Filter transport = (request, response, chain) -> {
            chain.doFilter(request, response);
            try { outsideTransaction(); t076AssertCommitted(run); }
            catch (Throwable failure) { preparationFailure[0] = failure; throw new jakarta.servlet.ServletException("T076 transmission point not reached", failure); }
            var http = (HttpServletResponse) response;
            http.flushBuffer(); committedResponse[0] = http.isCommitted(); status[0] = http.getStatus();
            // Simulate an outbound completion failure outside MVC after its response has been flushed.
            // This does not claim a real network write, a physical database fault or rollback after commit.
            throw transmission;
        };
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(AdminDiagnosticLoggingFilter.class),
                context.getBean(FilterChainProxy.class), transport).build();
        Throwable failure;
        Snapshot after;
        try { failure = catchThrowable(() -> t076Perform(operation, false, List.of(newCategory))); }
        finally { outsideTransaction(); after = database.snapshot(); probe.disarm(); }
        assertThat(preparationFailure[0]).as("outbound instrumentation failure is not a commit proof").isNull();
        assertThat(causeContains(failure, transmission)).isTrue(); assertThat(committedResponse[0]).isTrue();
        assertThat(status[0]).isEqualTo(t076Success(operation)); t076AssertRealProxy(run); t076AssertCommitted(run);
        assertThat(run.proxyFailures).isZero(); assertThat(run.rollbacks).isZero();
        t076AssertCommittedState(operation, before, after);
        System.out.printf("T076_POST_COMMIT operation=%s statusBeforeTransportFailure=%d driverCommit=1 completion=COMMITTED responseFlushed=true transportFailureObserved=true persistedStateRetained=true rollbackClaim=false%n", operation, status[0]);
    }

    private void t076ObserveMutation(Operation operation, Point point, Connection connection, Snapshot before) throws Exception {
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(connection.getAutoCommit()).isFalse();
        if (operation == Operation.POST) {
            long id;
            try (var query = connection.prepareStatement("select id,description,image,price from tb_product where name=?")) {
                query.setString(1, NAME);
                try (var row = query.executeQuery()) {
                    assertThat(row.next()).isTrue(); id = row.getLong("id");
                    assertThat(row.getString("description")).isEqualTo(DESCRIPTION); assertThat(row.getString("image")).isEqualTo(IMAGE);
                    assertThat(row.getBigDecimal("price")).isEqualTo(PRICE); assertThat(row.next()).isFalse();
                }
            }
            if (point != Point.AFTER_DML) {
                try (var query = connection.prepareStatement("select category_id from tb_product_category where product_id=?")) {
                    query.setLong(1, id);
                    try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getLong(1)).isEqualTo(newCategory); assertThat(row.next()).isFalse(); }
                }
            }
        } else {
            try (var query = connection.prepareStatement("select count(*) from tb_product_category where product_id=?")) {
                query.setLong(1, other.id());
                try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getInt(1)).isZero(); }
            }
            try (var query = connection.prepareStatement("select count(*) from tb_product where id=?")) {
                query.setLong(1, other.id());
                try (var row = query.executeQuery()) { assertThat(row.next()).isTrue(); assertThat(row.getInt(1)).isEqualTo(point == Point.AFTER_DML ? 1 : 0); }
            }
        }
    }

    private MvcResult t076Perform(Operation operation, boolean rejected, List<Long> selection) throws Exception {
        outsideTransaction(); String route = t076Route(operation, rejected);
        var request = switch (operation) { case POST -> post(route); case PUT -> put(route); case DELETE -> delete(route); };
        request.servletPath(route).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (operation != Operation.DELETE) {
            var body = mapper.createObjectNode().put("name", NAME).put("description", DESCRIPTION).put("image", IMAGE).put("price", rejected ? "0.00" : "12.00");
            var ids = body.putArray("categoryIds"); selection.forEach(ids::add);
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        }
        try { return mvc.perform(request).andReturn(); }
        finally {
            outsideTransaction();
            database.snapshot().products().stream().filter(row -> row.id() != target.id() && row.id() != other.id())
                    .forEach(row -> ownedCreatedProducts.add(row.id()));
        }
    }

    private String t076Route(Operation operation, boolean rejected) {
        return operation == Operation.POST ? "/admin/products" : "/admin/products/" + (operation == Operation.DELETE && !rejected ? other.id() : target.id());
    }

    private static int t076Success(Operation operation) { return switch (operation) { case POST -> 201; case PUT -> 200; case DELETE -> 204; }; }

    private void t076AssertRealProxy(Run run) {
        assertThat(run.originalServiceAdvised).isTrue(); assertThat(run.proxyEntries).isEqualTo(1); assertThat(run.bodyEntries).isEqualTo(1);
        assertThat(run.outsideAtEntry).isTrue(); assertThat(run.outsideAtExit).isTrue();
        assertThat(run.bodyTransaction).isTrue(); assertThat(run.bodyReadOnly).isFalse();
    }

    private void t076AssertCommitted(Run run) {
        assertThat(run.proxyReturns).isEqualTo(1); assertThat(run.proxyFailures).isZero(); assertThat(run.bodyReturns).isEqualTo(1);
        assertThat(run.flushReturns).isEqualTo(1); assertThat(run.beforeCommits).isEqualTo(1);
        assertThat(run.commitAttempts).isEqualTo(1); assertThat(run.delegatedCommits).isEqualTo(1); assertThat(run.successfulCommits).isEqualTo(1);
        assertThat(run.completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
    }

    private void t076AssertCommittedState(Operation operation, Snapshot before, Snapshot after) {
        var products = new ArrayList<>(before.products()); var pairs = new HashSet<>(before.associations());
        if (operation == Operation.POST) {
            var created = after.products().stream().filter(row -> !before.products().stream().map(ProductRow::id).toList().contains(row.id())).toList();
            assertThat(created).hasSize(1); long id = created.getFirst().id();
            products.add(new ProductRow(id, NAME, DESCRIPTION, IMAGE, PRICE)); pairs.add(new ProductCategory(id, newCategory));
        } else if (operation == Operation.DELETE) {
            products.removeIf(row -> row.id() == other.id()); pairs.removeIf(pair -> pair.productId() == other.id());
        } else {
            products.replaceAll(row -> row.id() == target.id() ? new ProductRow(target.id(), NAME, DESCRIPTION, IMAGE, PRICE) : row);
            pairs.removeIf(pair -> pair.productId() == target.id()); pairs.add(new ProductCategory(target.id(), newCategory));
        }
        products.sort(Comparator.comparingLong(ProductRow::id));
        assertThat(after).as("exact independently expected products/categories/pairs/orders/items after confirmed commit")
                .isEqualTo(new Snapshot(products, before.categories(), pairs, before.orders(), before.items()));
    }

    private void t076AssertError(MvcResult result, String path, int status, String field, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        var body = mapper.readTree(result.getResponse().getContentAsByteArray()); var fields = new HashSet<String>(); body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(body.path("status").intValue()).isEqualTo(status); assertThat(body.path("path").asText()).isEqualTo(path);
        assertThat(body.path("error").asText()).isEqualTo(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(switch (status) {
            case 400 -> "Invalid product maintenance data"; case 409 -> "Product deletion violates referential integrity";
            default -> "Product maintenance could not be completed";
        });
        assertThat(body.path("errors").isArray()).isTrue();
        if (status == 400) {
            boolean found = false;
            for (var error : body.path("errors")) {
                var names = new HashSet<String>(); error.fieldNames().forEachRemaining(names::add);
                assertThat(names).containsExactlyInAnyOrder("field", "code", "message");
                found |= field.equals(error.path("field").asText()) && code.equals(error.path("code").asText());
            }
            assertThat(found).as("expected safe field diagnostic").isTrue();
        } else assertThat(body.path("errors")).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains(token)).as("credential is not echoed").isFalse();
    }

    private void assertEmpty() { assertThat(database.snapshot()).isEqualTo(new Snapshot(List.of(), List.of(), Set.of(), List.of(), List.of())); }

    @AfterEach
    void disarmAndCleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        probe.disarm(); outsideTransaction(); if (fixture == null) return;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var items = connection.prepareStatement("delete from tb_order_item where order_id=?");
                 var order = connection.prepareStatement("delete from tb_order where id=?");
                 var pairs = connection.prepareStatement("delete from tb_product_category where product_id=?");
                 var product = connection.prepareStatement("delete from tb_product where id=?")) {
                for (long id : ownedOrders) { items.setLong(1, id); items.executeUpdate(); order.setLong(1, id); order.executeUpdate(); }
                for (long id : ownedCreatedProducts) { pairs.setLong(1, id); pairs.executeUpdate(); product.setLong(1, id); product.executeUpdate(); }
                // The positive PUT owns a replacement pair that the original fixture did not insert.
                if (target != null) { pairs.setLong(1, target.id()); pairs.executeUpdate(); }
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }
}
