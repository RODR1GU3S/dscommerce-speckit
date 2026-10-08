package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.OrderResponseDTO;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderItem;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** Unit orchestration contract: mocks do not prove JPA, a transaction proxy or commit. */
class OrderServiceTest {

    private static final Long ACCOUNT_ID = 42L;
    private static final Long FIRST_ID = 91L;
    private static final Long SECOND_ID = 11L;
    private static final AuthenticatedAccountPrincipal PRINCIPAL = new AuthenticatedAccountPrincipal(ACCOUNT_ID);

    private ProductRepository products;
    private OrderRepository orders;
    private UserAccountRepository accounts;
    private OrderService service;
    private Product first;
    private Product second;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        orders = mock(OrderRepository.class);
        accounts = mock(UserAccountRepository.class);
        var account = new UserAccount(ACCOUNT_ID, "unit-buyer", "unused-unit-hash");
        account.getRoles().add(UserRole.CLIENT);
        when(accounts.findByIdWithRoles(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(call -> call.getArgument(0));
        first = product(FIRST_ID, "10.00");
        second = product(SECOND_ID, "7.50");
        var clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00.123456Z"), ZoneOffset.UTC);
        // Direct construction only: no application context, scan change or Spring registration.
        service = new OrderService(products, orders, accounts, clock);
    }

    @ParameterizedTest(name = "current positive price {0}")
    @ValueSource(strings = {"0.01", "7.50", "12345.67"})
    void shouldUseTheCurrentPositivePriceForTheRequestedProduct(String price) {
        first = product(FIRST_ID, price);
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));

