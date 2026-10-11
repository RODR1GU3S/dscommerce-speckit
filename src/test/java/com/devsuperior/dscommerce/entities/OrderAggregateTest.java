package com.devsuperior.dscommerce.entities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure domain contract: no Spring context, persistence or automatic test rollback. */
class OrderAggregateTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-07T12:00:00.123Z");

    @Test
    void shouldCreateAnOrderWithTheCompleteSuppliedListBuyerTimeAndInitialStatus() {
        var buyer = buyer();
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);

        var order = new Order(buyer, CREATED_AT, List.of(first, second));

        assertThat(order.getBuyer()).isSameAs(buyer);
        assertThat(order.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(order.getItems()).containsExactly(first, second);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.WAITING_PAYMENT);
    }

    @ParameterizedTest(name = "reject invalid aggregate list: {0}")
    @MethodSource("invalidItemLists")
    void shouldRejectNullEmptyOrNullContainingItemLists(String description, List<OrderItem> items) {
        assertThatThrownBy(() -> new Order(buyer(), CREATED_AT, items))
                .as(description)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldBindEveryItemToTheOwningOrder() {
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);

        var order = new Order(buyer(), CREATED_AT, List.of(first, second));

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems()).extracting(OrderItem::getOrder).containsOnly(order);
        assertThat(order.getItems()).extracting(OrderItem::getProduct).containsExactly(
                first.getProduct(), second.getProduct());
    }

    @Test
    void shouldAssignZeroBasedPositionsInTheSuppliedLineOrder() {
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);

        var order = new Order(buyer(), CREATED_AT, List.of(first, second));

        assertThat(order.getItems()).containsExactly(first, second);
        assertThat(order.getItems()).extracting(OrderItem::getPosition).containsExactly(0, 1);
    }

    @Test
    void shouldRemainCompleteWhenTheCallerClearsTheOriginalList() {
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);
        var supplied = new ArrayList<>(List.of(first, second));
        var order = new Order(buyer(), CREATED_AT, supplied);

        supplied.clear();

        assertThat(order.getItems()).containsExactly(first, second);
    }

    @Test
    void shouldNotAcquireItemsAddedToTheOriginalListAfterCreation() {
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);
        var supplied = new ArrayList<>(List.of(first, second));
        var order = new Order(buyer(), CREATED_AT, supplied);

        supplied.add(item(30L, "1.00", 1));

        assertThat(order.getItems()).containsExactly(first, second);
    }

    @Test
    void shouldKeepItsLinesWhenTheCallerReplacesAnOriginalListElement() {
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);
        var supplied = new ArrayList<>(List.of(first, second));
        var order = new Order(buyer(), CREATED_AT, supplied);

        supplied.set(0, item(30L, "999.00", 50));

        assertThat(order.getItems()).containsExactly(first, second);
        assertThat(order.getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(order.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldPreventClearingTheExposedItemList() {
        var first = item(20L, "10.00", 2);
        var supplied = new ArrayList<>(List.of(first));
        var order = new Order(buyer(), CREATED_AT, supplied);

        assertThatThrownBy(() -> order.getItems().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.getItems()).containsExactly(first);
    }

    @Test
    void shouldPreventAddingToTheExposedItemList() {
        var first = item(20L, "10.00", 2);
        var supplied = new ArrayList<>(List.of(first));
        var order = new Order(buyer(), CREATED_AT, supplied);

        assertThatThrownBy(() -> order.getItems().add(item(30L, "1.00", 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.getItems()).containsExactly(first);
    }

    @Test
    void shouldPreventReplacingAnExposedItem() {
        var first = item(20L, "10.00", 2);
        var supplied = new ArrayList<>(List.of(first));
        var order = new Order(buyer(), CREATED_AT, supplied);

        assertThatThrownBy(() -> order.getItems().set(0, item(30L, "999.00", 50)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(order.getItems()).containsExactly(first);
    }

    @Test
    void shouldKeepTheHistoricalPriceAndQuantityWhenTheProductPriceChanges() {
        var product = product(20L, "10.00");
        var registered = new OrderItem(product, 2, new BigDecimal("10.00"));
        var order = new Order(buyer(), CREATED_AT, List.of(registered));

        // Test-only change of the existing Product object; its production API stays intact.
        ReflectionTestUtils.setField(product, "price", new BigDecimal("99.99"));

        assertThat(product.getPrice()).isEqualByComparingTo("99.99");
        var historical = order.getItems().getFirst();
        assertThat(historical.getProduct()).isSameAs(product);
        assertThat(historical.getQuantity()).isEqualTo(2);
        assertThat(historical.getUnitPrice()).isEqualByComparingTo("10.00");
    }

    @ParameterizedTest(name = "historical subtotal {0} x {1} = {2}")
    @CsvSource({"10.00,2,20.00", "7.50,3,22.50", "0.10,3,0.30"})
    void shouldDeriveSubtotalExactlyFromTheSuppliedSnapshotAndQuantity(
            String historicalPrice, int quantity, String expectedSubtotal) {
        var currentProduct = product(20L, "999.99");
        var registered = new OrderItem(currentProduct, quantity, new BigDecimal(historicalPrice));

        assertThat(registered.getSubtotal()).isEqualByComparingTo(expectedSubtotal);
    }

    @Test
    void shouldDeriveTheTotalFromAllHistoricalItemSubtotals() {
        var order = new Order(buyer(), CREATED_AT,
                List.of(item(20L, "10.00", 2), item(10L, "7.50", 3)));

        assertThat(order.getTotal()).isEqualByComparingTo("42.50");
    }

    @Test
    void shouldKeepHistoricalSubtotalsAndTotalAfterBothProductPricesChange() {
        var first = item(20L, "10.00", 2);
        var second = item(10L, "7.50", 3);
        var order = new Order(buyer(), CREATED_AT, List.of(first, second));

        ReflectionTestUtils.setField(first.getProduct(), "price", new BigDecimal("99.99"));
        ReflectionTestUtils.setField(second.getProduct(), "price", new BigDecimal("25.00"));

        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(second.getQuantity()).isEqualTo(3);
        assertThat(first.getUnitPrice()).isEqualByComparingTo("10.00");
        assertThat(second.getUnitPrice()).isEqualByComparingTo("7.50");
        assertThat(first.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(second.getSubtotal()).isEqualByComparingTo("22.50");
        assertThat(order.getTotal()).isEqualByComparingTo("42.50");
    }

    static Stream<Arguments> invalidItemLists() {
        return Stream.of(
                Arguments.of("null list", (Object) null),
                Arguments.of("empty list", List.of()),
                Arguments.of("null line among valid lines",
                        Arrays.asList(item(20L, "10.00", 2), null, item(10L, "7.50", 3))));
    }

    private static UserAccount buyer() {
        return new UserAccount(42L, "aggregate-test-account", "unused-test-hash");
    }

    private static Product product(long id, String price) {
        return new Product(id, "aggregate-test-product-" + id, "https://example.test/product.jpg",
                new BigDecimal(price));
    }

    private static OrderItem item(long productId, String historicalPrice, int quantity) {
        return new OrderItem(product(productId, historicalPrice), quantity, new BigDecimal(historicalPrice));
    }
}
