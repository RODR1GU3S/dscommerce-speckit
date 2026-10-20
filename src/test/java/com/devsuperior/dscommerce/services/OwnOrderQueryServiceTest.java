package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.dto.OwnOrderDetailDTO;
import com.devsuperior.dscommerce.dto.OwnOrderItemDTO;
import com.devsuperior.dscommerce.dto.OwnOrderPageRequestDTO;
import com.devsuperior.dscommerce.dto.OwnOrderSummaryDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderItem;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.devsuperior.dscommerce.services.exceptions.OwnOrderNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real service/JPA transactions for success; local mocks only for controlled impossible/corrupt results. */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
                "feature007.own-order-service-test-context=list",
                "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnOrderQueryServiceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private OwnOrderQueryService service;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PlatformTransactionManager transactions;
    private String stableUrl;
    private int realReads;
    private int detailReads;
    private int detailRejectedReads;

    @BeforeAll
    void verifyIsolatedContext() {
        stableUrl = context.getEnvironment().getRequiredProperty("spring.datasource.url");
        assertThat(stableUrl).startsWith("jdbc:h2:mem:admin-product-test-");
        assertThat(context.getEnvironment().getRequiredProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(context.getEnvironment().getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(context.getEnvironment().getRequiredProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(context.getEnvironment().getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
        assertEmpty();
        System.out.println("T014_CONTEXT database=" + stableUrl.split(";", 2)[0] + " osiv=false");
    }

    @AfterEach
    void verifyCleanupAndNoCallerTransaction() {
        noTransaction();
        assertEmpty();
        assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url")).isEqualTo(stableUrl);
        sql.clear();
    }

    @AfterAll
    void reportRealReads() {
        System.out.println("T014_COUNTS realServiceReadWindows=" + realReads
                + " databaseContexts=1 mockMvcDispatches=0 realHttpRequests=0");
    }

    @Test
    void listRealPagesMapHistoricalTotalsAndKeepTimestampIdOrderAfterReturn() throws Exception {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var scenario = fixture.preparePaginationScenario();
            var visited = new ArrayList<Long>();
            for (int page = 0; page < 3; page++) {
                var expected = scenario.expectedClientOrders().subList(page * 2, Math.min(page * 2 + 2, 5));
                var result = realList(scenario.accounts().client().id(), page, 2);
                assertPage(result, expected, page, 2, 5, 3);
                visited.addAll(result.content().stream().map(OwnOrderSummaryDTO::id).toList());
            }
            assertThat(visited).containsExactlyElementsOf(scenario.expectedClientOrders().stream()
                    .map(OrderManifest::id).toList()).doesNotHaveDuplicates();
        }
    }

    @Test
    void listRealSuccessivePrincipalsIncludeAdminAloneAndBothWithoutSharingOwner() throws Exception {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            for (var expected : List.of(s.expectedClientOrders(),
                    List.of(s.insertedOrders().get(5), s.insertedOrders().get(1)),
                    List.of(s.insertedOrders().get(3)), List.of(s.insertedOrders().get(7)), s.expectedClientOrders())) {
                assertPage(realList(expected.getFirst().buyerId(), 0, 12), expected, 0, 12, expected.size(), 1);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"empty,0,12,0,0", "empty,3,2,0,0", "client,3,2,5,3", "client,2147483647,1,5,5"})
    void listRealEmptyPagesKeepRequestedMetadataAndSkipBatch(String identity, int page, int size,
                                                              long total, int pages) throws Exception {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            long owner = identity.equals("empty") ? s.emptyBuyer().id() : s.accounts().client().id();
            assertPage(realList(owner, page, size), List.of(), page, size, total, pages);
            assertThat(sql.events()).noneSatisfy(event -> assertThat(event.sql()).contains(" in ("));
        }
    }

    @Test
    void listRealGuardRechecksCurrentScalarRolesBeforeOrderQueries() {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var id = s.accounts().client().id();
            // The authentication still claims ADMIN; current database permission must win.
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", id);
            assertRealFailure(() -> service.list(request(0, 12), authentication(id)), AccessDeniedException.class);
            assertRealFailure(() -> service.list(request(0, 12), authentication(Long.MAX_VALUE)), BadCredentialsException.class);
            assertRealFailure(() -> service.list(request(0, 12), null), BadCredentialsException.class);
        }
    }

    @Test
    void listRealRootWithoutItemsFailsWithoutRepairOrPartialSuccess() {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var oldest = s.expectedClientOrders().getLast();
            assertThat(jdbc.update("delete from tb_order_item where order_id = ?", oldest.id())).isEqualTo(2);
            assertRealFailure(() -> service.list(request(0, 12), authentication(oldest.buyerId())), IllegalStateException.class);
        }
    }

    @Test
    void listReordersShuffledBatchAndCompletesMappingInsideReadOnlySpringTransaction() {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        var auth = authentication(42);
        when(access.requireAccountId(auth)).thenAnswer(call -> { readOnlyTransaction(); return 42L; });
        var first = spy(order(8, 42, 2, "10.00"));
        var second = spy(order(3, 42, 3, "7.50"));
        doAnswer(call -> { readOnlyTransaction(); return call.callRealMethod(); }).when(first).getTotal();
        doAnswer(call -> { readOnlyTransaction(); return call.callRealMethod(); }).when(second).getTotal();
        when(orders.findOwnOrderIds(eq(42L), any())).thenAnswer(call -> {
            readOnlyTransaction();
            return new PageImpl<>(List.of(8L, 3L), PageRequest.of(0, 2), 5);
        });
        when(orders.findOwnOrdersWithItemsByIds(42, List.of(8L, 3L))).thenAnswer(call -> {
            readOnlyTransaction(); return List.of(second, first);
        });
        var proxy = new ProxyFactory(new OwnOrderQueryService(access, orders));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        noTransaction();
        var result = ((OwnOrderQueryService) proxy.getProxy()).list(request(0, 2), auth);
        noTransaction();
        assertThat(result.content()).extracting(OwnOrderSummaryDTO::id).containsExactly(8L, 3L);
        assertThat(result.content()).extracting(OwnOrderSummaryDTO::total).containsExactly("20.00", "22.50");
        var sequence = inOrder(access, orders);
        sequence.verify(access).requireAccountId(auth);
        sequence.verify(orders).findOwnOrderIds(42, PageRequest.of(0, 2));
        sequence.verify(orders).findOwnOrdersWithItemsByIds(42, List.of(8L, 3L));
        verifyNoMoreInteractions(access, orders);
    }

    @ParameterizedTest
    @CsvSource({"0,12,0,0", "3,2,5,3", "0,50,2147483648,42949673",
            "0,1,2147483647,2147483647"})
    void listComputesLongMetadataWithoutUsingSaturatingPageTotalPages(int page, int size, long total, int pages) {
        var orders = mock(OrderRepository.class);
        var access = mock(OwnOrderQueryAccessService.class);
        when(access.requireAccountId(any())).thenReturn(42L);
        var source = spy(new PageImpl<Long>(List.of(), PageRequest.of(page, size), total));
        when(orders.findOwnOrderIds(42, PageRequest.of(page, size))).thenReturn(source);
        var result = new OwnOrderQueryService(access, orders).list(request(page, size), authentication(42));
        assertPage(result, List.of(), page, size, total, pages);
        verify(source, never()).getTotalPages();
        verify(orders).findOwnOrderIds(42, PageRequest.of(page, size));
        verifyNoMoreInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483648L, Long.MAX_VALUE})
    void listUnrepresentableTotalPagesFailsBeforeBatchWithoutLongOverflow(long total) {
        var orders = mock(OrderRepository.class);
        var access = mock(OwnOrderQueryAccessService.class);
        when(access.requireAccountId(any())).thenReturn(42L);
        when(orders.findOwnOrderIds(eq(42L), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), total));
        assertThatThrownBy(() -> new OwnOrderQueryService(access, orders).list(request(0, 1), authentication(42)))
                .isInstanceOf(IllegalStateException.class);
        verify(orders).findOwnOrderIds(42, PageRequest.of(0, 1));
        verifyNoMoreInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "extra", "duplicate", "foreign", "emptyItems", "nullDate", "nullStatus", "duplicateIds"})
    void listInconsistentBatchFailsAsInternalErrorInsteadOfPartialContent(String corruption) {
        var orders = mock(OrderRepository.class);
        var access = mock(OwnOrderQueryAccessService.class);
        when(access.requireAccountId(any())).thenReturn(42L);
        var ids = corruption.equals("duplicateIds") ? List.of(8L, 8L) : List.of(8L, 3L);
        when(orders.findOwnOrderIds(eq(42L), any())).thenReturn(new PageImpl<>(ids, PageRequest.of(0, 2), 5));
        var first = order(8, 42, 1, "10.00");
        var second = order(3, 42, 1, "7.50");
        var batch = switch (corruption) {
            case "missing" -> List.of(first);
            case "extra" -> List.of(first, second, order(99, 42, 1, "1.00"));
            case "duplicate" -> List.of(first, first);
            case "foreign" -> List.of(first, order(3, 99, 1, "7.50"));
            default -> List.of(first, second);
        };
        if (corruption.equals("emptyItems")) { ReflectionTestUtils.setField(second, "items", new ArrayList<>()); }
        if (corruption.equals("nullDate")) { ReflectionTestUtils.setField(second, "createdAt", null); }
        if (corruption.equals("nullStatus")) { ReflectionTestUtils.setField(second, "status", null); }
        lenient().when(orders.findOwnOrdersWithItemsByIds(42, ids)).thenReturn(batch);
        assertThatThrownBy(() -> new OwnOrderQueryService(access, orders).list(request(0, 2), authentication(42)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({"1,0.01,0.01", "1,9007199254740993.01,9007199254740993.01",
            "1,99999999999999999.99,99999999999999999.99", "2,1E+1,20.00", "3,7.500,22.50"})
    void listUsesExactRegisteredBigDecimalMoneyWithoutCatalogRepricing(int quantity, String price, String expected) {
        assertThat(mockList(order(8, 42, quantity, price)).content().getFirst().total()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,-1.00", "1,0.001", "1,100000000000000000.00", "0,1.00",
            "-1,-1.00", "2,99999999999999999.99"})
    void listRejectsIncoherentMoneyAndQuantityWithoutRounding(int quantity, String price) {
        assertThatThrownBy(() -> mockList(order(8, 42, quantity, price))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listRejectsTotalBeyondCapacityEvenWhenEachItemAndSubtotalFits() {
        var order = order(8, 42, 1, "50000000000000000.00");
        var product = new Product(9L, "same current name", "image", BigDecimal.ZERO);
        var item = new OrderItem(product, 1, new BigDecimal("50000000000000000.00"));
        ReflectionTestUtils.setField(item, "order", order);
        ReflectionTestUtils.setField(item, "position", 1);
        ReflectionTestUtils.setField(order, "items", new ArrayList<>(List.of(order.getItems().getFirst(), item)));
        assertThatThrownBy(() -> mockList(order)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"guard", "ids", "batch"})
    void listReadFailuresPropagateWithoutEmptyOrAuthenticationSubstitution(String stage) {
        var orders = mock(OrderRepository.class);
        var access = mock(OwnOrderQueryAccessService.class);
        RuntimeException cause = stage.equals("guard")
                ? new OrderAccountResolutionException("Account resolution could not be completed", new IllegalStateException())
                : new DataAccessResourceFailureException("controlled read failure");
        if (stage.equals("guard")) { when(access.requireAccountId(any())).thenThrow(cause); }
        else {
            when(access.requireAccountId(any())).thenReturn(42L);
            if (stage.equals("ids")) { when(orders.findOwnOrderIds(eq(42L), any())).thenThrow(cause); }
            else {
                when(orders.findOwnOrderIds(eq(42L), any())).thenReturn(new PageImpl<>(List.of(8L)));
                when(orders.findOwnOrdersWithItemsByIds(42, List.of(8L))).thenThrow(cause);
            }
        }
        assertThatThrownBy(() -> new OwnOrderQueryService(access, orders).list(request(0, 12), authentication(42)))
                .isSameAs(cause);
        if (stage.equals("guard")) { verifyNoInteractions(orders); }
    }

    @AfterAll
    void reportDetailReads() {
        System.out.println("T021_COUNTS detailSuccessWindows=" + detailReads + " detailRejectedWindows="
                + detailRejectedReads + " mockMvcDispatches=0 realHttpRequests=0 databaseContexts=1");
    }

    @Test
    void detailRealMapsEachOwnersCompleteHistoricalOrderAndSerializesAfterReturn() throws Exception {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var scenario = fixture.preparePaginationScenario();
            for (var expected : scenario.insertedOrders()) {
                assertDetailDto(realDetail(expected.buyerId(), expected.id()), expected,
                        expected.items().stream().map(item -> item.productName()).toList());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"renamed", "short", "long"})
    void detailRealPreservesCurrentNamesAndHistoricalMoneyAfterCatalogChanges(String kind) throws Exception {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var scenario = fixture.preparePaginationScenario();
            var expected = scenario.expectedClientOrders().getLast();
            var before = realDetail(expected.buyerId(), expected.id());
            var oldNames = expected.items().stream().map(item -> item.productName()).toList();
            assertDetailDto(before, expected, oldNames);
            var name = kind.equals("short") ? "Á" : kind.equals("long") ? " " + "Café ".repeat(17) + " " : " Nome atual B ";
            var names = List.of(name, kind.equals("renamed") ? " Nome atual A " : name);
            var setup = new TransactionTemplate(transactions);
            setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            setup.executeWithoutResult(status -> {
                assertThat(jdbc.update("update tb_product set name = ?, price = ? where id = ?",
                        names.get(0), new BigDecimal("37.00"), scenario.productB().id())).isEqualTo(1);
                assertThat(jdbc.update("update tb_product set name = ?, price = ? where id = ?",
                        names.get(1), BigDecimal.ZERO, scenario.productA().id())).isEqualTo(1);
                assertThat(jdbc.update("delete from tb_product_category where product_id in (?, ?)",
                        scenario.productA().id(), scenario.productB().id())).isEqualTo(2);
            });
            assertDetailDto(realDetail(expected.buyerId(), expected.id()), expected, names);
            assertDetailDto(before, expected, oldNames);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "admin"})
    void detailRealAbsentAndForeignOrdersHaveTheSameBusinessFailureIncludingAdmin(String identity) {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var owner = identity.equals("client") ? s.accounts().client().id() : s.accounts().admin().id();
            var foreign = identity.equals("client") ? s.insertedOrders().get(1) : s.expectedClientOrders().getLast();
            detailRealFailure(foreign.id(), authentication(owner), OwnOrderNotFoundException.class, true);
            detailRealFailure(Long.MAX_VALUE, authentication(owner), OwnOrderNotFoundException.class, true);
        }
    }

    @Test
    void detailRealGuardRechecksCurrentRolesBeforeReadingAnyOrder() {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var owner = s.accounts().client().id();
            var id = s.expectedClientOrders().getLast().id();
            assertThat(jdbc.update("delete from tb_user_account_role where user_account_id = ?", owner)).isEqualTo(1);
            detailRealFailure(id, authentication(owner), AccessDeniedException.class, false);
            detailRealFailure(id, authentication(Long.MAX_VALUE), BadCredentialsException.class, false);
            detailRealFailure(id, null, BadCredentialsException.class, false);
        }
    }

    @Test
    void detailRealRootWithoutItemsIsAnInternalFailureWithoutRepair() {
        try (var fixture = new OwnOrderQueryTestFixture(context)) {
            var s = fixture.preparePaginationScenario();
            var expected = s.expectedClientOrders().getLast();
            assertThat(jdbc.update("delete from tb_order_item where order_id = ?", expected.id())).isEqualTo(2);
            detailRealFailure(expected.id(), authentication(expected.buyerId()), IllegalStateException.class, true);
        }
    }

    @Test
    void detailCompletesGuardFetchCalculationAndNameMappingInsideReadOnlyTransaction() {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        var auth = authentication(42);
        var root = spy(order(8, 42, 2, "10.00"));
        var product = spy(root.getItems().getFirst().getProduct());
        ReflectionTestUtils.setField(root.getItems().getFirst(), "product", product);
        when(access.requireAccountId(auth)).thenAnswer(call -> { readOnlyTransaction(); return 42L; });
        when(orders.findOwnOrderWithItemsById(42, 8)).thenAnswer(call -> {
            readOnlyTransaction(); return Optional.of(root);
        });
        doAnswer(call -> { readOnlyTransaction(); return call.callRealMethod(); }).when(root).getTotal();
        doAnswer(call -> { readOnlyTransaction(); return call.callRealMethod(); }).when(product).getName();
        doThrow(new AssertionError("Current catalog price is not historical money")).when(product).getPrice();
        doThrow(new AssertionError("Current categories are not query validation")).when(product).getCategories();
        var proxy = new ProxyFactory(new OwnOrderQueryService(access, orders));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        noTransaction();
        var result = ((OwnOrderQueryService) proxy.getProxy()).detail(8, auth);
        noTransaction();
        assertThat(result.total()).isEqualTo("20.00");
        assertThat(result.items()).containsExactly(new OwnOrderItemDTO(7L, "x", 2, "10.00", "20.00"));
        var sequence = inOrder(access, orders);
        sequence.verify(access).requireAccountId(auth);
        sequence.verify(orders).findOwnOrderWithItemsById(42, 8);
        verifyNoMoreInteractions(access, orders);
        verify(product, never()).getPrice();
        verify(product, never()).getCategories();
    }

    @Test
    void detailAbsentResultUsesOnlyTheGuardAndOneOwnerRestrictedLookup() {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        var auth = authentication(42);
        when(access.requireAccountId(auth)).thenReturn(42L);
        when(orders.findOwnOrderWithItemsById(42, 8)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new OwnOrderQueryService(access, orders).detail(8, auth))
                .isInstanceOf(OwnOrderNotFoundException.class).hasMessage("Order not found");
        var sequence = inOrder(access, orders);
        sequence.verify(access).requireAccountId(auth);
        sequence.verify(orders).findOwnOrderWithItemsById(42, 8);
        verifyNoMoreInteractions(access, orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrongId", "foreign", "nullBuyer", "nullId", "emptyItems", "nullDate", "nullStatus",
            "nullProductName", "duplicateProduct", "nullItem", "nullProduct", "nullQuantity", "nullPrice",
            "wrongPosition", "foreignLineOrder"})
    void detailCorruptRootsFailInternallyInsteadOfNotFoundOrPartialContent(String corruption) {
        var root = order(8, 42, 1, "10.00");
        var item = root.getItems().getFirst();
        switch (corruption) {
            case "wrongId" -> ReflectionTestUtils.setField(root, "id", 9L);
            case "foreign" -> ReflectionTestUtils.setField(root, "buyer", new UserAccount(99L, "test-only", "unused"));
            case "nullBuyer" -> ReflectionTestUtils.setField(root, "buyer", null);
            case "nullId" -> ReflectionTestUtils.setField(root, "id", null);
            case "emptyItems" -> ReflectionTestUtils.setField(root, "items", new ArrayList<>());
            case "nullDate" -> ReflectionTestUtils.setField(root, "createdAt", null);
            case "nullStatus" -> ReflectionTestUtils.setField(root, "status", null);
            case "nullProductName" -> ReflectionTestUtils.setField(item.getProduct(), "name", null);
            case "duplicateProduct" -> {
                var duplicate = new OrderItem(item.getProduct(), 1, new BigDecimal("10.00"));
                ReflectionTestUtils.setField(duplicate, "order", root);
                ReflectionTestUtils.setField(duplicate, "position", 1);
                ReflectionTestUtils.setField(root, "items", new ArrayList<>(List.of(item, duplicate)));
            }
            case "nullItem" -> {
                var items = new ArrayList<OrderItem>(); items.add(null);
                ReflectionTestUtils.setField(root, "items", items);
            }
            case "nullProduct" -> ReflectionTestUtils.setField(item, "product", null);
            case "nullQuantity" -> ReflectionTestUtils.setField(item, "quantity", null);
            case "nullPrice" -> ReflectionTestUtils.setField(item, "unitPrice", null);
            case "wrongPosition" -> ReflectionTestUtils.setField(item, "position", 1);
            case "foreignLineOrder" -> ReflectionTestUtils.setField(item, "order", order(9, 42, 1, "1.00"));
            default -> throw new AssertionError("Unknown corruption case");
        }
        assertThatThrownBy(() -> mockDetail(root)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @CsvSource({"1,0.01,0.01,0.01", "1,9007199254740993.01,9007199254740993.01,9007199254740993.01",
            "1,99999999999999999.99,99999999999999999.99,99999999999999999.99",
            "2,1E+1,10.00,20.00", "3,7.500,7.50,22.50"})
    void detailUsesTheSharedHistoricalCalculationAndExactMoneyWithoutCatalogRepricing(
            int quantity, String price, String unitPrice, String total) {
        var root = order(8, 42, quantity, price);
        var result = mockDetail(root);
        assertThat(result.total()).isEqualTo(total);
        assertThat(result.items().getFirst().unitPrice()).isEqualTo(unitPrice);
        assertThat(result.items().getFirst().subtotal()).isEqualTo(total);
        assertThat(result.total()).isEqualTo(mockList(root).content().getFirst().total());
    }

    @ParameterizedTest
    @CsvSource({"1,0", "1,-1.00", "1,0.001", "1,100000000000000000.00", "0,1.00",
            "-1,-1.00", "2,99999999999999999.99"})
    void detailRejectsInvalidHistoricalMoneyAndQuantitiesWithoutRounding(int quantity, String price) {
        assertThatThrownBy(() -> mockDetail(order(8, 42, quantity, price))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detailRejectsTotalBeyondCapacityEvenWhenEachLineFits() {
        var root = order(8, 42, 1, "50000000000000000.00");
        var other = new OrderItem(new Product(9L, "x", "image", BigDecimal.ZERO), 1,
                new BigDecimal("50000000000000000.00"));
        ReflectionTestUtils.setField(other, "order", root);
        ReflectionTestUtils.setField(other, "position", 1);
        ReflectionTestUtils.setField(root, "items", new ArrayList<>(List.of(root.getItems().getFirst(), other)));
        assertThatThrownBy(() -> mockDetail(root)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"guard", "fetch", "calculation", "mapping"})
    void detailReadFailuresPropagateWithoutNotFoundOrAuthenticationSubstitution(String stage) {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        var cause = stage.equals("guard")
                ? new OrderAccountResolutionException("Account resolution could not be completed", new IllegalStateException())
                : new DataAccessResourceFailureException("controlled detail failure");
        if (stage.equals("guard")) { when(access.requireAccountId(any())).thenThrow(cause); }
        else {
            when(access.requireAccountId(any())).thenReturn(42L);
            if (stage.equals("fetch")) { when(orders.findOwnOrderWithItemsById(42, 8)).thenThrow(cause); }
            else {
                var root = spy(order(8, 42, 1, "10.00"));
                if (stage.equals("calculation")) { doThrow(cause).when(root).getTotal(); }
                else {
                    var product = spy(root.getItems().getFirst().getProduct());
                    ReflectionTestUtils.setField(root.getItems().getFirst(), "product", product);
                    doThrow(cause).when(product).getName();
                }
                when(orders.findOwnOrderWithItemsById(42, 8)).thenReturn(Optional.of(root));
            }
        }
        assertThatThrownBy(() -> new OwnOrderQueryService(access, orders).detail(8, authentication(42))).isSameAs(cause);
        if (stage.equals("guard")) { verifyNoInteractions(orders); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"authentication", "permission"})
    void detailGuardRejectionsStopBeforeTheRepository(String kind) {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        RuntimeException cause = kind.equals("authentication") ? new BadCredentialsException("controlled auth")
                : new AccessDeniedException("controlled permission");
        when(access.requireAccountId(any())).thenThrow(cause);
        assertThatThrownBy(() -> new OwnOrderQueryService(access, orders).detail(8, authentication(42))).isSameAs(cause);
        verifyNoInteractions(orders);
    }

    private OwnOrderDetailDTO realDetail(long owner, long id) throws Exception {
        noTransaction();
        var observation = database.observeRead(sql, () -> service.detail(id, authentication(owner)));
        noTransaction();
        observation.assertNoWrites();
        assertThat(observation.events()).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        assertThat(observation.events().getFirst().sql()).contains("tb_user_account");
        var fetches = observation.events().stream().filter(event -> event.sql().contains("from tb_order ")).toList();
        assertThat(fetches).hasSize(1).allSatisfy(event -> assertThat(event.sql())
                .contains("buyer_id", "left join tb_order_item", "left join tb_product")
                .doesNotContain("offset", "fetch first", "tb_category"));
        var beforeSerialization = sql.events();
        var json = mapper.readTree(mapper.writeValueAsBytes(observation.result()));
        assertThat(json.get("items").size()).isEqualTo(observation.result().items().size());
        assertThat(sql.events()).containsExactlyElementsOf(beforeSerialization);
        detailReads++;
        System.out.println("T021_DETAIL_READ statements=" + observation.events().size()
                + " orderFetches=1 snapshotEqual=true callerTransaction=false serializedAfterReturn=true");
        return observation.result();
    }

    private void detailRealFailure(long id, Authentication auth, Class<? extends Throwable> type, boolean orderRead) {
        noTransaction();
        var before = database.snapshot(); sql.clear();
        var failure = catchThrowable(() -> service.detail(id, auth));
        assertThat(failure).isInstanceOf(type);
        if (type == OwnOrderNotFoundException.class) { assertThat(failure).hasMessage("Order not found"); }
        noTransaction();
        OwnOrderQueryDatabaseProbe.assertUnchanged(before, database.snapshot());
        assertThat(sql.events()).allSatisfy(event -> {
            assertThat(event.sql()).startsWith("select");
            assertThat(event.transactionActive()).isTrue();
        });
        assertThat(sql.events().stream().filter(event -> event.sql().contains("from tb_order ")).toList())
                .hasSize(orderRead ? 1 : 0);
        detailRejectedReads++;
        System.out.println("T021_DETAIL_REJECT statements=" + sql.events().size() + " orderFetches="
                + (orderRead ? 1 : 0) + " snapshotEqual=true callerTransaction=false failure=" + type.getSimpleName());
    }

    private static void assertDetailDto(OwnOrderDetailDTO actual, OrderManifest expected, List<String> names) {
        assertThat(actual.id()).isEqualTo(expected.id());
        assertThat(actual.createdAt()).isEqualTo(expected.createdAt());
        assertThat(actual.status().name()).isEqualTo(expected.status());
        assertThat(actual.total()).isEqualTo(expected.total());
        var items = new ArrayList<OwnOrderItemDTO>();
        for (int index = 0; index < expected.items().size(); index++) {
            var line = expected.items().get(index);
            items.add(new OwnOrderItemDTO(line.productId(), names.get(index), line.quantity(), line.unitPrice(), line.subtotal()));
        }
        assertThat(actual.items()).containsExactlyElementsOf(items);
    }

    private static OwnOrderDetailDTO mockDetail(Order root) {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        when(access.requireAccountId(any())).thenReturn(42L);
        when(orders.findOwnOrderWithItemsById(42, 8)).thenReturn(Optional.of(root));
        var result = new OwnOrderQueryService(access, orders).detail(8, authentication(42));
        verify(orders).findOwnOrderWithItemsById(42, 8);
        verifyNoMoreInteractions(orders);
        return result;
    }

    private PageResponseDTO<OwnOrderSummaryDTO> realList(long owner, int page, int size) throws Exception {
        noTransaction();
        var observation = database.observeRead(sql, () -> service.list(request(page, size), authentication(owner)));
        noTransaction();
        observation.assertNoWrites();
        assertThat(observation.events()).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        assertThat(observation.events().getFirst().sql()).contains("tb_user_account");
        var orderStatements = observation.events().stream().filter(event -> event.sql().contains("from tb_order ")).toList();
        assertThat(orderStatements).isNotEmpty().allSatisfy(event -> assertThat(event.sql()).contains("buyer_id"));
        var batches = orderStatements.stream().filter(event -> event.sql().contains("join tb_order_item")).toList();
        assertThat(batches).hasSize(observation.result().content().isEmpty() ? 0 : 1);
        assertThat(batches).allSatisfy(event -> assertThat(event.sql()).doesNotContain("fetch first", "offset", "tb_category"));
        var beforeSerialization = sql.events();
        var json = mapper.readTree(mapper.writeValueAsString(observation.result()));
        assertThat(json.get("content").size()).isEqualTo(observation.result().content().size());
        assertThat(sql.events()).containsExactlyElementsOf(beforeSerialization);
        realReads++;
        System.out.println("T014_READ statements=" + observation.events().size() + " batchFetches=" + batches.size()
                + " snapshotEqual=true callerTransaction=false serializedAfterReturn=true");
        return observation.result();
    }

    private void assertRealFailure(Runnable operation, Class<? extends Throwable> type) {
        noTransaction();
        var before = database.snapshot(); sql.clear();
        assertThatThrownBy(operation::run).isInstanceOf(type);
        noTransaction();
        OwnOrderQueryDatabaseProbe.assertUnchanged(before, database.snapshot());
        assertThat(sql.events()).allSatisfy(event -> assertThat(event.sql()).startsWith("select"));
        if (type != IllegalStateException.class) {
            assertThat(sql.events()).noneSatisfy(event -> assertThat(event.sql()).contains("from tb_order "));
        }
    }

    private static void assertPage(PageResponseDTO<OwnOrderSummaryDTO> actual, List<OrderManifest> expected,
                                   int page, int size, long total, int pages) {
        assertThat(actual.page()).isEqualTo(page); assertThat(actual.size()).isEqualTo(size);
        assertThat(actual.totalElements()).isEqualTo(total); assertThat(actual.totalPages()).isEqualTo(pages);
        assertThat(actual.content()).containsExactlyElementsOf(expected.stream().map(order ->
                new OwnOrderSummaryDTO(order.id(), order.createdAt(),
                        com.devsuperior.dscommerce.entities.OrderStatus.valueOf(order.status()), order.total())).toList());
    }

    private static PageResponseDTO<OwnOrderSummaryDTO> mockList(Order order) {
        var access = mock(OwnOrderQueryAccessService.class);
        var orders = mock(OrderRepository.class);
        when(access.requireAccountId(any())).thenReturn(42L);
        when(orders.findOwnOrderIds(eq(42L), any())).thenReturn(new PageImpl<>(List.of(8L)));
        when(orders.findOwnOrdersWithItemsByIds(42, List.of(8L))).thenReturn(List.of(order));
        var result = new OwnOrderQueryService(access, orders).list(request(0, 12), authentication(42));
        verify(orders).findOwnOrderIds(42, PageRequest.of(0, 12));
        verify(orders).findOwnOrdersWithItemsByIds(42, List.of(8L));
        verifyNoMoreInteractions(orders);
        return result;
    }

    private static Order order(long id, long owner, int quantity, String price) {
        var buyer = new UserAccount(owner, "test-only", "unused");
        var product = new Product(7L, "x", "image", BigDecimal.ZERO);
        var order = new Order(buyer, Instant.parse("2026-10-19T12:00:00.123Z"),
                List.of(new OrderItem(product, quantity, new BigDecimal(price))));
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }

    private static OwnOrderPageRequestDTO request(int page, int size) {
        var request = new OwnOrderPageRequestDTO(); request.setPage(page); request.setSize(size); return request;
    }

    private static Authentication authentication(long id) {
        return UsernamePasswordAuthenticationToken.authenticated(new AuthenticatedAccountPrincipal(id), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private void assertEmpty() {
        var snapshot = database.snapshot();
        assertThat(snapshot.accountIds()).isEmpty(); assertThat(snapshot.roles()).isEmpty();
        assertThat(snapshot.data().orders()).isEmpty(); assertThat(snapshot.data().items()).isEmpty();
        assertThat(snapshot.data().products()).isEmpty(); assertThat(snapshot.data().categories()).isEmpty();
        assertThat(snapshot.data().associations()).isEmpty();
    }

    private static void noTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private static void readOnlyTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
    }
}
