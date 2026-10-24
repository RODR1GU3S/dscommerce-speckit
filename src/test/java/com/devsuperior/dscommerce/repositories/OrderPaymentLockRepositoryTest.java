package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.Payment;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T020 authorship: stale first-level cache, not an HTTP transaction/commit experiment. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
        "feature008.payment-test-context=lock-t020"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(PaymentRegistrationTestConfig.class)
class OrderPaymentLockRepositoryTest {
    @Autowired private WebApplicationContext context;
    @Autowired private EntityManagerFactory factory;

    @Test
    void lockAndRefreshReplacePreloadedWaitingStateWithAnotherTransactionsCommittedSettlement() throws SQLException {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        var database = new PaymentRegistrationDatabaseProbe(
                context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        var cleanupBaseline = database.snapshot();
        try {
            try (var fixture = new PaymentRegistrationTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var product = fixture.createProduct("T020 stale-context catalog", new BigDecimal("12.00"));
                var createdAt = Instant.parse("2026-01-01T10:00:00.123Z");
                var occurredAt = createdAt.plusSeconds(3600);
                var order = fixture.createOrder(buyer, createdAt,
                        List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
                var before = database.snapshot();
                var readerSql = new ArrayList<String>();
                var sessions = factory.unwrap(SessionFactory.class);
                // Per-session observation only; preserve the application's/global inspector and mapper.
                try (var reader = sessions.withOptions().statementInspector(sql -> {
                    readerSql.add(sql.replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT));
                    return sql;
                }).openSession(); var writer = sessions.openSession()) {
                    var readerTx = reader.beginTransaction();
                    try {
                        var readerConnection = identity(reader, database);
                        var cached = reader.find(Order.class, order.id());
                        assertThat(cached.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
                        var writerTx = writer.beginTransaction();
                        try {
                            var writerConnection = identity(writer, database);
                            assertThat(writerConnection.url()).isEqualTo(readerConnection.url());
                            assertThat(writerConnection.session()).isNotEqualTo(readerConnection.session());
                            var current = writer.find(Order.class, order.id());
                            writer.persist(new Payment(current, occurredAt));
                            current.markPaid();
                            writer.flush();
                            writerTx.commit();
                            System.out.println("T020_REFRESH reader=" + readerConnection.session()
                                    + " committedWriter=" + writerConnection.session() + " order=" + order.id());
                        } finally {
                            if (writerTx.isActive()) { writerTx.rollback(); }
                        }
                        var committed = database.snapshot();
                        assertThat(committed.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(
                                before.payments().stream(), Stream.of(new PaymentRow(order.id(), occurredAt))).toList());
                        assertThat(committed.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                                .map(row -> row.id() == order.id()
                                        ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
                        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(
                                before.previous(), committed.previous())).isInstanceOf(AssertionError.class)
                                .hasMessage("Observed database state changed: tb_order");
                        assertThat(readerTx.isActive()).isTrue();
                        assertThat(reader.contains(cached)).isTrue();
                        assertThat(reader.find(Order.class, order.id())).isSameAs(cached);
                        assertThat(cached.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);

                        var fragment = new OrderPaymentLockRepositoryImpl();
                        ReflectionTestUtils.setField(fragment, "entityManager", reader);
                        int start = readerSql.size();
                        var refreshed = fragment.findPaymentOrderForUpdate(order.id()).orElseThrow();
                        var acquisitionSql = List.copyOf(readerSql.subList(start, readerSql.size()));
                        assertThat(refreshed).isSameAs(cached);
                        assertThat(refreshed.getStatus()).isEqualTo(OrderStatus.PAID);
                        assertThat(refreshed.getId()).isEqualTo(order.id());
                        assertThat(refreshed.getCreatedAt()).isEqualTo(createdAt);
                        assertThat(refreshed.getBuyer().getId()).isEqualTo(buyer.id());
                        assertThat(reader.getLockMode(refreshed)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
                        assertThat(acquisitionSql).hasSize(2).allSatisfy(OrderPaymentLockRepositoryTest::assertRootLock);
                        assertThat(readerSql).allSatisfy(sql -> assertThat(sql).startsWith("select "));
                        readerTx.rollback(); // Must not undo the independent writer's committed settlement.
                        PaymentRegistrationDatabaseProbe.assertUnchanged(committed, database.snapshot());
                    } finally {
                        if (readerTx.isActive()) { readerTx.rollback(); }
                    }
                }
                try (var connection = database.openConnection()) {
                    connection.setAutoCommit(false);
                    try (var timeout = connection.createStatement()) { timeout.execute("set lock_timeout 2000"); }
                    try (var statement = connection.prepareStatement("select id from tb_order where id = ? for update")) {
                        statement.setQueryTimeout(2);
                        statement.setLong(1, order.id());
                        try (var rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getLong(1)).isEqualTo(order.id());
                            assertThat(rows.next()).isFalse();
                        }
                    } finally { connection.rollback(); }
                }
            }
        } finally {
            PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot());
        }
    }

    private static ConnectionIdentity identity(Session session, PaymentRegistrationDatabaseProbe database) {
        return session.doReturningWork(connection -> {
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            try (var timeout = connection.createStatement()) {
                timeout.setQueryTimeout(2);
                timeout.execute("set lock_timeout 2000");
            }
            try (var observer = database.openConnection()) {
                assertThat(connection.getMetaData().getURL()).isEqualTo(observer.getMetaData().getURL());
            }
            try (var statement = connection.prepareStatement("select session_id()")) {
                statement.setQueryTimeout(2);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    int id = rows.getInt(1);
                    assertThat(id).isPositive();
                    assertThat(rows.next()).isFalse();
                    return new ConnectionIdentity(id, connection.getMetaData().getURL());
                }
            }
        });
    }

    private static void assertRootLock(String sql) {
        assertThat(sql).startsWith("select ").contains("from tb_order ", "for update", " where ");
        assertThat(sql).doesNotContain(" join ", "distinct", "nowait", "skip locked");
        assertThat(sql).doesNotContainPattern("\\b(?:tb_product|tb_order_item|tb_payment)\\b");
        assertThat(sql.substring(sql.indexOf(" where ")))
                .matches(" where (?:[a-z0-9_]+\\.)?id\\s*=\\s*\\? for update");
    }

    private record ConnectionIdentity(int session, String url) { }
}