        var response = register(request(FIRST_ID, 1));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(FIRST_ID);
        assertThat(response.items().get(0).quantity()).isEqualTo(1);
        assertThat(response.items().get(0).unitPrice()).isEqualTo(price);
        assertThat(response.items().get(0).subtotal()).isEqualTo(price);
        assertThat(response.total()).isEqualTo(price);
        assertThat(capturedOrder().getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal(price));
    }

    @Test
    void shouldReadOnlyTheSubmittedIdsInOneBatchWithoutCatalogOrIndividualLookups() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));

        service.register(twoItems(), PRINCIPAL);

        // Verify only the requested ids, irrespective of the batch argument's iteration order.
        verify(products).findAllById(argThat(ids -> {
            var requested = new ArrayList<Long>();
            ids.forEach(requested::add);
            return requested.size() == 2 && Set.copyOf(requested).equals(Set.of(FIRST_ID, SECOND_ID));
        }));
        verifyNoMoreInteractions(products);
    }

    @Test
    void shouldRestoreRequestOrderWhenTheBatchReturnsProductsInAnotherOrder() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));

        var response = register(twoItems());

        assertThat(response.items()).extracting(item -> item.productId()).containsExactly(FIRST_ID, SECOND_ID);
        assertThat(response.items()).extracting(item -> item.quantity()).containsExactly(2, 3);
        assertThat(response.items()).extracting(item -> item.unitPrice()).containsExactly("10.00", "7.50");
        var aggregate = capturedOrder();
        assertThat(aggregate.getItems()).extracting(item -> item.getProduct().getId())
                .containsExactly(FIRST_ID, SECOND_ID);
        assertThat(aggregate.getItems()).extracting(OrderItem::getQuantity).containsExactly(2, 3);
        assertThat(aggregate.getItems()).extracting(OrderItem::getPosition).containsExactly(0, 1);
    }

    @Test
    void shouldObserveAPositivePriceChangeBeforeTheProductRead() {
        var request = request(FIRST_ID, 2);
        changePrice(first, "12.50");
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));

        var response = register(request);

        assertThat(capturedOrder().getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal("12.50"));
        assertThat(response.items().get(0).unitPrice()).isEqualTo("12.50");
        assertThat(response.items().get(0).subtotal()).isEqualTo("25.00");
        assertThat(response.total()).isEqualTo("25.00");
    }

    @Test
    void shouldKeepTheSnapshotWhenProductPriceChangesAfterTheAggregateWasBuilt() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(call -> {
            // Test-only interleaving after snapshot construction, before response mapping.
            changePrice(first, "99.99");
            changePrice(second, "25.00");
            return call.getArgument(0);
        });

        var response = register(twoItems());

        var aggregate = capturedOrder();
        assertThat(first.getPrice()).isEqualTo(new BigDecimal("99.99"));
        assertThat(second.getPrice()).isEqualTo(new BigDecimal("25.00"));
        assertThat(aggregate.getItems()).extracting(OrderItem::getUnitPrice)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("7.50"));
        assertThat(aggregate.getItems()).extracting(OrderItem::getQuantity).containsExactly(2, 3);
        assertThat(aggregate.getItems()).extracting(OrderItem::getSubtotal)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("22.50"));
        assertThat(aggregate.getTotal()).isEqualTo(new BigDecimal("42.50"));
        assertThat(response.items()).extracting(item -> item.unitPrice()).containsExactly("10.00", "7.50");
        assertThat(response.items()).extracting(item -> item.subtotal()).containsExactly("20.00", "22.50");
        assertThat(response.total()).isEqualTo("42.50");
    }

    @Test
    void shouldNormalizeOnePointZeroZeroZeroExactlyWithoutChangingProduct() {
        first = product(FIRST_ID, "1.000");
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));

        var response = register(request(FIRST_ID, 3));

        var line = capturedOrder().getItems().get(0);
        assertThat(line.getUnitPrice()).isEqualTo(new BigDecimal("1.00"));
        assertThat(line.getUnitPrice().scale()).isEqualTo(2);
        assertThat(line.getSubtotal()).isEqualTo(new BigDecimal("3.00"));
        assertThat(first.getPrice()).isEqualTo(new BigDecimal("1.000"));
        assertThat(response.items().get(0).unitPrice()).isEqualTo("1.00");
        assertThat(response.items().get(0).subtotal()).isEqualTo("3.00");
        assertThat(response.total()).isEqualTo("3.00");
    }

    @Test
    void shouldCalculateTenTimesTwoPlusSevenPointFiveTimesThreeExactly() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));

        var response = register(twoItems());

        var aggregate = capturedOrder();
        assertThat(aggregate.getItems()).extracting(OrderItem::getSubtotal)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("22.50"));
        assertThat(aggregate.getTotal()).isEqualTo(new BigDecimal("42.50"));
        assertThat(response.items()).extracting(item -> item.subtotal()).containsExactly("20.00", "22.50");
        assertThat(response.total()).isEqualTo("42.50");
    }

    @Test
    void shouldBuildANewAggregateAndNewItemsEvenForTheSameRequestObject() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));
        var sameRequest = twoItems();

        var firstResponse = register(sameRequest);
        var secondResponse = register(sameRequest);

        var saved = ArgumentCaptor.forClass(Order.class);
        verify(orders, times(2)).saveAndFlush(saved.capture());
        verifyNoMoreInteractions(orders);
        var firstOrder = saved.getAllValues().get(0);
        var secondOrder = saved.getAllValues().get(1);
        assertThat(secondOrder).isNotSameAs(firstOrder);
        assertThat(firstOrder.getItems()).hasSize(2);
        assertThat(secondOrder.getItems()).hasSize(2);
        assertThat(secondOrder.getItems()).isNotSameAs(firstOrder.getItems());
        for (var index = 0; index < 2; index++) {
            assertThat(secondOrder.getItems().get(index)).isNotSameAs(firstOrder.getItems().get(index));
        }
        assertThat(firstResponse.total()).isEqualTo("42.50");
        assertThat(secondResponse.total()).isEqualTo("42.50");
        verify(products, times(2)).findAllById(anyIterable());
        verifyNoMoreInteractions(products);
        // The mock deliberately does not generate ids; no claim about database identity/commit.
    }

    @Test
    void shouldReadPricesAgainOnAnEqualSubmissionAndKeepTheFirstAggregateHistorical() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));
        var sameRequest = request(FIRST_ID, 2);

        var firstResponse = register(sameRequest);
        changePrice(first, "12.50");
        var secondResponse = register(sameRequest);

        var saved = ArgumentCaptor.forClass(Order.class);
        verify(orders, times(2)).saveAndFlush(saved.capture());
        var firstOrder = saved.getAllValues().get(0);
        var secondOrder = saved.getAllValues().get(1);
        assertThat(secondOrder).isNotSameAs(firstOrder);
        assertThat(firstOrder.getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal("10.00"));
        assertThat(firstOrder.getTotal()).isEqualTo(new BigDecimal("20.00"));
        assertThat(secondOrder.getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal("12.50"));
        assertThat(secondOrder.getTotal()).isEqualTo(new BigDecimal("25.00"));
        assertThat(firstResponse.items().get(0).unitPrice()).isEqualTo("10.00");
        assertThat(firstResponse.total()).isEqualTo("20.00");
        assertThat(secondResponse.items().get(0).unitPrice()).isEqualTo("12.50");
        assertThat(secondResponse.total()).isEqualTo("25.00");
        verify(products, times(2)).findAllById(anyIterable());
        verifyNoMoreInteractions(products);
    }

    private OrderResponseDTO register(OrderRequestDTO request) {
        var response = service.register(request, PRINCIPAL);
        assertThat(response).as("registration must produce a confirmation from a newly built aggregate").isNotNull();
        return response;
    }

    private Order capturedOrder() {
        var saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).saveAndFlush(saved.capture());
        verifyNoMoreInteractions(orders);
        return saved.getValue();
    }

    private static OrderRequestDTO request(Long id, int quantity) {
        return new OrderRequestDTO(List.of(new OrderItemRequestDTO(id, quantity)));
    }

    private static OrderRequestDTO twoItems() {
        return new OrderRequestDTO(List.of(new OrderItemRequestDTO(FIRST_ID, 2), new OrderItemRequestDTO(SECOND_ID, 3)));
    }

    private static Product product(Long id, String price) {
        return new Product(id, "unit-product-" + id, "unit-image", new BigDecimal(price));
    }

    private static void changePrice(Product product, String price) {
        ReflectionTestUtils.setField(product, "price", new BigDecimal(price));
    }
}
