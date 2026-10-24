package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderItem;
import com.devsuperior.dscommerce.entities.Payment;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.repositories.PaymentPersistenceIntegrationTest.Database;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

/** T009 repository wiring and real H2 access, without a payment service or HTTP operation. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
        "feature008.payment-test-context=repositories-t009"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import({PaymentRegistrationTestConfig.class, OwnOrderQueryTestObservationConfig.class})
class PaymentRepositoriesIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private OrderRepository orders;
    @Autowired private PaymentRepository payments;
    @Autowired private SqlObservation sql;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory factory;
    @PersistenceContext private EntityManager entityManager;
    private Database database;
    private TransactionTemplate transaction;

    @BeforeEach
    void prepareOwnedReadCommittedRepositoryTransaction() throws SQLException {
        database = new Database(context);
        database.requirePaymentSchema();
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        sql.clear();
    }

    @Test
    void paymentRepositoryIsRegisteredInTheRealSpringContext() {
        assertThat(context.containsBean("paymentRepository"))
                .as("T009 must register the real payment repository before its API scenarios can run").isTrue();
    }

    @ParameterizedTest(name = "root acquisition must not filter stored status: {0}")
    @ValueSource(strings = {"WAITING_PAYMENT", "PAID"})
    void lockAndRefreshOnlyTheRequestedRootWithoutLoadingBuyerOrItems(String storedStatus) throws SQLException {
        try (var fixture = database.fixture()) {
            var target = database.prepare(fixture);
            database.prepare(fixture); // Distinct buyer and non-target root.
            database.update("update tb_order set status = ? where id = ?", storedStatus, target.order().id());
            var before = database.probe().snapshot();
            sql.clear();
            transaction.executeWithoutResult(status -> {
                var root = orders.findPaymentOrderForUpdate(target.order().id()).orElseThrow();
                assertThat(root.getId()).isEqualTo(target.order().id());
                assertThat(root.getStatus().name()).isEqualTo(storedStatus);
                assertThat(root.getCreatedAt()).isEqualTo(Database.CREATED_AT);
                assertThat(entityManager.contains(root)).isTrue();
                assertThat(entityManager.getLockMode(root)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
                assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "buyer")).isFalse();
                assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "items")).isFalse();
            });
            assertRootLocks(statements(), 2);
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    @Test
    void missingRootReturnsEmptyWithoutRefreshOrUnrelatedReads() {
        var before = database.probe().snapshot();
        transaction.executeWithoutResult(status -> assertThat(orders.findPaymentOrderForUpdate(Long.MAX_VALUE)).isEmpty());
        assertRootLocks(statements(), 1);
        PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
    }

    @Test
    void lookupThenScalarHistoricalProjectionFollowTheRootLocksWithoutReadingProductOrLoadingItems() {
        try (var fixture = database.fixture()) {
            var buyer = fixture.prepareBuyer();
            var first = fixture.createProduct("T009 current A", new BigDecimal("12.00"));
            var second = fixture.createProduct("T009 current B", new BigDecimal("99.00"));
            var target = fixture.createOrder(buyer, Database.CREATED_AT,
                    List.of(new ItemInput(second.id(), 2, "10.00", "20.00"),
                            new ItemInput(first.id(), 3, "7.50", "22.50")), "42.50");
            database.prepare(fixture);
            var before = database.probe().snapshot();
            sql.clear();
            transaction.executeWithoutResult(status -> {
                var root = orders.findPaymentOrderForUpdate(target.id()).orElseThrow();
                assertThat(payments.findById(root.getId())).isEmpty();
                var lines = orders.findPaymentItemsByOrderId(root.getId());
                assertThat(lines).extracting(OrderRepository.PaymentItemView::getProductId,
                        OrderRepository.PaymentItemView::getPosition, OrderRepository.PaymentItemView::getQuantity,
                        OrderRepository.PaymentItemView::getUnitPrice).containsExactly(
                        tuple(second.id(), 0, 2, new BigDecimal("10.00")),
                        tuple(first.id(), 1, 3, new BigDecimal("7.50")));
                assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "items")).isFalse();
                assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "buyer")).isFalse();
            });
            var events = statements();
            assertThat(events).hasSize(4);
            assertRootLocks(events.subList(0, 2), 2);
            assertThat(events.get(2)).contains("from tb_payment ").doesNotContain(" join ");
            assertThat(events.get(3)).contains("from tb_order_item ", "product_id", "position", "quantity", "unit_price")
                    .containsPattern("order by \\w+\\.position(?: asc)?$")
                    .doesNotContain(" join ", " distinct ", "tb_product ", "tb_category", "tb_user_account");
            assertThat(events).allSatisfy(statement -> assertThat(statement).startsWith("select "));
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    @Test
    void explicitInsertCommitsOneRowAndDuplicateKeyCannotOverwriteTheOriginalOccurrence() {
        try (var fixture = database.fixture()) {
            var target = database.prepare(fixture);
            var before = database.previous().snapshot();
            sql.clear();
            transaction.executeWithoutResult(status -> {
                var root = orders.findPaymentOrderForUpdate(target.order().id()).orElseThrow();
                var payment = new Payment(root, Database.OCCURRED_AT);
                payments.insert(payment);
                entityManager.flush();
                assertThat(payment.getOrderId()).isEqualTo(root.getId());
            });
            var insertion = statements();
            assertThat(insertion).hasSize(3);
            assertRootLocks(insertion.subList(0, 2), 2);
            assertThat(insertion.get(2)).startsWith("insert into tb_payment ");
            var committed = database.probe().snapshot();
            assertThat(committed.payments()).containsExactly(new PaymentRow(target.order().id(), Database.OCCURRED_AT));
            com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe.assertUnchanged(before, committed.previous());
            transaction.executeWithoutResult(status -> {
                var found = payments.findById(target.order().id()).orElseThrow();
                assertThat(found.getOrderId()).isEqualTo(target.order().id());
                assertThat(found.getOccurredAt()).isEqualTo(Database.OCCURRED_AT);
                assertThat(factory.getPersistenceUnitUtil().isLoaded(found, "order")).isFalse();
            });
            var failure = catchThrowable(() -> transaction.executeWithoutResult(status -> {
                var root = orders.findPaymentOrderForUpdate(target.order().id()).orElseThrow();
                payments.insert(new Payment(root, Database.OCCURRED_AT.plusMillis(1)));
                entityManager.flush();
            }));
            assertThat(failure).as("A new insert for the existing shared key must fail, never merge/update").isNotNull();
            Throwable cause = failure;
            while (cause != null && !(cause instanceof SQLException)) { cause = cause.getCause(); }
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23505");
            PaymentRegistrationDatabaseProbe.assertUnchanged(committed, database.probe().snapshot());
        }
    }

    @ParameterizedTest(name = "caller transaction is mandatory: {0}")
    @ValueSource(strings = {"lock", "items", "lookup", "insert"})
    void paymentRepositoryOperationsRequireTheCallersTransaction(String operation) {
        var before = database.probe().snapshot();
        var transientOrder = new Order(new UserAccount(42L, "unused", "unused-hash"), Database.CREATED_AT,
                List.of(new OrderItem(new Product(1L, "Unused", "image", BigDecimal.ONE), 1, BigDecimal.ONE)));
        assertThatThrownBy(() -> {
            switch (operation) {
                case "lock" -> orders.findPaymentOrderForUpdate(Long.MAX_VALUE);
                case "items" -> orders.findPaymentItemsByOrderId(Long.MAX_VALUE);
                case "lookup" -> payments.findById(Long.MAX_VALUE);
                case "insert" -> payments.insert(new Payment(transientOrder, Database.OCCURRED_AT));
                default -> throw new IllegalArgumentException("Unsupported transaction control");
            }
        }).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(sql.events()).isEmpty();
        PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
    }

    @Test
    void existingOwnQueriesKeepTheirOwnerPredicatesAfterAddingThePaymentFragment() {
        try (var fixture = database.fixture()) {
            var own = database.prepare(fixture);
            var foreign = database.prepare(fixture);
            var before = database.probe().snapshot();
            assertThat(orders.findOwnOrderIds(own.buyer().id(), PageRequest.of(0, 2)).getContent())
                    .containsExactly(own.order().id());
            assertThat(orders.findOwnOrdersWithItemsByIds(own.buyer().id(), List.of(own.order().id(), foreign.order().id())))
                    .extracting(Order::getId).containsExactly(own.order().id());
            assertThat(orders.findOwnOrderWithItemsById(own.buyer().id(), own.order().id())).isPresent();
            assertThat(orders.findOwnOrderWithItemsById(own.buyer().id(), foreign.order().id())).isEmpty();
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.probe().snapshot());
        }
    }

    private List<String> statements() {
        assertThat(sql.events()).allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        return sql.events().stream().map(event -> event.sql().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim()).toList();
    }

    private static void assertRootLocks(List<String> statements, int expected) {
        assertThat(statements).hasSize(expected).allSatisfy(statement -> {
            assertThat(statement).startsWith("select ").contains("from tb_order ", "for update")
                    .doesNotContain(" join ", "distinct", "tb_order_item", "tb_product", "tb_user_account");
            var where = statement.substring(statement.indexOf(" where "));
            assertThat(where).containsPattern("\\.id\\s*=\\s*\\?").doesNotContain("buyer_id", "status");
        });
    }
}
