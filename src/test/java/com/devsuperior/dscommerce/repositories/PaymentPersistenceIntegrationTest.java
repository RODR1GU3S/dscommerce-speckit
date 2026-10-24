package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T007: real Hibernate schema/JDBC, no synthetic DDL or imports of absent Payment APIs. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
        "feature008.payment-test-context=persistence-t007"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(PaymentRegistrationTestConfig.class)
public class PaymentPersistenceIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManagerFactory factory;
    private Database database;

    @BeforeEach
    void verifyIsolatedRealDatabaseOutsideCallerTransaction() throws SQLException {
        database = new Database(context);
    }

    @Test
    void generatedPaymentSchemaHasOnlySharedPrimaryForeignKeyAndRequiredMillisecondOccurrence() throws SQLException {
        database.requirePaymentSchema();
        var columns = database.columns("TB_PAYMENT");
        assertThat(columns.keySet()).containsExactlyInAnyOrder("ORDER_ID", "OCCURRED_AT");
        assertThat(columns.values()).allSatisfy(column -> {
            assertThat(column.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls);
            assertThat(column.autoIncrement()).isFalse();
        });
        assertThat(columns.get("ORDER_ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("OCCURRED_AT").jdbcType()).isEqualTo(Types.TIMESTAMP_WITH_TIMEZONE);
        assertThat(columns.get("OCCURRED_AT").scale()).isEqualTo(3);
        try (var connection = database.connection()) {
            var keys = new ArrayList<String>();
            try (var rows = connection.getMetaData().getPrimaryKeys(null, "PUBLIC", "TB_PAYMENT")) {
                while (rows.next()) {
                    assertThat(rows.getShort("KEY_SEQ")).isEqualTo((short) 1);
                    keys.add(rows.getString("COLUMN_NAME"));
                }
            }
            assertThat(keys).containsExactly("ORDER_ID");
            var foreignKeys = new ArrayList<String>();
            try (var rows = connection.getMetaData().getImportedKeys(null, "PUBLIC", "TB_PAYMENT")) {
                while (rows.next()) {
                    foreignKeys.add(rows.getString("FKCOLUMN_NAME") + "->"
                            + rows.getString("PKTABLE_NAME") + "." + rows.getString("PKCOLUMN_NAME"));
                    assertThat(rows.getString("FK_NAME").toLowerCase(Locale.ROOT)).isEqualTo("fk_payment_order");
                    assertThat(rows.getShort("DELETE_RULE")).isNotEqualTo((short) DatabaseMetaData.importedKeyCascade);
                }
            }
            assertThat(foreignKeys).containsExactly("ORDER_ID->TB_ORDER.ID");
        }
    }

    @Test
    void orderKeepsItsFourOriginalRequiredColumnsWithoutPaymentOrVersionColumns() throws SQLException {
        var columns = database.columns("TB_ORDER");
        assertThat(columns.keySet()).containsExactlyInAnyOrder("ID", "BUYER_ID", "CREATED_AT", "STATUS");
        assertThat(columns.values()).allSatisfy(column ->
                assertThat(column.nullable()).isEqualTo(DatabaseMetaData.columnNoNulls));
        assertThat(columns.get("ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("ID").autoIncrement()).isTrue();
        assertThat(columns.get("BUYER_ID").jdbcType()).isEqualTo(Types.BIGINT);
        assertThat(columns.get("CREATED_AT").jdbcType()).isIn(Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE);
        assertThat(columns.get("STATUS").jdbcType()).isIn(Types.VARCHAR, Types.OTHER);
    }

    @Test
    void domainStatusContainsExactlyWaitingPaymentAndPaid() {
        assertThat(Arrays.stream(OrderStatus.values()).map(Enum::name).toList())
                .as("T008 must extend the real OrderStatus enum with PAID")
                .containsExactlyInAnyOrder("WAITING_PAYMENT", "PAID");
    }

    @Test
    void paymentIsRegisteredInTheRealJpaMetamodel() {
        assertThat(factory.getMetamodel().getEntities().stream().map(entity -> entity.getJavaType().getName()).toList())
                .as("T008 must register the real Payment entity, not a test table")
                .contains("com.devsuperior.dscommerce.entities.Payment");
        // Constructor/MapsId/persist/LAZY Java API tests must precede the T008/T009 bodies once compilable.
    }

    @Test
    void generatedOrderStatusConstraintAcceptsPaidAndRejectsUnknownWithoutChangingOtherColumns() throws SQLException {
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            var before = database.previous().snapshot();
            assertThatCode(() -> database.update("update tb_order set status = ? where id = ?", "PAID", scenario.order().id()))
                    .as("T008 generated tb_order.status must accept PAID").doesNotThrowAnyException();
            assertThat(database.text("select status from tb_order where id = ?", scenario.order().id())).isEqualTo("PAID");
            var accepted = database.previous().snapshot();
            assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before, accepted))
                    .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
            var original = before.data().orders().stream().filter(row -> row.id() == scenario.order().id()).findFirst().orElseThrow();
            var changed = accepted.data().orders().stream().filter(row -> row.id() == scenario.order().id()).findFirst().orElseThrow();
            assertThat(changed.id()).isEqualTo(original.id());
            assertThat(changed.buyerId()).isEqualTo(original.buyerId());
            assertThat(changed.createdAt()).isEqualTo(original.createdAt());
            var type = database.columns("TB_ORDER").get("STATUS").typeName();
            var nativeEnum = type.toUpperCase(Locale.ROOT).startsWith("ENUM");
            assertSqlState(nativeEnum ? "22030" : "23513", () -> database.update("update tb_order set status = ? where id = ?",
                    "T007_UNKNOWN", scenario.order().id()));
            OwnOrderQueryDatabaseProbe.assertUnchanged(accepted, database.previous().snapshot());
            if (nativeEnum) {
                try (var connection = database.connection(); var statement = connection.prepareStatement(
                        "select ev.value_name from information_schema.enum_values ev "
                                + "join information_schema.columns c on ev.object_catalog=c.table_catalog "
                                + "and ev.object_schema=c.table_schema and ev.object_name=c.table_name "
                                + "and ev.enum_identifier=c.dtd_identifier "
                                + "where c.table_schema='PUBLIC' and c.table_name='TB_ORDER' and c.column_name='STATUS'")) {
                    statement.setQueryTimeout(10);
                    var values = new ArrayList<String>();
                    try (var rows = statement.executeQuery()) { while (rows.next()) { values.add(rows.getString(1)); } }
                    assertThat(values).containsExactlyInAnyOrder("WAITING_PAYMENT", "PAID");
                }
            } else {
                try (var connection = database.connection(); var statement = connection.prepareStatement(
                        "select cc.check_clause from information_schema.check_constraints cc "
                                + "join information_schema.table_constraints tc on cc.constraint_catalog=tc.constraint_catalog "
                                + "and cc.constraint_schema=tc.constraint_schema and cc.constraint_name=tc.constraint_name "
                                + "where tc.table_schema='PUBLIC' and tc.table_name='TB_ORDER'")) {
                    statement.setQueryTimeout(10);
                    var clauses = new ArrayList<String>();
                    try (var rows = statement.executeQuery()) { while (rows.next()) { clauses.add(rows.getString(1)); } }
                    assertThat(clauses).anySatisfy(clause -> assertThat(clause).contains("WAITING_PAYMENT", "PAID"));
                }
            }
        }
    }

    @ParameterizedTest(name = "exact committed JDBC instant: {0}")
    @ValueSource(strings = {"0001-01-01T00:00:00.000Z", "2026-10-21T11:00:00.123Z", "9999-12-31T23:59:59.999Z"})
    void realPaymentRoundTripsAcceptedMillisecondsAcrossIndependentConnections(String value) throws SQLException {
        database.requirePaymentSchema();
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            var before = database.previous().snapshot();
            var occurrence = Instant.parse(value);
            assertThat(database.insertPayment(scenario.order().id(), occurrence)).isEqualTo(1);
            var after = database.probe().snapshot();
            assertThat(after.payments()).containsExactly(new PaymentRow(scenario.order().id(), occurrence));
            OwnOrderQueryDatabaseProbe.assertUnchanged(before, after.previous());
            try (var connection = database.connection(); var statement = connection.prepareStatement(
                    "select occurred_at from tb_payment where order_id = ?")) {
                statement.setQueryTimeout(10);
                statement.setLong(1, scenario.order().id());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject(1, OffsetDateTime.class).toInstant()).isEqualTo(occurrence);
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }

    @Test
    void duplicatePrimaryKeyIsRejectedByJdbcAndPreservesTheOriginalOccurrence() throws SQLException {
        database.requirePaymentSchema();
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            database.insertPayment(scenario.order().id(), Database.OCCURRED_AT);
            var before = database.probe().snapshot();
            assertSqlState("23505", () -> database.insertPayment(scenario.order().id(), Database.OCCURRED_AT.plusMillis(1)));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    @Test
    void unknownOrderIsRejectedByTheNamedForeignKeyWithoutCreatingAnyPayment() throws SQLException {
        database.requirePaymentSchema();
        var before = database.probe().snapshot();
        assertThat(before.previous().data().orders()).noneMatch(row -> row.id() == Long.MAX_VALUE);
        assertSqlState("23506", () -> database.insertPayment(Long.MAX_VALUE, Database.OCCURRED_AT));
        PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
    }

    @ParameterizedTest(name = "SQL NOT NULL: {0}")
    @ValueSource(strings = {"order_id", "occurred_at"})
    void requiredPaymentColumnsRejectNullInTheDatabase(String column) throws SQLException {
        database.requirePaymentSchema();
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            var before = database.probe().snapshot();
            assertSqlState("23502", () -> database.update("insert into tb_payment(order_id, occurred_at) values (?, ?)",
                    column.equals("order_id") ? null : scenario.order().id(),
                    column.equals("occurred_at") ? null : Database.OCCURRED_AT.atOffset(ZoneOffset.UTC)));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    @Test
    void deletingTheReferencedOrderCannotCascadeAwayItsPayment() throws SQLException {
        database.requirePaymentSchema();
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            // Remove the other order dependants explicitly so only the payment FK can explain rejection.
            assertThat(database.update("delete from tb_order_item where order_id = ?", scenario.order().id())).isEqualTo(1);
            database.insertPayment(scenario.order().id(), Database.OCCURRED_AT);
            var before = database.probe().snapshot();
            assertSqlState("23503", () -> database.update("delete from tb_order where id = ?", scenario.order().id()));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    private static void assertSqlState(String expected, SqlOperation operation) {
        assertThatThrownBy(operation::run).isInstanceOf(SQLException.class)
                .satisfies(failure -> {
                    assertThat(((SQLException) failure).getSQLState()).isEqualTo(expected);
                    if (expected.equals("23506") || expected.equals("23503")) {
                        assertThat(failure.getMessage()).containsIgnoringCase("fk_payment_order");
                    }
                });
    }

    @FunctionalInterface
    private interface SqlOperation { void run() throws SQLException; }

    public record Column(int jdbcType, String typeName, int scale, int nullable, boolean autoIncrement) { }
    public record Scenario(Account buyer, ProductData product, OrderManifest order) { }

    /** Shared only by the two T007 tests; direct JDBC setup is not the payment operation or an ORM proof. */
    public static final class Database {
        public static final Instant CREATED_AT = Instant.parse("2026-10-21T10:00:00.123Z");
        public static final Instant OCCURRED_AT = Instant.parse("2026-10-21T11:00:00.123Z");
        private final WebApplicationContext context;
        private final PaymentRegistrationDatabaseProbe probe;
        private final OwnOrderQueryDatabaseProbe previous;

        public Database(WebApplicationContext context) throws SQLException {
            this.context = context;
            assertNoCallerTransaction();
            var environment = context.getEnvironment();
            var url = environment.getRequiredProperty("spring.datasource.url");
            assertThat(url).startsWith("jdbc:h2:mem:admin-product-test-");
            assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
            assertThat(environment.getRequiredProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
            assertThat(environment.getRequiredProperty("spring.jpa.open-in-view")).isEqualTo("false");
            probe = new PaymentRegistrationDatabaseProbe(url);
            previous = new OwnOrderQueryDatabaseProbe(url);
            try (var first = connection(); var second = connection()) { assertThat(first).isNotSameAs(second); }
        }

        public Connection connection() throws SQLException {
            assertNoCallerTransaction();
            var connection = probe.openConnection();
            try {
                assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
                assertThat(connection.getAutoCommit()).isTrue();
                return connection;
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.close(); } catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }

        public void requirePaymentSchema() throws SQLException {
            try (var connection = connection(); var tables = connection.getMetaData().getTables(null, "PUBLIC", "TB_PAYMENT", null)) {
                var exists = tables.next();
                System.out.println("T007_SCHEMA context=" + context.getEnvironment().getRequiredProperty("feature008.payment-test-context")
                        + " realPaymentTable=" + exists);
                assertThat(exists).as("T007 requires the real PUBLIC.TB_PAYMENT generated by T008 before fixtures/probe/SQL").isTrue();
            }
        }

        public Map<String, Column> columns(String table) throws SQLException {
            var columns = new LinkedHashMap<String, Column>();
            try (var connection = connection(); var rows = connection.getMetaData().getColumns(null, "PUBLIC", table, null)) {
                while (rows.next()) {
                    columns.put(rows.getString("COLUMN_NAME"), new Column(rows.getInt("DATA_TYPE"), rows.getString("TYPE_NAME"),
                            rows.getInt("DECIMAL_DIGITS"), rows.getInt("NULLABLE"), "YES".equals(rows.getString("IS_AUTOINCREMENT"))));
                }
            }
            return columns;
        }

        public PaymentRegistrationTestFixture fixture() { return new PaymentRegistrationTestFixture(context); }
        public PaymentRegistrationDatabaseProbe probe() { return probe; }
        public OwnOrderQueryDatabaseProbe previous() { return previous; }

        public Scenario prepare(PaymentRegistrationTestFixture fixture) {
            var buyer = fixture.prepareBuyer();
            var product = fixture.createProduct("T007 owned product", new BigDecimal("12.00"));
            var order = fixture.createOrder(buyer, CREATED_AT,
                    List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
            return new Scenario(buyer, product, order);
        }

        public int insertPayment(long orderId, Instant occurrence) throws SQLException {
            return update("insert into tb_payment(order_id, occurred_at) values (?, ?)", orderId, occurrence.atOffset(ZoneOffset.UTC));
        }

        public int update(String sql, Object... values) throws SQLException {
            try (var connection = connection()) {
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(10);
                    for (int i = 0; i < values.length; i++) { statement.setObject(i + 1, values[i]); }
                    var changed = statement.executeUpdate();
                    connection.commit();
                    return changed;
                } catch (SQLException | RuntimeException | Error failure) {
                    try { connection.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                    throw failure;
                }
            }
        }

        public String text(String sql, long id) throws SQLException {
            try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(10);
                statement.setLong(1, id);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    var value = rows.getString(1);
                    assertThat(rows.next()).isFalse();
                    return value;
                }
            }
        }

        private static void assertNoCallerTransaction() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        }
    }
}
