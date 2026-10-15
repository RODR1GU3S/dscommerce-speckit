package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** T056: actual H2 constraints through independent JDBC, without an HTTP deletion or maintenance implementation. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductReferentialIntegrityTest {
    private static final long ABSENT = Long.MAX_VALUE;
    @Autowired private WebApplicationContext context;
    @Autowired private OrderService orders;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AuthenticatedAccountPrincipal buyer;
    private ProductData sold;
    private ProductData other;
    private final Set<Long> ownedOrders = new HashSet<>();
    private boolean extraTable;

    @BeforeEach
    void prepareCommittedPrivateCatalogAndHistoricalOrderThroughTheRealProxy() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(orders)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context);
        ownedOrders.clear(); extraTable = false;
        var account = fixture.prepareBuyer();
        buyer = new AuthenticatedAccountPrincipal(account.id());
        assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
        var a = fixture.createCategory("Categoria igual");
        var b = fixture.createCategory("Categoria igual");
        var unused = fixture.createCategory("Categoria sem produto");
        sold = fixture.createProduct("Produto vendido", "Descricao completa do produto vendido",
                "https://example.invalid/t056-sold.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("Produto independente", "Descricao completa independente",
                "https://example.invalid/t056-other.jpg", new BigDecimal("3.25"));
        fixture.associate(sold.id(), a.id()); fixture.associate(sold.id(), b.id());
        fixture.associate(other.id(), a.id());
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(sold), row(other));
        assertThat(prepared.categories()).containsExactly(new AdminProductDatabaseProbe.CategoryRow(a.id(), a.name()),
                new AdminProductDatabaseProbe.CategoryRow(b.id(), b.name()), new AdminProductDatabaseProbe.CategoryRow(unused.id(), unused.name()));
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(sold.id(), a.id()),
                new ProductCategory(sold.id(), b.id()), new ProductCategory(other.id(), a.id()));
        assertThat(prepared.orders()).isEmpty(); assertThat(prepared.items()).isEmpty();
        assertThat(prepared.products()).noneMatch(product -> product.id() == ABSENT);
        registerOrder(2);
    }

    @Test
    void actualMetadataRequiresProductsAndRestrictsDeletionWithoutCascadeOrSetNull() throws Exception {
        var before = database.snapshot();
        var keys = List.of(foreignKey("TB_ORDER_ITEM", "PRODUCT_ID", "TB_PRODUCT"),
                foreignKey("TB_PRODUCT_CATEGORY", "PRODUCT_ID", "TB_PRODUCT"),
                foreignKey("TB_PRODUCT_CATEGORY", "CATEGORY_ID", "TB_CATEGORY"));
        for (var key : keys) assertRestrictiveRequiredReference(key);
        assertThat(database.snapshot()).isEqualTo(before);
        System.out.printf("T056_METADATA keys=%s independentSnapshotEqual=true%n", keys);
    }

    @ParameterizedTest(name = "sold product referenced by {0} committed orders")
    @ValueSource(ints = {1, 2})
    void directSqlCannotDeleteSoldParentEvenAfterItsCategoryPairsAreRemoved(int orderCount) throws Exception {
        if (orderCount == 2) registerOrder(3);
        var before = database.snapshot();
        assertThat(before.items()).hasSize(orderCount).allSatisfy(item -> assertThat(item.productId()).isEqualTo(sold.id()));
        var key = foreignKey("TB_ORDER_ITEM", "PRODUCT_ID", "TB_PRODUCT");
        assertRestrictiveRequiredReference(key);
        // Removing only owned pairs inside this transaction prevents the join-table FK masking the OrderItem FK.
        rejectParentDeleteAndRestorePairs(sold.id(), 2, key, before);
        assertThat(database.snapshot()).isEqualTo(before);
        assertHistory(database.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"INSERT", "UPDATE"})
    void directSqlCannotCreateAnOrphanOrderItem(String operation) throws Exception {
        var before = database.snapshot();
        var item = before.items().get(0);
        var key = foreignKey("TB_ORDER_ITEM", "PRODUCT_ID", "TB_PRODUCT");
        assertRestrictiveRequiredReference(key);
        SQLException failure;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (operation.equals("INSERT")) {
                    try (var statement = connection.prepareStatement("insert into tb_order_item "
                            + "(order_id, position, product_id, quantity, unit_price) values (?, ?, ?, ?, ?)")) {
                        statement.setQueryTimeout(2);
                        statement.setLong(1, item.orderId()); statement.setInt(2, 1); statement.setLong(3, ABSENT);
                        statement.setInt(4, 1); statement.setBigDecimal(5, new BigDecimal("1.00"));
                        failure = catchThrowableOfType(statement::executeUpdate, SQLException.class);
                    }
                } else {
                    try (var statement = connection.prepareStatement("update tb_order_item set product_id = ? where id = ?")) {
                        statement.setQueryTimeout(2); statement.setLong(1, ABSENT); statement.setLong(2, item.id());
                        failure = catchThrowableOfType(statement::executeUpdate, SQLException.class);
                    }
                }
                assertConstraint(failure, "23506", key);
            } finally {
                connection.rollback(); // End the attempted write; no continuation after its failure.
            }
        }
        outsideTransaction();
        assertThat(database.snapshot()).isEqualTo(before);
        assertNoOrphans(); assertHistory(database.snapshot());
        System.out.printf("T056_ORPHAN operation=%s state=%s code=%d constraint=%s rollbackReturned=true snapshotEqual=true orphans=0%n",
                operation, failure.getSQLState(), failure.getErrorCode(), key.name());
    }

    @Test
    void oneAdditionalTemporaryForeignKeyAlsoRestrictsItsParentAndPreservesChildren() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.execute("create table tb_t056_product_reference (id bigint primary key, product_id bigint not null, "
                    + "note varchar(80) not null, constraint fk_t056_extra_product foreign key(product_id) references tb_product(id))");
            extraTable = true;
            connection.setAutoCommit(false);
            try (var insert = connection.prepareStatement("insert into tb_t056_product_reference (id, product_id, note) values (?, ?, ?)")) {
                insert.setLong(1, 1); insert.setLong(2, other.id()); insert.setString(3, "Filho do alvo adicional"); insert.executeUpdate();
                insert.setLong(1, 2); insert.setLong(2, sold.id()); insert.setString(3, "Filho independente"); insert.executeUpdate();
                connection.commit();
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
        var children = List.of(new ExtraRow(1, other.id(), "Filho do alvo adicional"), new ExtraRow(2, sold.id(), "Filho independente"));
        assertThat(extraRows()).isEqualTo(children);
        var before = database.snapshot();
        assertThat(before.items()).noneMatch(item -> item.productId() == other.id());
        var key = foreignKey("TB_T056_PRODUCT_REFERENCE", "PRODUCT_ID", "TB_PRODUCT");
        assertThat(key.name()).isEqualTo("FK_T056_EXTRA_PRODUCT");
        assertRestrictiveRequiredReference(key);
        rejectParentDeleteAndRestorePairs(other.id(), 1, key, before);
        assertThat(extraRows()).isEqualTo(children); assertThat(database.snapshot()).isEqualTo(before);
        assertHistory(database.snapshot()); assertNoOrphans();
        System.out.printf("T056_EXTRA metadata=%s children=2 exactChildrenPreserved=true independentSnapshotEqual=true%n", key);
    }

    private void rejectParentDeleteAndRestorePairs(long productId, int expectedPairs, ForeignKey key, Snapshot before) throws Exception {
        SQLException failure;
        int removed;
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?")) {
                    pairs.setQueryTimeout(2); pairs.setLong(1, productId); removed = pairs.executeUpdate();
                    assertThat(removed).isEqualTo(expectedPairs);
                }
                try (var count = connection.prepareStatement("select count(*) from tb_product_category where product_id = ?")) {
                    count.setLong(1, productId);
                    try (var rows = count.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero(); }
                }
                // A separate DriverManager connection still sees the full committed snapshot, including these pairs.
                assertThat(database.snapshot()).isEqualTo(before);
                try (var parent = connection.prepareStatement("delete from tb_product where id = ?")) {
                    parent.setQueryTimeout(2); parent.setLong(1, productId);
                    failure = catchThrowableOfType(parent::executeUpdate, SQLException.class);
                }
                assertConstraint(failure, "23503", key);
            } finally {
                connection.rollback();
            }
        }
        outsideTransaction();
        assertThat(database.snapshot()).isEqualTo(before); assertNoOrphans();
        System.out.printf("T056_PARENT childTable=%s constraint=%s state=%s code=%d removedPairs=%d rollbackReturned=true snapshotEqual=true orphans=0%n",
                key.table(), key.name(), failure.getSQLState(), failure.getErrorCode(), removed);
    }

    private ForeignKey foreignKey(String table, String column, String parent) throws Exception {
        try (var connection = database.openConnection()) {
            var found = new ArrayList<ForeignKey>();
            try (var rows = connection.getMetaData().getImportedKeys(null, "PUBLIC", table)) {
                while (rows.next()) if (column.equals(rows.getString("FKCOLUMN_NAME"))) {
                    found.add(new ForeignKey(rows.getString("FK_NAME"), rows.getString("FKTABLE_NAME"), rows.getString("FKCOLUMN_NAME"),
                            rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"), rows.getShort("DELETE_RULE"),
                            rows.getShort("UPDATE_RULE"), rows.getShort("DEFERRABILITY")));
                }
            }
            assertThat(found).hasSize(1);
            var key = found.get(0);
            assertThat(key.table()).isEqualTo(table); assertThat(key.column()).isEqualTo(column);
            assertThat(key.parent()).isEqualTo(parent); assertThat(key.parentColumn()).isEqualTo("ID");
            assertThat(key.name()).isNotBlank();
            return key;
        }
    }

    private void assertRestrictiveRequiredReference(ForeignKey key) throws Exception {
        assertThat(key.deleteRule()).isIn((short) DatabaseMetaData.importedKeyRestrict, (short) DatabaseMetaData.importedKeyNoAction);
        assertThat(key.deferrability()).isEqualTo((short) DatabaseMetaData.importedKeyNotDeferrable);
        try (var connection = database.openConnection(); var rows = connection.getMetaData()
                .getColumns(null, "PUBLIC", key.table(), key.column())) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNoNulls);
            assertThat(rows.next()).isFalse();
        }
    }

    private static void assertConstraint(SQLException failure, String state, ForeignKey key) {
        assertThat((Throwable) failure).as("real JDBC reference constraint failure").isNotNull();
        assertThat(failure.getSQLState()).isEqualTo(state); assertThat(failure.getErrorCode()).isEqualTo(Integer.parseInt(state));
        // Test-only H2 assertion of the exact FK discovered from metadata, not a production error classifier.
        assertThat(failure.getMessage().toUpperCase(Locale.ROOT)).contains(key.name().toUpperCase(Locale.ROOT) + ":");
    }

    private void assertNoOrphans() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(
                "select count(*) from tb_order_item i left join tb_product p on p.id=i.product_id where p.id is null")) {
            assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isZero();
        }
    }

    private void registerOrder(int quantity) {
        outsideTransaction();
        var before = database.snapshot();
        var expected = new BigDecimal("10.00").multiply(BigDecimal.valueOf(quantity));
        long id;
        try {
            var result = orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(sold.id(), quantity))), buyer);
            id = result.id(); assertThat(result.total()).isEqualTo(expected.toPlainString());
        } finally {
            outsideTransaction();
            database.snapshot().orders().stream().filter(order -> before.orders().stream().noneMatch(prior -> prior.id() == order.id()))
                    .forEach(order -> ownedOrders.add(order.id()));
        }
        var after = database.snapshot();
        assertThat(after.orders()).hasSize(before.orders().size() + 1); assertThat(after.items()).hasSize(before.items().size() + 1);
        assertThat(after.orders().stream().filter(order -> order.id() != id).toList()).isEqualTo(before.orders());
        assertThat(after.items().stream().filter(item -> item.orderId() != id).toList()).isEqualTo(before.items());
        var item = after.items().stream().filter(row -> row.orderId() == id).findFirst().orElseThrow();
        assertThat(item.id()).isPositive(); assertThat(item.productId()).isEqualTo(sold.id()); assertThat(item.position()).isZero();
        assertThat(item.quantity()).isEqualTo(quantity); assertThat(item.unitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(expected);
        assertThat(after.products()).isEqualTo(before.products()); assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.associations()).isEqualTo(before.associations()); assertHistory(after);
        System.out.printf("T056_ORDER id=%d quantity=%d unitPrice=10.00 independentTotal=%s proxyReturned=true%n", id, quantity, expected.toPlainString());
    }

    private void assertHistory(Snapshot state) {
        assertThat(state.orders()).isNotEmpty(); assertThat(state.items()).isNotEmpty();
        assertThat(state.orders().stream().map(AdminProductDatabaseProbe.OrderRow::id)).containsExactlyInAnyOrderElementsOf(ownedOrders);
        for (var order : state.orders()) {
            assertThat(order.buyerId()).isEqualTo(buyer.accountId()); assertThat(order.createdAt()).isNotNull();
            assertThat(order.status()).isEqualTo(OrderStatus.WAITING_PAYMENT.name());
        }
    }

    private List<ExtraRow> extraRows() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("select id, product_id, note from tb_t056_product_reference order by id")) {
            var found = new ArrayList<ExtraRow>();
            while (result.next()) found.add(new ExtraRow(result.getLong(1), result.getLong(2), result.getString(3)));
            return List.copyOf(found);
        }
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        outsideTransaction(); if (fixture == null) return;
        try (var connection = database.openConnection()) {
            if (extraTable) try (var statement = connection.createStatement()) { statement.execute("drop table tb_t056_product_reference"); }
            connection.setAutoCommit(false);
            try (var items = connection.prepareStatement("delete from tb_order_item where order_id = ?");
                 var headers = connection.prepareStatement("delete from tb_order where id = ?")) {
                for (long id : ownedOrders) { items.setLong(1, id); items.executeUpdate(); headers.setLong(1, id); headers.executeUpdate(); }
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
    private static ProductRow row(ProductData data) { return new ProductRow(data.id(), data.name(), data.description(), data.image(), data.price()); }
    private record ForeignKey(String name, String table, String column, String parent, String parentColumn,
                              short deleteRule, short updateRule, short deferrability) { }
    private record ExtraRow(long id, long productId, String note) { }
}
