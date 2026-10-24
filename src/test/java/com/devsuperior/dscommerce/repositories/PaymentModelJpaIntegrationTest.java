package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.Payment;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.repositories.PaymentPersistenceIntegrationTest.Database;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real ORM/commit/independent JDBC for T008; no future insert repository, service or endpoint. */
@SpringBootTest(properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
        "feature008.payment-test-context=model-jpa-t008"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(PaymentRegistrationTestConfig.class)
class PaymentModelJpaIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory factory;
    @PersistenceContext private EntityManager entityManager;
    private Database database;
    private TransactionTemplate transaction;

    @BeforeEach
    void prepareExplicitModelTransactionOutsideCallerTransaction() throws SQLException {
        database = new Database(context);
        database.requirePaymentSchema();
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @ParameterizedTest(name = "MapsId and exact ORM/JDBC instant: {0}")
    @ValueSource(strings = {"0001-01-01T00:00:00.000Z", "2026-10-21T11:00:00.123Z", "9999-12-31T23:59:59.999Z"})
    void persistDerivesTheSharedIdentityAndReloadsLazyOrderAndExactOccurrence(String value) {
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            var before = database.previous().snapshot();
            var occurrence = Instant.parse(value);
            var id = transaction.execute(status -> {
                assertThat(status.isNewTransaction()).isTrue();
                var order = entityManager.find(Order.class, scenario.order().id());
                var payment = new Payment(order, occurrence);
                entityManager.persist(payment);
                entityManager.flush();
                assertThat(payment.getOrderId()).isEqualTo(order.getId());
                entityManager.clear();

                var reloaded = entityManager.find(Payment.class, scenario.order().id());
                assertThat(reloaded).isNotNull();
                assertThat(reloaded.getOrderId()).isEqualTo(scenario.order().id());
                assertThat(reloaded.getOccurredAt()).isEqualTo(occurrence);
                assertThat(factory.getPersistenceUnitUtil().isLoaded(reloaded, "order")).isFalse();
                assertThat(reloaded.getOrder().getId()).isEqualTo(scenario.order().id());
                assertThat(factory.getPersistenceUnitUtil().isLoaded(reloaded, "order")).isFalse();
                var orderAttribute = (SingularAttribute<?, ?>) factory.getMetamodel().entity(Payment.class).getAttribute("order");
                assertThat(orderAttribute.getPersistentAttributeType()).isEqualTo(PersistentAttributeType.ONE_TO_ONE);
                assertThat(orderAttribute.isOptional()).isFalse();
                return payment.getOrderId();
            });
            // A fresh driver connection observes state after the test-owned ORM transaction committed.
            assertThat(id).isEqualTo(scenario.order().id());
            var after = database.probe().snapshot();
            assertThat(after.payments()).containsExactly(new PaymentRow(scenario.order().id(), occurrence));
            OwnOrderQueryDatabaseProbe.assertUnchanged(before, after.previous());
            assertThat(after.previous().data().orders().getFirst().status()).isEqualTo("WAITING_PAYMENT");
        }
    }

    @Test
    void explicitModelTransactionCommitsPaymentAndOnlyTheTargetStatusWhilePreservingHistory() {
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            var other = database.prepare(fixture);
            var before = database.previous().snapshot();
            transaction.executeWithoutResult(status -> {
                var order = entityManager.find(Order.class, scenario.order().id());
                assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
                entityManager.persist(new Payment(order, Database.OCCURRED_AT));
                order.markPaid();
                entityManager.flush();
                entityManager.clear();
                var reloaded = entityManager.find(Order.class, scenario.order().id());
                assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
                assertThat(reloaded.getBuyer().getId()).isEqualTo(scenario.buyer().id());
                assertThat(reloaded.getCreatedAt()).isEqualTo(Database.CREATED_AT);
                assertThat(reloaded.getTotal()).isEqualByComparingTo("20.00");
            });
            var after = database.probe().snapshot();
            assertThat(after.payments()).containsExactly(new PaymentRow(scenario.order().id(), Database.OCCURRED_AT));
            var expectedOrders = before.data().orders().stream().map(row -> row.id() == scenario.order().id()
                    ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList();
            assertThat(after.previous().data().orders()).isEqualTo(expectedOrders);
            assertThat(after.previous().data().items()).isEqualTo(before.data().items());
            assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before, after.previous()))
                    .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
            assertThat(after.previous().data().orders().stream().filter(row -> row.id() == other.order().id()).findFirst())
                    .hasValue(new OrderRow(other.order().id(), other.buyer().id(), Database.CREATED_AT, "WAITING_PAYMENT"));
        }
    }

    @Test
    void removingPaymentThroughJpaDoesNotCascadeRemovalToItsOrderOrHistoricalItems() {
        try (var fixture = database.fixture()) {
            var scenario = database.prepare(fixture);
            transaction.executeWithoutResult(status -> entityManager.persist(new Payment(
                    entityManager.find(Order.class, scenario.order().id()), Database.OCCURRED_AT)));
            var before = database.previous().snapshot();
            transaction.executeWithoutResult(status -> {
                entityManager.remove(entityManager.find(Payment.class, scenario.order().id()));
                entityManager.flush();
            });
            var after = database.probe().snapshot();
            assertThat(after.payments()).isEmpty();
            OwnOrderQueryDatabaseProbe.assertUnchanged(before, after.previous());
        }
    }
}
