package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.controllers.handlers.GlobalExceptionHandler;
import com.devsuperior.dscommerce.dto.AdminErrorResponseDTO;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.devsuperior.dscommerce.services.exceptions.ProductDeletionIntegrityException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import jakarta.validation.Validation;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** T060 advice after real proxies and real H2 failures; no production DELETE route or acceptance checkpoint. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(ProductDeletionIntegrityClassificationTest.ProbeConfig.class)
class ProductDeletionIntegrityClassificationTest {
    private static final String PRIVATE = "T060_PRIVATE_SQL_TOKEN_PAYLOAD";
    private static final String DELETE = "delete from tb_product where id=?";
    private static final String INSERT = "insert into tb_product_category (product_id,category_id) values (?,?)";
    @Autowired private WebApplicationContext context;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private OrderService orders;
    @Autowired private GlobalExceptionHandler handler;
    @Autowired private CompletionProbe completion;
    @Autowired private ObjectMapper mapper;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AuthenticatedAccountPrincipal admin;
    private ProductData sold;
    private ProductData free;
    private final Set<Long> ownedOrders = new HashSet<>();

    @BeforeEach
    void committedOwnedDataAndRealHistoricalPurchase() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(maintenance)).isTrue();
        assertThat(AopUtils.isAopProxy(completion)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty(); fixture = new AdminProductTestFixture(context); ownedOrders.clear();
        admin = new AuthenticatedAccountPrincipal(fixture.createAccount("T060 Admin", "Disposable-T060", Set.of(UserRole.ADMIN)).id());
        var buyer = new AuthenticatedAccountPrincipal(fixture.prepareBuyer().id());
        var category = fixture.createCategory("Categoria compartilhada");
        fixture.createCategory("Categoria sem produto");
        sold = fixture.createProduct("Produto vendido", "Descricao completa original", "https://example.invalid/t060.jpg", new BigDecimal("10.00"));
        free = fixture.createProduct("Produto livre", "Descricao completa independente", "https://example.invalid/t060-free.jpg", new BigDecimal("20.00"));
        fixture.associate(sold.id(), category.id()); fixture.associate(free.id(), category.id());
        assertThat(database.roles(admin.accountId())).containsExactly(UserRole.ADMIN);
        assertThat(AopUtils.isAopProxy(orders)).isTrue();
        try { assertThat(orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(sold.id(), 2))), buyer).total()).isEqualTo("20.00"); }
        finally { database.snapshot().orders().forEach(order -> ownedOrders.add(order.id())); }
        var snapshot = database.snapshot(); var order = snapshot.orders().getFirst(); var item = snapshot.items().getFirst();
        assertThat(snapshot.products()).hasSize(2); assertThat(snapshot.categories()).hasSize(2); assertThat(snapshot.associations()).hasSize(2);
        assertThat(snapshot.orders()).hasSize(1); assertThat(snapshot.items()).hasSize(1);
        assertThat(order.buyerId()).isEqualTo(buyer.accountId()); assertThat(order.createdAt()).isNotNull(); assertThat(order.status()).isEqualTo("WAITING_PAYMENT");
        assertThat(item.orderId()).isEqualTo(order.id()); assertThat(item.productId()).isEqualTo(sold.id()); assertThat(item.position()).isZero();
        assertThat(item.quantity()).isEqualTo(2); assertThat(item.unitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("20.00"));
    }

    @Test
    void realPositivePrecheckHasSpecificTypeAndSafeConflictAfterProxy() throws Exception {
        var before = database.snapshot();
        var failure = catchThrowable(() -> maintenance.deleteProduct(sold.id(), admin)); outsideTransaction();
        assertThat(failure).isInstanceOf(ProductDeletionIntegrityException.class).hasNoCause();
        assertThat(database.snapshot()).isEqualTo(before);
        assertResponse(handler.handleOrderProcessing((Exception) failure, request("DELETE", "/admin/products/" + sold.id())), 409);
        System.out.println("T060_REAL stage=precheck typed=true snapshotEqual=true");
    }

    @Test
    void actualAdditionalIncomingForeignKeyDuringServiceFlushIsConflictAfterRollback() throws Exception {
        execute("create table t060_extra (id bigint primary key, product_id bigint not null, constraint fk_t060_extra_product foreign key(product_id) references tb_product(id))");
        execute("insert into t060_extra values (1," + free.id() + "),(2," + free.id() + ")");
        var before = database.snapshot();
        var failure = catchThrowable(() -> maintenance.deleteProduct(free.id(), admin)); outsideTransaction();
        assertRealConstraint(failure, "23503", "FK_T060_EXTRA_PRODUCT");
        assertThat(database.snapshot()).isEqualTo(before);
        assertResponse(handler.handleOrderProcessing((Exception) failure, request("DELETE", "/admin/products/" + free.id())), 409);
        try (var connection = database.openConnection(); var s = connection.createStatement(); var rows = s.executeQuery("select id,product_id from t060_extra order by id")) {
            for (int i = 1; i <= 2; i++) { assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isEqualTo(i); assertThat(rows.getLong(2)).isEqualTo(free.id()); }
            assertThat(rows.next()).isFalse();
        }
        System.out.println("T060_REAL stage=serviceFlush fk=FK_T060_EXTRA_PRODUCT state=23503 snapshotEqual=true extraRows=2");
    }

    @Test
    void orderItemForeignKeyRaisedOnlyAtTransactionCompletionIsClassifiedAfterProxy() throws Exception {
        var before = database.snapshot();
        var failure = catchThrowable(() -> completion.deleteWithoutExplicitFlush(sold.id())); outsideTransaction();
        assertThat(completion.bodyReturned()).isTrue();
        assertThat(completion.completionStatus()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertRealConstraint(failure, "23503", "FK_ORDER_ITEM_PRODUCT");
        assertThat(database.snapshot()).isEqualTo(before);
        assertResponse(handler.handleOrderProcessing((Exception) failure, request("DELETE", "/admin/products/" + sold.id())), 409);
        System.out.println("T060_REAL stage=completionFlush bodyReturned=true completion=ROLLED_BACK fk=FK_ORDER_ITEM_PRODUCT state=23503 snapshotEqual=true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void missingCategoryAtCompletionIsOnlyTheProvenCategoryDiagnosis(String method) throws Exception {
        var before = database.snapshot();
        assertThat(before.categories()).noneMatch(row -> row.id() == Long.MAX_VALUE);
        var failure = catchThrowable(() -> completion.associateMissingCategoryWithoutFlush(free.id())); outsideTransaction();
        System.out.printf("T060_PREPARATION categoryCompletion bodyReturned=%s failure=%s%n", completion.bodyReturned(), failure == null ? "none" : failure.getClass().getSimpleName());
        assertThat(completion.bodyReturned()).isTrue();
        assertThat(completion.completionStatus()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertRealConstraint(failure, "23506", "FK_PRODUCT_CATEGORY_CATEGORY");
        assertThat(database.snapshot()).isEqualTo(before);
        assertResponse(handler.handleOrderProcessing((Exception) failure, request(method,
                method.equals("POST") ? "/admin/products" : "/admin/products/" + free.id())), 400);
        System.out.printf("T060_REAL stage=completionFlush method=%s bodyReturned=true completion=ROLLED_BACK fk=FK_PRODUCT_CATEGORY_CATEGORY state=23506 snapshotEqual=true%n", method);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring", "jpa", "rollback"})
    void knownTransactionWrappersKeepTheConfirmedCause(String wrapper) throws Exception {
        Exception failure = switch (wrapper) {
            case "spring" -> new TransactionSystemException(PRIVATE, validFailure());
            case "jpa" -> new PersistenceException(PRIVATE, validFailure());
            default -> new TransactionSystemException(PRIVATE, new RollbackException(PRIVATE, validFailure()));
        };
        assertResponse(handler.handleOrderProcessing(failure, request("DELETE", "/admin/products/7")), 409);
    }

    @ParameterizedTest(name = "internal: {0}")
    @MethodSource("unclassified")
    void uncertainFailuresNeverBecomeBusinessErrors(Control control) throws Exception {
        var before = database.snapshot();
        assertResponse(handler.handleOrderProcessing(control.failure(), request(control.method(), control.path())), 500);
        assertThat(database.snapshot()).isEqualTo(before);
    }

    static Stream<Control> unclassified() {
        var cycle = new PersistenceException(PRIVATE);
        var linked = new DataIntegrityViolationException(PRIVATE, cycle); cycle.initCause(linked);
        return Stream.of(
                control("CHECK", constraint("23513", 23513, "FK_ORDER_ITEM_PRODUCT", DELETE, false)),
                control("unique", constraint("23505", 23505, "FK_ORDER_ITEM_PRODUCT", DELETE, false)),
                control("NOT NULL", constraint("23502", 23502, "FK_ORDER_ITEM_PRODUCT", DELETE, false)),
                control("wrong code", constraint("23503", 23506, "FK_ORDER_ITEM_PRODUCT", DELETE, false)),
                control("missing parent is not delete conflict", constraint("23506", 23506, "FK_ORDER_ITEM_PRODUCT", DELETE, false)),
                control("unknown FK", constraint("23503", 23503, "UNKNOWN_FK", DELETE, false)),
                control("category FK is not incoming to Product", constraint("23503", 23503, "FK_PRODUCT_CATEGORY_CATEGORY", DELETE, false)),
                control("wrong table", constraint("23503", 23503, "FK_ORDER_ITEM_PRODUCT", "delete from tb_order where id=?", false)),
                control("missing SQL", constraint("23503", 23503, "FK_ORDER_ITEM_PRODUCT", null, false)),
                control("arbitrary text before vendor identity", constraint("23503", 23503, "FK_ORDER_ITEM_PRODUCT", DELETE, true)),
                control("partial FK identity", constraint("23503", 23503, "K_ORDER_ITEM_PRODUCT", DELETE, false)),
                control("raw SQLException alone", new DataIntegrityViolationException(PRIVATE, new SQLException(PRIVATE + " FK_ORDER_ITEM_PRODUCT", "23503", 23503))),
                control("unknown wrapper", new IllegalStateException(PRIVATE, validFailure())),
                control("timeout", new org.springframework.dao.QueryTimeoutException(PRIVATE, validFailure())),
                control("deadlock", new CannotAcquireLockException(PRIVATE, validFailure())),
                control("validation wrapper", new jakarta.validation.ValidationException(PRIVATE, validFailure())),
                control("cycle", linked),
                new Control("wrong method", "GET", "/admin/products/7", validFailure()),
                new Control("wrong route", "DELETE", "/admin/categories/7", validFailure()),
                new Control("wrong DELETE collection", "DELETE", "/admin/products", validFailure()),
                new Control("wrong write FK", "PUT", "/admin/products/7", constraint("23506", 23506, "FK_PRODUCT_CATEGORY_PRODUCT", INSERT, false)),
                new Control("wrong category write SQL", "PUT", "/admin/products/7", constraint("23506", 23506, "FK_PRODUCT_CATEGORY_CATEGORY", DELETE, false)),
                new Control("precheck wrong operation", "PUT", "/admin/products/7", new ProductDeletionIntegrityException()));
    }

    @Test
    void missingMetadataFailsClosedAndPublicHandlingStaysOutsideAdmin() throws Exception {
        assertResponse(new GlobalExceptionHandler().handleOrderProcessing(validFailure(), request("DELETE", "/admin/products/7")), 500);
        var failure = validFailure();
        assertThatThrownBy(() -> handler.handleOrderProcessing(failure, request("DELETE", "/products/7"))).isSameAs(failure);
    }

    @Test
    void entityBeanValidationIsNotHibernateJdbcReferentialIntegrity() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new Product(null, "", "", "", BigDecimal.ZERO));
            assertThat(violations).isNotEmpty();
            assertResponse(handler.handleConstraintViolation(new jakarta.validation.ConstraintViolationException(violations), request("DELETE", "/admin/products/7")), 500);
        }
    }

    private void assertResponse(ResponseEntity<?> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getLocation()).isNull();
        var body = (AdminErrorResponseDTO) response.getBody(); assertThat(body).isNotNull(); assertThat(body.status()).isEqualTo(status);
        assertThat(body.path()).startsWith("/api/admin/");
        assertThat(body.message()).isEqualTo(status == 409 ? "Product deletion violates referential integrity"
                : status == 400 ? "Invalid product maintenance data" : "Product maintenance could not be completed");
        if (status == 400) assertThat(body.errors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("categoryIds"); assertThat(error.code()).isEqualTo("CATEGORY_NOT_FOUND");
            assertThat(error.message()).isEqualTo("Selected category does not exist");
        }); else assertThat(body.errors()).isEmpty();
        var json = mapper.valueToTree(body); var fields = new HashSet<String>(); json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(mapper.writeValueAsString(body)).doesNotContain(PRIVATE, "FK_", "23503", "23506", "SQLException", "delete from", "insert into");
    }
    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, "/api" + path); request.setContextPath("/api"); return request;
    }
    private static Control control(String label, Exception failure) { return new Control(label, "DELETE", "/admin/products/7", failure); }
    private static Exception validFailure() { return constraint("23503", 23503, "FK_ORDER_ITEM_PRODUCT", DELETE, false); }
    private static Exception constraint(String state, int code, String fk, String sql, boolean misleading) {
        var jdbc = new SQLException((misleading ? PRIVATE : "") + "Referential integrity constraint violation: \"" + fk
                + ": PUBLIC.TB_ORDER_ITEM FOREIGN KEY(PRODUCT_ID) REFERENCES PUBLIC.TB_PRODUCT(ID) (99)\"; SQL statement:\n" + PRIVATE, state, code);
        return new DataIntegrityViolationException(PRIVATE, new ConstraintViolationException(PRIVATE, jdbc, sql, fk));
    }
    private static void assertRealConstraint(Throwable failure, String state, String name) {
        assertThat(failure).isNotNull(); Throwable cause = failure;
        while (!(cause instanceof ConstraintViolationException) && cause.getCause() != null) cause = cause.getCause();
        assertThat(cause).isInstanceOf(ConstraintViolationException.class);
        var violation = (ConstraintViolationException) cause;
        assertThat(violation.getSQLState()).isEqualTo(state); assertThat(violation.getSQLException().getMessage()).contains(name);
        System.out.printf("T060_CAUSE wrapper=%s state=%s hibernateName=%s sql=%s%n", failure.getClass().getSimpleName(), state, violation.getConstraintName(), violation.getSQL());
    }
    private void execute(String sql) throws SQLException { try (var c = database.openConnection(); var s = c.createStatement()) { s.setQueryTimeout(2); s.execute(sql); } }
    @AfterEach
    void cleanupOwnedDataAfterAllExternalAssertions() throws Exception {
        outsideTransaction(); if (fixture == null) return; execute("drop table if exists t060_extra");
        for (long id : ownedOrders) { execute("delete from tb_order_item where order_id=" + id); execute("delete from tb_order where id=" + id); }
        fixture.close(); assertEmpty();
    }
    private void assertEmpty() { var s = database.snapshot(); assertThat(s.products()).isEmpty(); assertThat(s.categories()).isEmpty();
        assertThat(s.associations()).isEmpty(); assertThat(s.orders()).isEmpty(); assertThat(s.items()).isEmpty(); assertThat(database.accountIds()).isEmpty(); }
    private static void outsideTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse(); }
    record Control(String label, String method, String path, Exception failure) { @Override public String toString() { return label; } }

    /** Only to reach the real automatic flush in transaction completion, without changing production hooks. */
    public static class CompletionProbe {
        @PersistenceContext private EntityManager entityManager;
        @Autowired private ProductRepository products;
        boolean bodyReturned; int status;
        public boolean bodyReturned() { return bodyReturned; }
        public int completionStatus() { return status; }
        private void start() { bodyReturned = false; status = -1; TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int completionStatus) { status = completionStatus; }
        }); }
        @Transactional public void deleteWithoutExplicitFlush(long id) { start(); products.delete(products.findAdminByIdForUpdate(id).orElseThrow()); bodyReturned = true; }
        @Transactional public void associateMissingCategoryWithoutFlush(long id) {
            // A proxy's hashCode can initialize the missing entity while adding to the Set.
            // This detached test reference reaches the real join-table FK only at automatic flush.
            start(); products.findAdminByIdForUpdate(id).orElseThrow().getCategories().add(new Category(Long.MAX_VALUE, "Categoria ausente")); bodyReturned = true;
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfig { @Bean CompletionProbe t060CompletionProbe() { return new CompletionProbe(); } }
}
