package com.devsuperior.dscommerce.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** T008 domain APIs; no security, service, repository or HTTP operation. */
class PaymentModelTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-21T10:00:00.123Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-21T11:00:00.123Z");

    @Test
    void constructingPaymentPreservesTheOrderAndOccurrenceWithoutChangingItsInitialStatus() {
        var order = order();
        var payment = new Payment(order, OCCURRED_AT);

        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(payment.getOrderId()).isNull(); // The transient order has no identity to share yet.
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
        assertHistory(order);
    }

    @ParameterizedTest(name = "required Payment constructor argument: {0}")
    @ValueSource(strings = {"order", "occurredAt"})
    void paymentRequiresAnOrderAndAnOccurrence(String missing) {
        assertThatThrownBy(() -> new Payment(missing.equals("order") ? null : order(),
                missing.equals("occurredAt") ? null : OCCURRED_AT)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void markPaidChangesOnlyStatusAndPreservesBuyerCreationOrderedLinesAndHistoricalMoney() {
        var order = order();
        var buyer = order.getBuyer();
        var first = order.getItems().getFirst();
        var second = order.getItems().getLast();
        var firstProduct = first.getProduct();
        var secondProduct = second.getProduct();

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getId()).isNull();
        assertThat(order.getBuyer()).isSameAs(buyer);
        assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getItems()).containsExactly(first, second);
        assertThat(first.getProduct()).isSameAs(firstProduct);
        assertThat(second.getProduct()).isSameAs(secondProduct);
        assertHistory(order);
        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void markPaidRejectsAnAlreadyPaidOrderWithoutChangingItsHistory() {
        var order = order();
        order.markPaid();

        assertThatThrownBy(order::markPaid).isInstanceOf(IllegalStateException.class)
                .hasMessage("Order is not awaiting payment");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertHistory(order);
    }

    private static Order order() {
        var buyer = new UserAccount(42L, "t008-model-buyer", "unused-test-hash");
        var first = new Product(20L, "Current product A", "https://example.invalid/a", new BigDecimal("12.00"));
        var second = new Product(10L, "Current product B", "https://example.invalid/b", new BigDecimal("99.00"));
        return new Order(buyer, CREATED_AT, List.of(new OrderItem(first, 2, new BigDecimal("10.00")),
                new OrderItem(second, 3, new BigDecimal("7.50"))));
    }

    private static void assertHistory(Order order) {
        assertThat(order.getBuyer().getId()).isEqualTo(42L);
        assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getItems()).hasSize(2).extracting(OrderItem::getPosition).containsExactly(0, 1);
        assertThat(order.getItems()).extracting(item -> item.getProduct().getId()).containsExactly(20L, 10L);
        assertThat(order.getItems()).extracting(OrderItem::getOrder).containsOnly(order);
        assertThat(order.getItems()).extracting(OrderItem::getQuantity).containsExactly(2, 3);
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(order.getItems().getLast().getUnitPrice()).isEqualByComparingTo("7.50");
        assertThat(order.getTotal()).isEqualByComparingTo("42.50");
    }
}
