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
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Test
    void shouldRejectAMissingProductBetweenValidLinesWithoutSaving() {
        var missing = 73L;
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));
        var input = new OrderRequestDTO(List.of(new OrderItemRequestDTO(FIRST_ID, 2),
                new OrderItemRequestDTO(missing, 1), new OrderItemRequestDTO(SECOND_ID, 3)));

        assertBusinessRejection("missing-product-between-valid", input,
                ProductNotFoundException.class.getName(), "Product not found");

        verify(products).findAllById(argThat(ids -> submittedIds(ids).equals(List.of(FIRST_ID, missing, SECOND_ID))));
        verifyNoMoreInteractions(products);
    }

    @Test
    void shouldRejectRepeatedProductWithDifferentQuantitiesWithoutConsolidatingOrSaving() {
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));
        var lines = List.of(new OrderItemRequestDTO(FIRST_ID, 2), new OrderItemRequestDTO(SECOND_ID, 3),
                new OrderItemRequestDTO(FIRST_ID, 4));
        var input = new OrderRequestDTO(lines);

        assertAll(
                () -> assertBusinessRejection("duplicate-different-quantities", input,
                        INVALID_ORDER_EXCEPTION, "Duplicate product in order"),
                () -> assertThat(input.items()).containsExactlyElementsOf(lines),
                () -> assertThat(input.items()).extracting(OrderItemRequestDTO::quantity).containsExactly(2, 3, 4));
        // No assumed priority against other business errors; all products/prices/quantities are valid here.
    }

    @ParameterizedTest(name = "invalid current price {0}")
    @MethodSource("invalidCurrentPrices")
    void shouldRejectInvalidPriceBetweenPositiveLinesWithoutSaving(String label, BigDecimal price) {
        // No ORM validation or JDBC: null is the unavailable-price representation defined in research D4.
        second = new Product(SECOND_ID, "unit-invalid-price", "unit-image", price);
        var third = product(73L, "7.50");
        when(products.findAllById(anyIterable())).thenReturn(List.of(third, second, first));
        var input = new OrderRequestDTO(List.of(new OrderItemRequestDTO(FIRST_ID, 2),
                new OrderItemRequestDTO(SECOND_ID, 1), new OrderItemRequestDTO(third.getId(), 3)));

        assertAll(
                () -> assertBusinessRejection(label, input, INVALID_ORDER_EXCEPTION,
                        "Product price is not valid for order"),
                () -> assertThat(second.getPrice()).isEqualTo(price),
                () -> assertThat(first.getPrice()).isEqualTo(new BigDecimal("10.00")),
                () -> assertThat(third.getPrice()).isEqualTo(new BigDecimal("7.50")));
    }

    static Stream<Arguments> invalidCurrentPrices() {
        return Stream.of(Arguments.of("null-unavailable-price", null),
                Arguments.of("zero-price", BigDecimal.ZERO),
                Arguments.of("negative-price", new BigDecimal("-0.01")));
    }

    @ParameterizedTest(name = "exact money boundary {0}, quantity {1}, subtotal {2}")
    @MethodSource("representableMoneyBoundaries")
    void shouldAcceptExactMinimumMaximumAndIntegerMaximumQuantity(String price, int quantity, String subtotal) {
        first = product(FIRST_ID, price);
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));

        var response = register(request(FIRST_ID, quantity));

        var aggregate = capturedOrder();
        assertThat(aggregate.getBuyer().getId()).isEqualTo(ACCOUNT_ID);
        assertThat(aggregate.getItems()).hasSize(1);
        var savedLine = aggregate.getItems().get(0);
        assertThat(savedLine.getQuantity()).isEqualTo(quantity);
        assertThat(savedLine.getUnitPrice()).isEqualTo(new BigDecimal(price));
        assertThat(savedLine.getSubtotal()).isEqualTo(new BigDecimal(subtotal));
        assertThat(aggregate.getTotal()).isEqualTo(new BigDecimal(subtotal));
        assertThat(response.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(FIRST_ID);
        assertThat(response.items().get(0).quantity()).isEqualTo(quantity);
        assertThat(response.items().get(0).unitPrice()).isEqualTo(price);
        assertThat(response.items().get(0).subtotal()).isEqualTo(subtotal);
        assertThat(response.total()).isEqualTo(subtotal);
        assertThat(first.getPrice()).isEqualTo(new BigDecimal(price));
        verify(accounts).findByIdWithRoles(ACCOUNT_ID);
    }

    static Stream<Arguments> representableMoneyBoundaries() {
        return Stream.of(Arguments.of("0.01", 1, "0.01"),
                Arguments.of("99999999999999999.99", 1, "99999999999999999.99"),
                Arguments.of("0.01", Integer.MAX_VALUE, "21474836.47"),
                Arguments.of("10.00", Integer.MAX_VALUE, "21474836470.00"),
                Arguments.of("33333333333333333.33", 3, "99999999999999999.99"));
    }

    @Test
    void shouldAcceptAnExactSumAtTheDecimalCapacity() {
        first = product(FIRST_ID, "99999999999999999.98");
        second = product(SECOND_ID, "0.01");
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));
        var input = new OrderRequestDTO(List.of(new OrderItemRequestDTO(FIRST_ID, 1),
                new OrderItemRequestDTO(SECOND_ID, 1)));

        var response = register(input);

        var aggregate = capturedOrder();
        assertThat(aggregate.getTotal()).isEqualTo(new BigDecimal("99999999999999999.99"));
        assertThat(response.items()).extracting(item -> item.productId()).containsExactly(FIRST_ID, SECOND_ID);
        assertThat(response.items()).extracting(item -> item.subtotal())
                .containsExactly("99999999999999999.98", "0.01");
        assertThat(response.total()).isEqualTo("99999999999999999.99");
    }

    @ParameterizedTest(name = "unrepresentable {0}, current price {1}, quantity {2}")
    @MethodSource("unrepresentableValues")
    void shouldRejectPrecisionLossOrUnitAndSubtotalOverflowBetweenValidLines(
            String label, String price, int quantity) {
        second = product(SECOND_ID, price);
        var third = product(73L, "7.50");
        when(products.findAllById(anyIterable())).thenReturn(List.of(third, second, first));
        var input = new OrderRequestDTO(List.of(new OrderItemRequestDTO(FIRST_ID, 2),
                new OrderItemRequestDTO(SECOND_ID, quantity), new OrderItemRequestDTO(third.getId(), 3)));

        assertAll(
                () -> assertBusinessRejection(label, input, INVALID_ORDER_EXCEPTION,
                        "Order value cannot be represented"),
                () -> assertThat(second.getPrice()).isEqualTo(new BigDecimal(price)),
                () -> assertThat(first.getPrice()).isEqualTo(new BigDecimal("10.00")),
                () -> assertThat(third.getPrice()).isEqualTo(new BigDecimal("7.50")));
    }

    static Stream<Arguments> unrepresentableValues() {
        return Stream.of(Arguments.of("fractional-third-place", "1.001", 1),
                Arguments.of("fractional-rounding-trap", "1.005", 2),
                Arguments.of("fractional-sub-cent", "0.009", 1),
                Arguments.of("fractional-distant-nonzero", "1.0000000000000000000001", 1),
                Arguments.of("unit-price-overflow", "100000000000000000.00", 1),
                Arguments.of("subtotal-overflow", "50000000000000000.00", 2));
    }

    @Test
    void shouldRejectSumOverflowEvenWhenEachPriceAndSubtotalFits() {
        first = product(FIRST_ID, "99999999999999999.99");
        second = product(SECOND_ID, "0.01");
        when(products.findAllById(anyIterable())).thenReturn(List.of(second, first));
        var input = new OrderRequestDTO(List.of(new OrderItemRequestDTO(FIRST_ID, 1),
                new OrderItemRequestDTO(SECOND_ID, 1)));

        assertBusinessRejection("sum-overflow-by-one-cent", input, INVALID_ORDER_EXCEPTION,
                "Order value cannot be represented");
    }

    @ParameterizedTest(name = "invalid principal {0}")
    @MethodSource("invalidPrincipals")
    void shouldRejectInvalidPrincipalBeforeRepositories(String label, AuthenticatedAccountPrincipal principal) {
        var failure = catchThrowable(() -> service.register(twoItems(), principal));
        System.out.printf("T028_GUARD case=%s exception=%s saveCalls=%d%n", label,
                failure == null ? "NONE" : failure.getClass().getSimpleName(), saveCalls());
        assertAll(
                () -> assertThat(failure).isInstanceOf(BadCredentialsException.class)
                        .hasMessage("Authentication required or invalid"),
                () -> verifyNoInteractions(accounts, products, orders));
    }

    static Stream<Arguments> invalidPrincipals() {
        return Stream.of(Arguments.of("no-principal", null),
                Arguments.of("null-id", new AuthenticatedAccountPrincipal(null)),
                Arguments.of("zero-id", new AuthenticatedAccountPrincipal(0L)),
                Arguments.of("negative-id", new AuthenticatedAccountPrincipal(-1L)));
    }

    @ParameterizedTest(name = "current account state {0}")
    @ValueSource(booleans = {false, true})
    void shouldRejectMissingAccountOrEmptyProfilesBeforeReadingProducts(boolean accountExists) {
        when(accounts.findByIdWithRoles(ACCOUNT_ID)).thenReturn(accountExists
                ? Optional.of(account(Set.of())) : Optional.empty());
        var failure = catchThrowable(() -> service.register(twoItems(), PRINCIPAL));
        System.out.printf("T028_GUARD case=%s exception=%s saveCalls=%d%n",
                accountExists ? "no-profiles" : "missing-account",
                failure == null ? "NONE" : failure.getClass().getSimpleName(), saveCalls());
        assertAll(
                () -> assertThat(failure).isInstanceOf(accountExists ? AccessDeniedException.class : BadCredentialsException.class)
                        .hasMessage(accountExists ? "Order registration not permitted" : "Authentication required or invalid"),
                () -> verify(accounts).findByIdWithRoles(ACCOUNT_ID),
                () -> verifyNoMoreInteractions(accounts),
                () -> verifyNoInteractions(products, orders));
    }

    @ParameterizedTest(name = "requery original profiles {0}, account removed {1}")
    @MethodSource("changedAccountScenarios")
    void shouldRequeryIdentityAndProfilesOnEveryCallAndNotSaveAfterRevocation(
            Set<UserRole> initialRoles, boolean removed) {
        when(accounts.findByIdWithRoles(ACCOUNT_ID)).thenReturn(Optional.of(account(initialRoles)),
                removed ? Optional.empty() : Optional.of(account(Set.of())));
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));
        var input = request(FIRST_ID, 2);
        var firstResponse = register(input);
        var previousAggregate = capturedOrder();

        var failure = catchThrowable(() -> service.register(input, PRINCIPAL));

        System.out.printf("T028_GUARD case=%s_%s exception=%s saveCalls=%d baselineSaveCalls=1%n",
                removed ? "removed-between-calls" : "revoked-between-calls", initialRoles,
                failure == null ? "NONE" : failure.getClass().getSimpleName(), saveCalls());
        assertAll(
                () -> assertThat(failure).isInstanceOf(removed ? BadCredentialsException.class : AccessDeniedException.class)
                        .hasMessage(removed ? "Authentication required or invalid" : "Order registration not permitted"),
                () -> verify(accounts, times(2)).findByIdWithRoles(ACCOUNT_ID),
                () -> verifyNoMoreInteractions(accounts),
                () -> verify(products).findAllById(anyIterable()),
                () -> verifyNoMoreInteractions(products),
                () -> verify(orders).saveAndFlush(any(Order.class)),
                () -> verifyNoMoreInteractions(orders),
                () -> assertThat(firstResponse.accountId()).isEqualTo(ACCOUNT_ID),
                () -> assertThat(firstResponse.total()).isEqualTo("20.00"),
                () -> assertThat(previousAggregate.getTotal()).isEqualTo(new BigDecimal("20.00")),
                () -> assertThat(previousAggregate.getItems().get(0).getUnitPrice()).isEqualTo(new BigDecimal("10.00")));
    }

    static Stream<Arguments> changedAccountScenarios() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN))
                .flatMap(roles -> Stream.of(false, true).map(removed -> Arguments.of(roles, removed)));
    }

    @Test
    void shouldResolveTheIdentityPassedToEachCallWithoutReusingPreviousBuyer() {
        var otherId = 43L;
        var other = new UserAccount(otherId, "other-unit-buyer", "unused-unit-hash");
        other.getRoles().add(UserRole.ADMIN);
        when(accounts.findByIdWithRoles(otherId)).thenReturn(Optional.of(other));
        when(products.findAllById(anyIterable())).thenReturn(List.of(first));
        var input = request(FIRST_ID, 1);

        var firstResponse = register(input);
        var otherResponse = service.register(input, new AuthenticatedAccountPrincipal(otherId));

        assertThat(firstResponse.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(otherResponse.accountId()).isEqualTo(otherId);
        verify(accounts).findByIdWithRoles(ACCOUNT_ID);
        verify(accounts).findByIdWithRoles(otherId);
        verifyNoMoreInteractions(accounts);
        var saved = ArgumentCaptor.forClass(Order.class);
        verify(orders, times(2)).saveAndFlush(saved.capture());
        verifyNoMoreInteractions(orders);
        assertThat(saved.getAllValues()).extracting(order -> order.getBuyer().getId()).containsExactly(ACCOUNT_ID, otherId);
        assertThat(saved.getAllValues().get(1)).isNotSameAs(saved.getAllValues().get(0));
    }

    // T034 defines this type; a name assertion avoids importing/creating an absent production class.
    private static final String INVALID_ORDER_EXCEPTION =
            "com.devsuperior.dscommerce.services.exceptions.InvalidOrderException";

    private void assertBusinessRejection(String label, OrderRequestDTO input, String expectedType, String message) {
        var failure = catchThrowable(() -> service.register(input, PRINCIPAL));
        System.out.printf("T028_REJECTION case=%s exception=%s saveCalls=%d%n", label,
                failure == null ? "NONE" : failure.getClass().getSimpleName(), saveCalls());
        assertAll(
                () -> assertThat(failure).as("request must be rejected by the service").isNotNull(),
                () -> { if (failure != null) assertThat(failure.getClass().getName()).isEqualTo(expectedType); },
                () -> { if (failure != null) assertThat(failure.getMessage()).isEqualTo(message); },
                () -> verifyNoInteractions(orders),
                () -> verify(accounts).findByIdWithRoles(ACCOUNT_ID),
                () -> verifyNoMoreInteractions(accounts));
    }

    private long saveCalls() {
        return mockingDetails(orders).getInvocations().stream()
                .filter(call -> call.getMethod().getName().startsWith("save")).count();
    }

    private static List<Long> submittedIds(Iterable<Long> ids) {
        var values = new ArrayList<Long>();
        ids.forEach(values::add);
        return values;
    }

    private static UserAccount account(Set<UserRole> roles) {
        var current = new UserAccount(ACCOUNT_ID, "unit-current-buyer", "unused-unit-hash");
        current.getRoles().addAll(roles);
        return current;
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
