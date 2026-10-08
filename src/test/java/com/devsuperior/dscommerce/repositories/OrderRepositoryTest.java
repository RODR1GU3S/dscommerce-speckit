package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderItem;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Schema gate precedes fixtures/JPA; JDBC rejection tests exercise SQL, not ORM validation. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@Import(OrderTestConfig.class)
class OrderRepositoryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-07T12:00:00.123Z");
    private final Set<Long> ownedAccounts = new LinkedHashSet<>();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrderTestFixture fixture;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager entityManager;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @AfterEach
    void cleanOnlyOwnedRowsIfTheOrderTablesExist() {
        // The mapping transaction has already ended; cleanup never requires absent tables.
        if (!ownedAccounts.isEmpty() && publicTables().contains("TB_ORDER")) {
            for (var accountId : ownedAccounts) {
                if (publicTables().contains("TB_ORDER_ITEM")) {
                    jdbc.update("delete from tb_order_item where order_id in "
                            + "(select id from tb_order where buyer_id = ?)", accountId);
                }
                jdbc.update("delete from tb_order where buyer_id = ?", accountId);
            }
        }
        fixture.cleanUp();
        ownedAccounts.clear();
    }

    @ParameterizedTest(name = "planned table and metadata: {0}")
    @ValueSource(strings = {"TB_ORDER", "TB_ORDER_ITEM"})
    void shouldExposeThePlannedTableAndItsMetadata(String table) {
        requireTable(table);
        if (table.equals("TB_ORDER")) {
            verifyOrderMetadata();
        } else {
            verifyItemMetadata();
        }
    }

    @Test
    void shouldRoundTripTheCompleteAggregateThroughEntityManagerAndJdbc() {
        requireSchema();
        var data = existingData();
        // Test transaction verifies flush/mapping only; it ends before fixture cleanup.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            var first = new OrderItem(entityManager.getReference(Product.class, data.firstProduct()),
                    2, new BigDecimal("10.00"));
            var second = new OrderItem(entityManager.getReference(Product.class, data.secondProduct()),
                    3, new BigDecimal("7.50"));
            var order = new Order(entityManager.getReference(UserAccount.class, data.buyer()), CREATED_AT,
                    List.of(first, second));
            entityManager.persist(order);
            entityManager.flush();
            var identifier = entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(order);
            assertThat(identifier).isInstanceOf(Long.class);
            var orderId = (Long) identifier;
            assertThat(orderId).isPositive();
            entityManager.clear();

            var reloaded = entityManager.find(Order.class, orderId);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getBuyer().getId()).isEqualTo(data.buyer());
            assertThat(reloaded.getCreatedAt()).isEqualTo(CREATED_AT);
            assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
            assertThat(reloaded.getItems()).hasSize(2);
            assertThat(reloaded.getItems()).extracting(OrderItem::getPosition).containsExactly(0, 1);
            assertThat(reloaded.getItems()).extracting(item -> item.getProduct().getId())
                    .containsExactly(data.firstProduct(), data.secondProduct());
            assertThat(reloaded.getItems()).extracting(OrderItem::getOrder).containsOnly(reloaded);
            assertThat(reloaded.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10.00");
            assertThat(reloaded.getItems().get(1).getUnitPrice()).isEqualByComparingTo("7.50");
            assertThat(reloaded.getTotal()).isEqualByComparingTo("42.50");
            assertThat(jdbc.queryForObject("select buyer_id from tb_order where id = ?", Long.class, orderId))
                    .isEqualTo(data.buyer());
            Instant storedTime = jdbc.queryForObject("select created_at from tb_order where id = ?",
                    (rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(), orderId);
            assertThat(storedTime).isEqualTo(CREATED_AT);
            assertThat(jdbc.queryForObject("select status from tb_order where id = ?", String.class, orderId))
                    .isEqualTo("WAITING_PAYMENT");
            assertThat(itemRows(orderId)).containsExactly(
                    new ItemRow(orderId, data.firstProduct(), 0, 2, new BigDecimal("10.00")),
                    new ItemRow(orderId, data.secondProduct(), 1, 3, new BigDecimal("7.50")));
            assertThat(jdbc.queryForObject("select count(distinct id) from tb_order_item where order_id = ?",
                    Integer.class, orderId)).isEqualTo(2);
        });
    }

    @Test
    void shouldGeneratePositiveDistinctIdentityKeysForOrdersAndItems() {
        requireSchema();
        var data = sqlFixture();
        var firstItem = insertRow("tb_order_item", itemValues(data.firstOrder(), data.data().firstProduct()));
        var secondItem = insertRow("tb_order_item", itemValues(data.secondOrder(), data.data().firstProduct()));

        assertThat(data.firstOrder()).isPositive().isNotEqualTo(data.secondOrder());
        assertThat(data.secondOrder()).isPositive();
        assertThat(firstItem).isPositive().isNotEqualTo(secondItem);
        assertThat(secondItem).isPositive();
    }

    @ParameterizedTest(name = "SQL NOT NULL: {0}.{1}")
    @MethodSource("requiredColumns")
    void shouldRejectNullRequiredValuesByJdbc(String table, String column) {
        requireSchema();
        var data = sqlFixture();
        var values = table.equals("tb_order") ? orderValues(data.data().buyer())
                : itemValues(data.firstOrder(), data.data().firstProduct());
        values.put(column, null);

        assertSqlConstraint("23502", null, () -> insertRow(table, values));
    }

    @ParameterizedTest(name = "SQL foreign key: {0}.{1}")
    @MethodSource("foreignKeyColumns")
    void shouldRejectUnknownLinkedRowsByJdbc(String table, String column) {
        requireSchema();
        var data = sqlFixture();
        var values = table.equals("tb_order") ? orderValues(data.data().buyer())
                : itemValues(data.firstOrder(), data.data().firstProduct());
        values.put(column, Long.MAX_VALUE);

        assertSqlConstraint("23506", null, () -> insertRow(table, values));
    }

    @Test
    void shouldRejectOnlyDuplicateProductPairsWithinTheSameOrder() {
        requireSchema();
        var data = sqlFixture();
        insertRow("tb_order_item", itemValues(data.firstOrder(), data.data().firstProduct()));
        insertRow("tb_order_item", itemValues(data.secondOrder(), data.data().firstProduct()));
        var duplicate = itemValues(data.firstOrder(), data.data().firstProduct());
        duplicate.put("position", 1);

        assertSqlConstraint("23505", null, () -> insertRow("tb_order_item", duplicate));
        var allowed = itemValues(data.firstOrder(), data.data().secondProduct());
        allowed.put("position", 1);
        insertRow("tb_order_item", allowed);
        assertThat(itemRows(data.firstOrder())).hasSize(2);
        assertThat(itemRows(data.secondOrder())).hasSize(1);
    }

    @Test
    void shouldRejectOnlyDuplicatePositionPairsWithinTheSameOrder() {
        requireSchema();
        var data = sqlFixture();
        insertRow("tb_order_item", itemValues(data.firstOrder(), data.data().firstProduct()));
        insertRow("tb_order_item", itemValues(data.secondOrder(), data.data().secondProduct()));
        var duplicate = itemValues(data.firstOrder(), data.data().secondProduct());

        assertSqlConstraint("23505", null, () -> insertRow("tb_order_item", duplicate));
        duplicate.put("position", 1);
        insertRow("tb_order_item", duplicate);
        assertThat(itemRows(data.firstOrder())).hasSize(2);
        assertThat(itemRows(data.secondOrder())).hasSize(1);
    }

    @ParameterizedTest(name = "SQL named CHECK: {2}, invalid {0}={1}")
    @MethodSource("invalidCheckValues")
    void shouldRejectNonPositiveOrNegativeValuesByTheNamedSqlCheck(String column, Object value, String checkName) {
        requireSchema();
        var data = sqlFixture();
        var values = itemValues(data.firstOrder(), data.data().firstProduct());
        values.put(column, value);

        assertSqlConstraint("23513", checkName, () -> insertRow("tb_order_item", values));
    }

    @Test
    void shouldAcceptZeroPositionPositiveBoundariesAndDecimalCapacityByJdbc() {
        requireSchema();
        var data = sqlFixture();
        var minimumPrice = itemValues(data.firstOrder(), data.data().firstProduct());
        minimumPrice.put("quantity", Integer.MAX_VALUE);
        minimumPrice.put("unit_price", new BigDecimal("0.01"));
        insertRow("tb_order_item", minimumPrice);
        var maximumPrice = itemValues(data.firstOrder(), data.data().secondProduct());
        maximumPrice.put("position", 1);
        maximumPrice.put("unit_price", new BigDecimal("99999999999999999.99"));
        insertRow("tb_order_item", maximumPrice);

        assertThat(itemRows(data.firstOrder())).containsExactly(
                new ItemRow(data.firstOrder(), data.data().firstProduct(), 0, Integer.MAX_VALUE, new BigDecimal("0.01")),
                new ItemRow(data.firstOrder(), data.data().secondProduct(), 1, 1,
                        new BigDecimal("99999999999999999.99")));
    }

    private List<String> publicTables() {
        return jdbc.queryForList("select table_name from information_schema.tables "
                + "where table_schema = 'PUBLIC' order by table_name", String.class);
    }

    private void requireTable(String table) {
        assertThat(publicTables()).as("T018 must map the missing table PUBLIC.%s before metadata/fixtures", table)
                .contains(table);
    }

    private void requireSchema() {
        assertThat(publicTables()).as("T018 must map PUBLIC.TB_ORDER and PUBLIC.TB_ORDER_ITEM before fixtures/JPA")
                .contains("TB_ORDER", "TB_ORDER_ITEM");
        verifyOrderMetadata();
        verifyItemMetadata();
    }

    private void verifyOrderMetadata() {
        var columns = columns("TB_ORDER");
        assertThat(columns.keySet()).containsExactlyInAnyOrder("ID", "BUYER_ID", "CREATED_AT", "STATUS");
        verifyIdentityAndNotNull("TB_ORDER", columns);
        assertThat(columns.get("BUYER_ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("CREATED_AT").jdbcType()).isIn(Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE);
        var status = columns.get("STATUS");
        assertThat(status.jdbcType() == Types.VARCHAR || status.typeName().toUpperCase(Locale.ROOT).startsWith("ENUM"))
                .as("WAITING_PAYMENT is a string enum, not an ordinal").isTrue();
        assertThat(foreignKeys("TB_ORDER")).contains("BUYER_ID->TB_USER_ACCOUNT.ID");
    }

    private void verifyItemMetadata() {
        var columns = columns("TB_ORDER_ITEM");
        assertThat(columns.keySet()).containsExactlyInAnyOrder("ID", "ORDER_ID", "PRODUCT_ID", "POSITION",
                "QUANTITY", "UNIT_PRICE");
        verifyIdentityAndNotNull("TB_ORDER_ITEM", columns);
        assertThat(columns.get("ORDER_ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("PRODUCT_ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("POSITION").jdbcType()).isEqualTo(Types.INTEGER);
        assertThat(columns.get("QUANTITY").jdbcType()).isEqualTo(Types.INTEGER);
        var price = columns.get("UNIT_PRICE");
        assertThat(price.jdbcType()).isIn(Types.DECIMAL, Types.NUMERIC);
        assertThat(price.size()).isEqualTo(19);
        assertThat(price.scale()).isEqualTo(2);
        assertThat(foreignKeys("TB_ORDER_ITEM")).contains("ORDER_ID->TB_ORDER.ID", "PRODUCT_ID->TB_PRODUCT.ID");
        assertThat(uniqueIndexes("TB_ORDER_ITEM")).containsValue(Set.of("ORDER_ID", "PRODUCT_ID"))
                .containsValue(Set.of("ORDER_ID", "POSITION"));
        var checks = namedItemChecks();
        assertThat(checks).containsEntry("ck_order_item_quantity_positive", "quantity>0")
                .containsEntry("ck_order_item_position_nonnegative", "position>=0")
                .containsEntry("ck_order_item_unit_price_positive", "unit_price>0");
    }

    private Map<String, Column> columns(String table) {
        return jdbc.execute((ConnectionCallback<Map<String, Column>>) connection -> {
            var columns = new LinkedHashMap<String, Column>();
            try (var rows = connection.getMetaData().getColumns(null, "PUBLIC", table, null)) {
                while (rows.next()) {
                    columns.put(rows.getString("COLUMN_NAME"), new Column(rows.getInt("DATA_TYPE"),
                            rows.getString("TYPE_NAME"), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"),
                            rows.getInt("NULLABLE"), "YES".equals(rows.getString("IS_AUTOINCREMENT"))));
                }
            }
            return columns;
        });
    }

    private void verifyIdentityAndNotNull(String table, Map<String, Column> columns) {
        assertThat(columns.values()).allSatisfy(column ->
                assertThat(column.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls));
        assertThat(columns.get("ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("ID").autoIncrement()).isTrue();
        var primaryKey = jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            var result = new LinkedHashSet<String>();
            try (var rows = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", table)) {
                while (rows.next()) result.add(rows.getString("COLUMN_NAME"));
            }
            return result;
        });
        assertThat(primaryKey).containsExactly("ID");
    }

    private List<String> foreignKeys(String table) {
        return jdbc.execute((ConnectionCallback<List<String>>) connection -> {
            var result = new ArrayList<String>();
            try (var rows = connection.getMetaData().getImportedKeys(null, "PUBLIC", table)) {
                while (rows.next()) {
                    result.add(rows.getString("FKCOLUMN_NAME") + "->" + rows.getString("PKTABLE_NAME")
                            + "." + rows.getString("PKCOLUMN_NAME"));
                }
            }
            return result;
        });
    }

    private Map<String, Set<String>> uniqueIndexes(String table) {
        return jdbc.execute((ConnectionCallback<Map<String, Set<String>>>) connection -> {
            var result = new LinkedHashMap<String, Set<String>>();
            try (var rows = connection.getMetaData().getIndexInfo(null, "PUBLIC", table, true, false)) {
                while (rows.next()) {
                    var name = rows.getString("INDEX_NAME");
                    var column = rows.getString("COLUMN_NAME");
                    if (name != null && column != null) {
                        result.computeIfAbsent(name, ignored -> new LinkedHashSet<>()).add(column);
                    }
                }
            }
            return result;
        });
    }

    private Map<String, String> namedItemChecks() {
        return jdbc.query("select tc.constraint_name, cc.check_clause from information_schema.table_constraints tc "
                        + "join information_schema.check_constraints cc on tc.constraint_catalog = cc.constraint_catalog "
                        + "and tc.constraint_schema = cc.constraint_schema and tc.constraint_name = cc.constraint_name "
                        + "where tc.table_schema = 'PUBLIC' and tc.table_name = 'TB_ORDER_ITEM' "
                        + "and tc.constraint_type = 'CHECK'",
                rs -> {
                    var result = new LinkedHashMap<String, String>();
                    while (rs.next()) {
                        result.put(rs.getString(1).toLowerCase(Locale.ROOT),
                                rs.getString(2).toLowerCase(Locale.ROOT).replaceAll("[\\s\"()]", "")
                                        // H2 renders the integer zero against DECIMAL as CAST(0 AS NUMERIC(1)).
                                        .replace("cast0asnumeric1", "0"));
                    }
                    return result;
                });
    }

    private ExistingData existingData() {
        var buyer = fixture.createAccount("order-schema-" + UUID.randomUUID(), "schema-test-password", Set.of(UserRole.CLIENT));
        ownedAccounts.add(buyer);
        var second = fixture.createProduct("schema-second", new BigDecimal("7.50"));
        var first = fixture.createProduct("schema-first", new BigDecimal("10.00"));
        return new ExistingData(buyer, first, second);
    }

    private SqlFixture sqlFixture() {
        var data = existingData();
        return new SqlFixture(data, insertRow("tb_order", orderValues(data.buyer())),
                insertRow("tb_order", orderValues(data.buyer())));
    }

    private Map<String, Object> orderValues(long buyer) {
        var values = new LinkedHashMap<String, Object>();
        values.put("buyer_id", buyer);
        values.put("created_at", OffsetDateTime.ofInstant(CREATED_AT, ZoneOffset.UTC));
        values.put("status", "WAITING_PAYMENT");
        return values;
    }

    private Map<String, Object> itemValues(long order, long product) {
        var values = new LinkedHashMap<String, Object>();
        values.put("order_id", order);
        values.put("product_id", product);
        values.put("position", 0);
        values.put("quantity", 1);
        values.put("unit_price", new BigDecimal("10.00"));
        return values;
    }

    private long insertRow(String table, Map<String, Object> values) {
        var keys = new GeneratedKeyHolder();
        var placeholders = values.keySet().stream().map(ignored -> "?").collect(Collectors.joining(", "));
        var sql = "insert into " + table + " (" + String.join(", ", values.keySet()) + ") values (" + placeholders + ")";
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            var parameter = 1;
            for (var value : values.values()) statement.setObject(parameter++, value);
            return statement;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private List<ItemRow> itemRows(long orderId) {
        return jdbc.query("select order_id, product_id, position, quantity, unit_price from tb_order_item "
                        + "where order_id = ? order by position",
                (rs, row) -> new ItemRow(rs.getLong("order_id"), rs.getLong("product_id"), rs.getInt("position"),
                        rs.getInt("quantity"), rs.getBigDecimal("unit_price")), orderId);
    }

    private static void assertSqlConstraint(String expectedState, String checkName, Runnable statement) {
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class).satisfies(error -> {
            var cause = ((DataIntegrityViolationException) error).getMostSpecificCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(expectedState);
            if (checkName != null) {
                assertThat(cause.getMessage().toLowerCase(Locale.ROOT)).contains(checkName);
            }
        });
    }

    static Stream<Arguments> requiredColumns() {
        return Stream.of(Arguments.of("tb_order", "buyer_id"), Arguments.of("tb_order", "created_at"),
                Arguments.of("tb_order", "status"), Arguments.of("tb_order_item", "order_id"),
                Arguments.of("tb_order_item", "product_id"), Arguments.of("tb_order_item", "position"),
                Arguments.of("tb_order_item", "quantity"), Arguments.of("tb_order_item", "unit_price"));
    }

    static Stream<Arguments> foreignKeyColumns() {
        return Stream.of(Arguments.of("tb_order", "buyer_id"), Arguments.of("tb_order_item", "order_id"),
                Arguments.of("tb_order_item", "product_id"));
    }

    static Stream<Arguments> invalidCheckValues() {
        return Stream.of(Arguments.of("quantity", 0, "ck_order_item_quantity_positive"),
                Arguments.of("quantity", -1, "ck_order_item_quantity_positive"),
                Arguments.of("position", -1, "ck_order_item_position_nonnegative"),
                Arguments.of("unit_price", BigDecimal.ZERO, "ck_order_item_unit_price_positive"),
                Arguments.of("unit_price", new BigDecimal("-0.01"), "ck_order_item_unit_price_positive"));
    }

    private record Column(int jdbcType, String typeName, int size, int scale, int nullable, boolean autoIncrement) { }
    private record ExistingData(long buyer, long firstProduct, long secondProduct) { }
    private record SqlFixture(ExistingData data, long firstOrder, long secondOrder) { }
    private record ItemRow(long order, long product, int position, int quantity, BigDecimal unitPrice) { }
}
