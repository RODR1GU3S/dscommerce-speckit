package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** T057: repository existence only, with committed fixtures and independent state verification. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OrderItemRepositoryTest.SqlConfig.class)
class OrderItemRepositoryTest {
    @Autowired private WebApplicationContext context;
    @Autowired private OrderItemRepository items;
    @Autowired private OrderService orders;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AuthenticatedAccountPrincipal buyer;
    private ProductData target;
    private ProductData other;
    private final Set<Long> ownedOrders = new HashSet<>();

    @BeforeEach
    void prepareIsolatedCommittedDataWithoutAnOuterTransaction() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(items)).isTrue(); assertThat(AopUtils.isAopProxy(orders)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context); ownedOrders.clear();
        var client = fixture.prepareBuyer(); buyer = new AuthenticatedAccountPrincipal(client.id());
        assertThat(database.roles(client.id())).containsExactly(UserRole.CLIENT);
        var a = fixture.createCategory("Categoria igual"); var b = fixture.createCategory("Categoria igual");
        target = fixture.createProduct("Alvo T057", "Descricao completa do alvo", "https://example.invalid/t057-target.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Outro T057", "Descricao completa independente", "https://example.invalid/t057-other.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), a.id()); fixture.associate(target.id(), b.id()); fixture.associate(other.id(), a.id());
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).containsExactly(new AdminProductDatabaseProbe.CategoryRow(a.id(), a.name()),
                new AdminProductDatabaseProbe.CategoryRow(b.id(), b.name()));
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(target.id(), a.id()),
                new ProductCategory(target.id(), b.id()), new ProductCategory(other.id(), a.id()));
        assertThat(prepared.orders()).isEmpty(); assertThat(prepared.items()).isEmpty();
        assertThat(database.accountIds()).containsExactly(client.id());
        assertThat(prepared.products()).noneMatch(product -> product.id() == Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty-existing", "missing", "other-only", "one", "multiple"})
    void detectsAnyItemForExactlyTheRequestedProductWithoutChangingItsHistory(String kind) {
        long productId = kind.equals("missing") ? Long.MAX_VALUE : target.id();
        boolean expected = kind.equals("one") || kind.equals("multiple");
        if (kind.equals("missing") || kind.equals("other-only")) {
            register(List.of(new Line(other.id(), 2, new BigDecimal("3.25"))), "6.50");
        }
        if (expected) {
            // Target is not the first item; a reference anywhere in the order is sufficient.
            register(List.of(new Line(other.id(), 3, new BigDecimal("3.25")),
                    new Line(target.id(), 2, new BigDecimal("10.00"))), "29.75");
            if (kind.equals("multiple")) register(List.of(new Line(target.id(), 3, new BigDecimal("10.00"))), "30.00");
        }
        var before = database.snapshot();
        assertThat(before.items().stream().filter(item -> item.productId() == productId).count())
                .isEqualTo(kind.equals("multiple") ? 2L : expected ? 1L : 0L);
        var accounts = database.accountIds(); sql.clear(); outsideTransaction();
        boolean actual = items.existsByProductId(productId);
        outsideTransaction();
        var after = database.snapshot();
        assertThat(after).isEqualTo(before); assertThat(database.accountIds()).isEqualTo(accounts);
        // Captures ORM statements, not direct JDBC/binds; the independent snapshot is a separate oracle.
        assertThat(sql.events()).allSatisfy(event -> {
            assertThat(event.transactionActive()).isTrue();
            assertThat(event.sql().replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(Locale.ROOT))
                    .startsWith("select ").doesNotContain("status");
        });
        System.out.printf("T057_EXISTS case=%s expected=%s actual=%s queryStatements=%d snapshotEqual=true%n", kind, expected, actual, sql.events().size());
        sql.events().forEach(event -> System.out.printf("T057_SQL case=%s transactionActive=%s statement=%s%n", kind, event.transactionActive(), event.sql()));
        assertThat(actual).as("existence of any OrderItem for the requested product").isEqualTo(expected);
    }

    private void register(List<Line> lines, String total) {
        outsideTransaction(); var before = database.snapshot(); long id;
        try {
            var response = orders.register(new OrderRequestDTO(lines.stream()
                    .map(line -> new OrderItemRequestDTO(line.product(), line.quantity())).toList()), buyer);
            id = response.id(); assertThat(response.total()).isEqualTo(total);
        } finally {
            outsideTransaction();
            database.snapshot().orders().stream().filter(order -> before.orders().stream().noneMatch(prior -> prior.id() == order.id()))
                    .forEach(order -> ownedOrders.add(order.id()));
        }
        var after = database.snapshot();
        assertThat(after.orders()).hasSize(before.orders().size() + 1); assertThat(after.items()).hasSize(before.items().size() + lines.size());
        assertThat(after.orders().stream().filter(order -> order.id() != id).toList()).isEqualTo(before.orders());
        assertThat(after.items().stream().filter(item -> item.orderId() != id).toList()).isEqualTo(before.items());
        var order = after.orders().stream().filter(row -> row.id() == id).findFirst().orElseThrow();
        assertThat(order.buyerId()).isEqualTo(buyer.accountId()); assertThat(order.createdAt()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.WAITING_PAYMENT.name());
        var stored = after.items().stream().filter(item -> item.orderId() == id).toList();
        BigDecimal calculated = new BigDecimal("0.00");
        for (int position = 0; position < lines.size(); position++) {
            var item = stored.get(position); var line = lines.get(position);
            assertThat(item.id()).isPositive(); assertThat(item.productId()).isEqualTo(line.product());
            assertThat(item.position()).isEqualTo(position); assertThat(item.quantity()).isEqualTo(line.quantity());
            assertThat(item.unitPrice()).isEqualTo(line.price());
            calculated = calculated.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        assertThat(calculated).isEqualTo(new BigDecimal(total));
        assertThat(after.products()).isEqualTo(before.products()); assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.associations()).isEqualTo(before.associations());
        System.out.printf("T057_ORDER id=%d items=%d independentTotal=%s proxyReturned=true%n", id, lines.size(), calculated.toPlainString());
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        outsideTransaction(); if (fixture == null) return;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var itemRows = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var orderRows = connection.prepareStatement("delete from tb_order where id = ?")) {
                for (long id : ownedOrders) { itemRows.setLong(1, id); itemRows.executeUpdate(); orderRows.setLong(1, id); orderRows.executeUpdate(); }
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }
    private void assertEmpty() {
        var state = database.snapshot(); assertThat(state.products()).isEmpty(); assertThat(state.categories()).isEmpty();
        assertThat(state.associations()).isEmpty(); assertThat(state.orders()).isEmpty(); assertThat(state.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private static AdminProductDatabaseProbe.ProductRow row(ProductData data) {
        return new AdminProductDatabaseProbe.ProductRow(data.id(), data.name(), data.description(), data.image(), data.price());
    }
    private record Line(long product, int quantity, BigDecimal price) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation t057SqlObservation() { return new AdminProductDatabaseProbe.SqlObservation(); }
        @Bean HibernatePropertiesCustomizer t057SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
