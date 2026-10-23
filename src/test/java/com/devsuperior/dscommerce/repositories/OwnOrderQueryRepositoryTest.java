package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe.Observation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.Scenario;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.FIRST_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Committed JDBC setup and real JPA reads; no login, MockMvc dispatch or network request. */
@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
                "feature007.own-order-repository-test-context=list",
                "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OwnOrderQueryRepositoryTest {

    @Autowired private WebApplicationContext context;
    @Autowired private OrderRepository repository;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @Autowired private EntityManagerFactory factory;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private OwnOrderQueryTestFixture fixture;
    private Scenario scenario;
    private String stableUrl;
    private int cases;
    private int reads;
    private int pages;
    private int batches;
    private int detailQueries;

    @BeforeAll
    void verifyTheDedicatedStableSeedFreeDatabase() {
        noCallerTransaction();
        var environment = context.getEnvironment();
        stableUrl = environment.getRequiredProperty("spring.datasource.url");
        assertThat(stableUrl).startsWith("jdbc:h2:mem:admin-product-test-");
        assertStableDatabase();
        assertThat(environment.getRequiredProperty("feature007.own-order-repository-test-context")).isEqualTo("list");
        assertThat(environment.getRequiredProperty("spring.jpa.open-in-view")).isEqualTo("false");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
        assertThat(environment.getRequiredProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
        assertEmptyDatabase();
        System.out.println("T013_CONTEXT database=" + stableUrl.split(";", 2)[0]
                + " osiv=false webEnvironment=MOCK databaseContexts=1");
    }

    @BeforeEach
    void prepareOnlyOwnedCommittedRows() {
        noCallerTransaction();
        assertStableDatabase();
        assertEmptyDatabase();
        fixture = new OwnOrderQueryTestFixture(context);
        scenario = fixture.preparePaginationScenario();
        assertThat(database.snapshot().data().orders()).hasSize(9);
        assertThat(database.snapshot().data().items()).hasSize(10);
        cases++;
    }

    @AfterEach
    void cleanOnlyThisFixtureOutsideTheReadWindow() {
        noCallerTransaction();
        if (fixture != null) { fixture.close(); }
        assertEmptyDatabase();
        assertStableDatabase();
        sql.clear();
    }

    @AfterAll
    void reportActualReadCounts() {
        System.out.println("T013_COUNTS junitCases=" + cases + " readWindows=" + reads + " idPages=" + pages
                + " batchFetches=" + batches + " mockMvcDispatches=0 realHttpRequests=0 databaseContexts=1");
    }

    @Test
    void listPagesTwoTwoOneWithStableTimestampIdOrderAndOwnCount() {
        var expected = scenario.expectedClientOrders();
        assertThat(expected.subList(0, 3)).extracting(OrderManifest::createdAt)
                .containsOnly(FIRST_INSTANT.plusSeconds(60));
        assertThat(expected.get(0).id()).isGreaterThan(expected.get(1).id());
        assertThat(expected.get(1).id()).isGreaterThan(expected.get(2).id());
        var visited = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            var slice = expected.subList(page * 2, Math.min(page * 2 + 2, 5));
            var result = page(clientId(), page, 2, ids(slice), 5, 3, page < 2);
            visited.addAll(result.getContent());
        }
        assertThat(visited).containsExactlyElementsOf(ids(expected)).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "other", "admin", "both"})
    void listPageContentAndCountUseTheSameOwner(String identity) {
        var expected = owned(identity);
        page(ownerId(identity), 0, 1, List.of(expected.getFirst().id()), expected.size(), expected.size(), true);
    }

    @ParameterizedTest
    @CsvSource({"client,3,2,5,3", "empty,0,2,0,0", "empty,3,2,0,0", "absent,0,2,0,0"})
    void listEmptyAndBeyondPagesKeepTheRequestedPositionAndOwnTotals(String identity, int page, int size,
                                                                         long total, int totalPages) {
        page(ownerId(identity), page, size, List.of(), total, totalPages, identity.equals("client"));
    }

    @Test
    void listPagesOrderIdsBeforeFetchingAllItemsWithoutACollectionLimit() {
        var oldest = scenario.expectedClientOrders().getLast();
        var ids = page(clientId(), 4, 1, List.of(oldest.id()), 5, 5, true).getContent();
        var observation = batch(clientId(), ids);
        assertThat(observation.result()).hasSize(1);
        assertLoadedOrder(observation.result().getFirst(), oldest);
        assertThat(observation.result().getFirst().getItems()).hasSize(2);
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "other", "admin", "both"})
    void listBatchFiltersMixedIdsByOwnerWithoutDuplicateRoots(String identity) {
        var expected = owned(identity);
        var observation = batch(ownerId(identity), ids(scenario.insertedOrders()));
        var roots = observation.result();
        assertThat(roots).extracting(Order::getId).containsExactlyInAnyOrderElementsOf(ids(expected))
                .doesNotHaveDuplicates();
        for (var root : roots) {
            assertLoadedOrder(root, expected.stream().filter(order -> order.id() == root.getId())
                    .findFirst().orElseThrow());
        }
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @Test
    void listBatchRejectsForeignAndAbsentIdsEvenWhenExplicitlyRequested() {
        var foreign = new ArrayList<>(scenario.insertedOrders().stream()
                .filter(order -> order.buyerId() != clientId()).map(OrderManifest::id).toList());
        foreign.add(Long.MAX_VALUE);
        assertThat(batch(clientId(), foreign).result()).isEmpty();
    }

    @Test
    void listLeftFetchKeepsAnIncoherentRootWithoutItemsForTheLaterServiceToDiagnose() {
        var oldest = scenario.expectedClientOrders().getLast();
        var setup = new TransactionTemplate(transactionManager);
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        setup.executeWithoutResult(status -> assertThat(jdbc.update(
                "delete from tb_order_item where order_id = ?", oldest.id())).isEqualTo(oldest.items().size()));
        // Test-only committed preparation; do not hide this root with an inner join or repair it on read.
        page(clientId(), 4, 1, List.of(oldest.id()), 5, 5, true);
        var observation = batch(clientId(), List.of(oldest.id()));
        assertThat(observation.result()).hasSize(1);
        var root = observation.result().getFirst();
        assertThat(root.getId()).isEqualTo(oldest.id());
        assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "items")).isTrue();
        assertThat(root.getItems()).isEmpty();
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @AfterAll
    void reportDetailQueryCount() {
        System.out.println("T020_COUNTS detailQueries=" + detailQueries
                + " mockMvcDispatches=0 realHttpRequests=0 databaseContexts=1");
    }

    @Test
    void detailLoadsOnlyRequestedOwnRootWithCompleteRecordedBAItems() {
        var expected = scenario.expectedClientOrders().getLast();
        assertThat(expected.items().get(0).productId()).isGreaterThan(expected.items().get(1).productId());
        var observation = detail(clientId(), expected.id());
        var root = assertDetailOrder(observation, expected);
        assertThat(root.getItems()).hasSize(2);
        assertThat(root.getItems().get(0).getProduct().getPrice()).isEqualByComparingTo("12.00");
        assertThat(root.getItems().get(1).getProduct().getPrice()).isEqualByComparingTo("99.00");
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "other", "admin", "both"})
    void detailFiltersTheSameOrderByEachSuppliedOwnerWithoutAGlobalProbe(String identity) {
        var owner = ownerId(identity);
        var expected = owned(identity).getFirst();
        assertDetailOrder(detail(owner, expected.id()), expected);
        for (var foreignOwner : List.of(clientId(), scenario.otherBuyer().id(),
                scenario.accounts().admin().id(), scenario.accounts().both().id())) {
            if (foreignOwner != owner) {
                assertThat(detail(foreignOwner, expected.id()).result()).isEmpty();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-order", "empty-owner", "absent-owner"})
    void detailReturnsEmptyForAbsentOrderOrNonOwningAccount(String kind) {
        var owner = switch (kind) {
            case "empty-owner" -> scenario.emptyBuyer().id();
            case "absent-owner" -> Long.MAX_VALUE;
            default -> clientId();
        };
        var id = kind.equals("missing-order") ? Long.MAX_VALUE : scenario.expectedClientOrders().getLast().id();
        assertThat(detail(owner, id).result()).isEmpty();
    }

    @Test
    void detailLeftFetchKeepsARootWithoutItemsForTheLaterServiceToDiagnose() {
        var expected = scenario.expectedClientOrders().getLast();
        committedSetup(() -> assertThat(jdbc.update("delete from tb_order_item where order_id = ?", expected.id()))
                .isEqualTo(expected.items().size()));
        var observation = detail(clientId(), expected.id());
        var root = observation.result().orElseThrow();
        assertThat(root.getId()).isEqualTo(expected.id());
        assertThat(root.getBuyer().getId()).isEqualTo(expected.buyerId());
        assertThat(root.getCreatedAt()).isEqualTo(expected.createdAt());
        assertThat(root.getStatus().name()).isEqualTo(expected.status());
        assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "items")).isTrue();
        assertThat(root.getItems()).isEmpty();
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @Test
    void detailReadsRenamedCurrentProductsWithoutReplacingHistoricalValues() {
        var expected = scenario.expectedClientOrders().getLast();
        assertDetailOrder(detail(clientId(), expected.id()), expected);
        changeCurrentProduct(scenario.productB().id(), " Nome atual B ", "37.00");
        changeCurrentProduct(scenario.productA().id(), " Nome atual A ", "0.00");
        var current = withCurrentNames(expected, Map.of(scenario.productB().id(), " Nome atual B ",
                scenario.productA().id(), " Nome atual A "));
        var observation = detail(clientId(), expected.id());
        var root = assertDetailOrder(observation, current);
        assertThat(root.getItems().get(0).getProduct().getPrice()).isEqualByComparingTo("37.00");
        assertThat(root.getItems().get(1).getProduct().getPrice()).isEqualByComparingTo("0.00");
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "long"})
    void detailReadsEqualLegacyNamesZeroCurrentPricesAndNoCategoriesWithoutReordering(String kind) {
        var name = kind.equals("short") ? "Á" : " " + "Café ".repeat(17) + " ";
        var expected = scenario.expectedClientOrders().getLast();
        changeCurrentProduct(scenario.productA().id(), name, "0.00");
        changeCurrentProduct(scenario.productB().id(), name, "0.00");
        committedSetup(() -> assertThat(jdbc.update(
                "delete from tb_product_category where product_id in (?, ?)",
                scenario.productA().id(), scenario.productB().id())).isEqualTo(2));
        assertThat(jdbc.queryForObject("select count(*) from tb_product_category", Long.class)).isZero();
        var current = withCurrentNames(expected, Map.of(scenario.productA().id(), name, scenario.productB().id(), name));
        var observation = detail(clientId(), expected.id());
        var root = assertDetailOrder(observation, current);
        assertThat(root.getItems()).hasSize(2);
        assertThat(root.getItems().get(0).getProduct().getId()).isGreaterThan(root.getItems().get(1).getProduct().getId());
        assertThat(root.getItems()).allSatisfy(item ->
                assertThat(item.getProduct().getPrice()).isEqualByComparingTo("0.00"));
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "9007199254740993.01"})
    void detailPreservesSmallAndLargeHistoricalAmountsInTheLoadedAggregate(String amount) {
        var expected = scenario.expectedClientOrders().stream().filter(order -> order.total().equals(amount))
                .findFirst().orElseThrow();
        assertDetailOrder(detail(clientId(), expected.id()), expected);
    }

    @Test
    void detailKeepsDifferentPurchaseHistoriesForTheSameCurrentProduct() {
        var first = scenario.expectedClientOrders().getLast();
        var second = scenario.expectedClientOrders().getFirst();
        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(first.items().get(0).productId()).isEqualTo(second.items().getFirst().productId());
        var firstRoot = assertDetailOrder(detail(clientId(), first.id()), first);
        assertThat(firstRoot.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(firstRoot.getItems().get(0).getUnitPrice()).isEqualByComparingTo("10.00");
        var secondObservation = detail(clientId(), second.id());
        var secondRoot = assertDetailOrder(secondObservation, second);
        assertThat(secondRoot.getItems().getFirst().getQuantity()).isEqualTo(1);
        assertThat(secondRoot.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("15.25");
        assertThat(firstRoot.getTotal()).isEqualByComparingTo("42.50");
        assertThat(secondRoot.getTotal()).isEqualByComparingTo("15.25");
        assertThat(sql.events()).containsExactlyElementsOf(secondObservation.events());
    }

    @Test
    void detailFetchesAllItemsWithoutBorrowingTheListPageLimit() {
        var inputs = new ArrayList<ItemInput>();
        for (int index = 0; index < 51; index++) {
            var product = fixture.createProduct("Item de detalhe " + index, new BigDecimal("99.00"));
            inputs.add(new ItemInput(product.id(), 1, "0.01", "0.01"));
        }
        var expected = fixture.createOrder(scenario.accounts().client(), FIRST_INSTANT.plusSeconds(120), inputs, "0.51");
        var root = assertDetailOrder(detail(clientId(), expected.id()), expected);
        assertThat(root.getItems()).hasSize(51);
    }

    @Test
    void detailOrdersByRecordedPositionEvenWhenItemIdentifiersHaveTheOppositeOrder() {
        var original = scenario.expectedClientOrders().getLast();
        var b = original.items().get(0);
        var a = original.items().get(1);
        assertThat(b.id()).isLessThan(a.id());
        // Explicit committed preparation only: avoid the existing unique(order_id, position) constraint.
        committedSetup(() -> {
            assertThat(jdbc.update("update tb_order_item set position = ? where id = ? and order_id = ?",
                    2, b.id(), original.id())).isEqualTo(1);
            assertThat(jdbc.update("update tb_order_item set position = ? where id = ? and order_id = ?",
                    0, a.id(), original.id())).isEqualTo(1);
            assertThat(jdbc.update("update tb_order_item set position = ? where id = ? and order_id = ?",
                    1, b.id(), original.id())).isEqualTo(1);
        });
        var expected = new OrderManifest(original.id(), original.buyerId(), original.createdAt(), original.status(),
                original.total(), List.of(
                        new ItemManifest(a.id(), a.productId(), a.productName(), 0, a.quantity(), a.unitPrice(), a.subtotal()),
                        new ItemManifest(b.id(), b.productId(), b.productName(), 1, b.quantity(), b.unitPrice(), b.subtotal())));
        var root = assertDetailOrder(detail(clientId(), original.id()), expected);
        assertThat(root.getItems()).extracting(item -> item.getId()).containsExactly(a.id(), b.id());
    }

    /** Feature 008 target: specs/008-registrar-pagamento/contracts/own-orders-openapi.yaml. */
    @Test
    void paidListStillPagesTwoTwoOneByCreationAndIdRatherThanPaymentOccurrence() throws Exception {
        try (var paid = new PaidOrders(scenario.insertedOrders())) {
            var oldest = scenario.expectedClientOrders().getLast();
            paid.add(oldest, FIRST_INSTANT.plusSeconds(3600));
            paid.add(scenario.expectedClientOrders().getFirst(), FIRST_INSTANT.plusSeconds(120));
            var expected = scenario.expectedClientOrders().stream().map(paid::expected).toList();
            var visited = new ArrayList<Long>();
            for (int number = 0; number < 3; number++) {
                int pageNumber = number;
                var slice = expected.subList(number * 2, Math.min(number * 2 + 2, 5));
                var result = paid.unchanged(() -> page(clientId(), pageNumber, 2, ids(slice), 5, 3, pageNumber < 2));
                visited.addAll(result.getContent());
                var roots = paid.unchanged(() -> batch(clientId(), result.getContent())).result();
                assertThat(roots).extracting(Order::getId).containsExactlyInAnyOrderElementsOf(ids(slice));
                for (var root : roots) {
                    assertLoadedOrder(root, slice.stream().filter(order -> order.id() == root.getId()).findFirst().orElseThrow());
                }
            }
            assertThat(visited).containsExactlyElementsOf(ids(expected)).doesNotHaveDuplicates();
            assertThat(visited.getLast()).isEqualTo(oldest.id());
            paid.unchanged(() -> page(clientId(), 3, 2, List.of(), 5, 3, true));
            paid.unchanged(() -> page(scenario.emptyBuyer().id(), 0, 2, List.of(), 0, 0, false));
            assertDetailOrder(paid.unchanged(() -> detail(clientId(), oldest.id())), paid.expected(oldest));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"client", "other", "admin", "both"})
    void paidContentCountBatchAndDetailRemainRestrictedToEachOwnerIncludingAdmin(String identity) throws Exception {
        try (var paid = new PaidOrders(scenario.insertedOrders())) {
            var expected = owned(identity);
            var settled = paid.add(expected.getFirst(), FIRST_INSTANT.plusSeconds(120));
            var owner = ownerId(identity);
            paid.unchanged(() -> page(owner, 0, 1, List.of(settled.id()), expected.size(), expected.size(), true));
            var roots = paid.unchanged(() -> batch(owner, ids(scenario.insertedOrders()))).result();
            assertThat(roots).extracting(Order::getId).containsExactlyInAnyOrderElementsOf(ids(expected)).doesNotHaveDuplicates();
            for (var root : roots) {
                assertLoadedOrder(root, paid.expected(expected.stream().filter(order -> order.id() == root.getId()).findFirst().orElseThrow()));
            }
            assertDetailOrder(paid.unchanged(() -> detail(owner, settled.id())), settled);
            for (var foreignIdentity : List.of("client", "other", "admin", "both")) {
                if (foreignIdentity.equals(identity)) { continue; }
                var foreignOwner = ownerId(foreignIdentity);
                var foreign = owned(foreignIdentity);
                paid.unchanged(() -> page(foreignOwner, 0, 1, List.of(foreign.getFirst().id()),
                        foreign.size(), foreign.size(), true));
                assertThat(paid.unchanged(() -> batch(foreignOwner, List.of(settled.id()))).result()).isEmpty();
                assertThat(paid.unchanged(() -> detail(foreignOwner, settled.id())).result()).isEmpty();
                assertThat(paid.unchanged(() -> detail(foreignOwner, Long.MAX_VALUE)).result()).isEmpty();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"42.50", "0.01", "9007199254740993.01"})
    void paidDetailKeepsCurrentNamesAndExactHistoricalAmounts(String amount) throws Exception {
        try (var paid = new PaidOrders(scenario.insertedOrders())) {
            var original = scenario.expectedClientOrders().stream().filter(order -> order.total().equals(amount)).findFirst().orElseThrow();
            var settled = paid.add(original, FIRST_INSTANT.plusSeconds(120));
            changeCurrentProduct(scenario.productA().id(), " A atual após quitação ", "0.00");
            changeCurrentProduct(scenario.productB().id(), "B atual após quitação", "37.00");
            var expected = withCurrentNames(settled, Map.of(scenario.productA().id(), " A atual após quitação ",
                    scenario.productB().id(), "B atual após quitação"));
            var root = assertDetailOrder(paid.unchanged(() -> detail(clientId(), original.id())), expected);
            assertThat(root.getTotal()).isEqualByComparingTo(amount);
            assertThat(root.getCreatedAt()).isEqualTo(original.createdAt());
        }
    }

    /** Local 008 setup/cleanup only: do not change the historical fixture/probe's meaning or read helpers. */
    private final class PaidOrders implements AutoCloseable {
        private final List<OrderManifest> fixtureOrders;
        private final List<Long> paidIds = new ArrayList<>();
        private final PaymentRegistrationDatabaseProbe payments = new PaymentRegistrationDatabaseProbe(stableUrl);
        private final List<PaymentRow> initialPayments = payments.snapshot().payments();

        private PaidOrders(List<OrderManifest> fixtureOrders) { this.fixtureOrders = List.copyOf(fixtureOrders); }

        private OrderManifest add(OrderManifest original, Instant occurredAt) {
            noCallerTransaction();
            assertThat(fixtureOrders).contains(original);
            assertThat(paidIds).doesNotContain(original.id());
            assertThat(original.status()).isEqualTo("WAITING_PAYMENT");
            assertThat(occurredAt).isAfterOrEqualTo(original.createdAt());
            assertThat(occurredAt.getNano() % 1_000_000).isZero();
            var before = payments.snapshot();
            setup().executeWithoutResult(status -> {
                assertThat(status.isNewTransaction()).isTrue();
                assertThat(jdbc.update("insert into tb_payment (order_id, occurred_at) values (?, ?)",
                        original.id(), occurredAt.atOffset(ZoneOffset.UTC))).isEqualTo(1);
                assertThat(jdbc.update("update tb_order set status = ? where id = ? and status = ?",
                        "PAID", original.id(), "WAITING_PAYMENT")).isEqualTo(1);
            });
            paidIds.add(original.id()); // Track before any oracle can fail, so finally still removes the owned Payment.
            var after = payments.snapshot();
            var expectedPayments = new ArrayList<>(before.payments());
            expectedPayments.add(new PaymentRow(original.id(), occurredAt));
            assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(expectedPayments);
            assertThat(after.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                    .map(row -> row.id() == original.id() ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
            assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                    .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
            return expected(original);
        }

        private OrderManifest expected(OrderManifest original) {
            return paidIds.contains(original.id()) ? new OrderManifest(original.id(), original.buyerId(),
                    original.createdAt(), "PAID", original.total(), original.items()) : original;
        }

        private <T> T unchanged(Callable<T> operation) throws Exception {
            noCallerTransaction();
            var before = payments.snapshot();
            try { return operation.call(); }
            finally {
                noCallerTransaction();
                PaymentRegistrationDatabaseProbe.assertUnchanged(before, payments.snapshot());
                assertThat(sql.events()).noneSatisfy(event -> assertThat(normalized(event.sql())).contains("tb_payment"));
            }
        }

        private TransactionTemplate setup() {
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return transaction;
        }

        @Override public void close() {
            noCallerTransaction();
            setup().executeWithoutResult(status -> {
                for (var id : paidIds) { assertThat(jdbc.update("delete from tb_payment where order_id = ?", id)).isEqualTo(1); }
            });
            assertThat(payments.snapshot().payments()).isEqualTo(initialPayments);
            // The owning fixture's existing @AfterEach removes items/orders only after this scope closes.
        }
    }

    private Observation<Optional<Order>> detail(long ownerId, long id) {
        detailQueries++;
        var observation = read("detail", () -> repository.findOwnOrderWithItemsById(ownerId, id));
        assertThat(observation.events()).hasSize(1); // No global lookup, count or lazy follow-up.
        var statement = normalized(observation.events().getFirst().sql());
        assertThat(statement).contains("select distinct", "left join tb_order_item", "left join tb_product")
                .containsPattern("order by \\w+\\.position(?: asc)?$")
                .doesNotContain("offset", "fetch first", " limit ", "count(", "exists", " in (",
                        "tb_category", "tb_product_category", "tb_user_account");
        var alias = Pattern.compile("\\bfrom tb_order (\\w+)\\b").matcher(statement);
        assertThat(alias.find()).isTrue();
        var root = Pattern.quote(alias.group(1));
        var where = statement.substring(statement.indexOf(" where "));
        assertThat(where).containsPattern(root + "\\.id\\s*=\\s*\\?")
                .containsPattern(root + "\\.buyer_id\\s*=\\s*\\?");
        return observation;
    }

    private Order assertDetailOrder(Observation<Optional<Order>> observation, OrderManifest expected) {
        var root = observation.result().orElseThrow();
        assertThat(root.getId()).isEqualTo(expected.id());
        assertThat(factory.getPersistenceUnitUtil().isLoaded(root, "buyer")).isFalse();
        assertThat(root.getBuyer().getId()).isEqualTo(expected.buyerId());
        assertLoadedOrder(root, expected);
        assertThat(root.getTotal()).isEqualByComparingTo(expected.total());
        for (int index = 0; index < expected.items().size(); index++) {
            assertThat(root.getItems().get(index).getSubtotal()).isEqualByComparingTo(expected.items().get(index).subtotal());
        }
        assertThat(sql.events()).containsExactlyElementsOf(observation.events());
        return root;
    }

    private void changeCurrentProduct(long id, String name, String price) {
        committedSetup(() -> assertThat(jdbc.update("update tb_product set name = ?, price = ? where id = ?",
                name, new BigDecimal(price), id)).isEqualTo(1));
    }

    private void committedSetup(Runnable preparation) {
        noCallerTransaction();
        var setup = new TransactionTemplate(transactionManager);
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        setup.executeWithoutResult(status -> preparation.run());
    }

    private static OrderManifest withCurrentNames(OrderManifest original, Map<Long, String> names) {
        return new OrderManifest(original.id(), original.buyerId(), original.createdAt(), original.status(),
                original.total(), original.items().stream().map(item -> new ItemManifest(item.id(), item.productId(),
                        names.getOrDefault(item.productId(), item.productName()), item.position(), item.quantity(),
                        item.unitPrice(), item.subtotal())).toList());
    }

    private Page<Long> page(long ownerId, int number, int size, List<Long> expected, long total, int totalPages,
                            boolean requireCount) {
        pages++;
        var observation = read("ids", () -> repository.findOwnOrderIds(ownerId, PageRequest.of(number, size)));
        var result = observation.result();
        assertThat(result.getContent()).containsExactlyElementsOf(expected);
        assertThat(result.getNumber()).isEqualTo(number);
        assertThat(result.getSize()).isEqualTo(size);
        assertThat(result.getTotalElements()).isEqualTo(total);
        assertThat(result.getTotalPages()).isEqualTo(totalPages);
        var statements = observation.events().stream().map(event -> normalized(event.sql())).toList();
        assertThat(statements).allSatisfy(statement -> {
            assertThat(statement).containsPattern("\\.buyer_id\\s*=\\s*\\?");
            assertThat(statement).doesNotContain(" join ", "tb_order_item", "tb_product", "tb_category", "tb_user_account");
        });
        assertThat(statements.getFirst()).matches("(?s)^select \\w+\\.id from tb_order .*")
                .containsPattern("order by \\w+\\.created_at desc,\\s*\\w+\\.id desc")
                .contains("fetch first");
        if (number > 0) {
            assertThat(statements.getFirst()).contains("offset");
        } else {
            assertThat(statements.getFirst()).doesNotContain("offset");
        }
        if (requireCount) {
            assertThat(statements).anySatisfy(statement -> assertThat(statement).contains("count("));
        }
        return result;
    }

    private Observation<List<Order>> batch(long ownerId, List<Long> ids) {
        batches++;
        var observation = read("batch", () -> repository.findOwnOrdersWithItemsByIds(ownerId, ids));
        assertThat(observation.events()).hasSize(1); // One batch query, not a global HTTP SELECT budget.
        var statement = normalized(observation.events().getFirst().sql());
        assertThat(statement).contains("left join tb_order_item", "left join tb_product", " in (")
                .containsPattern("\\.buyer_id\\s*=\\s*\\?")
                .doesNotContain("offset", "fetch first", "tb_category", "tb_product_category", "tb_user_account");
        return observation;
    }

    private <T> Observation<T> read(String kind, Supplier<T> operation) {
        noCallerTransaction();
        var observation = database.observeRead(sql, operation);
        noCallerTransaction();
        observation.assertNoWrites();
        assertThat(observation.events()).isNotEmpty().allSatisfy(event -> {
            assertThat(event.transactionActive()).isTrue();
            System.out.println("T013_SQL kind=" + kind + " sql=" + normalized(event.sql()));
        });
        reads++;
        System.out.println("T013_READ kind=" + kind + " statements=" + observation.events().size()
                + " snapshotEqual=true callerTransaction=false");
        return observation;
    }

    private void assertLoadedOrder(Order root, OrderManifest expected) {
        noCallerTransaction();
        var loaded = factory.getPersistenceUnitUtil();
        assertThat(loaded.isLoaded(root, "items")).isTrue();
        assertThat(root.getCreatedAt()).isEqualTo(expected.createdAt());
        assertThat(root.getStatus().name()).isEqualTo(expected.status());
        assertThat(root.getItems()).extracting(item -> item.getId())
                .containsExactlyElementsOf(expected.items().stream().map(item -> item.id()).toList());
        for (int index = 0; index < expected.items().size(); index++) {
            var item = root.getItems().get(index);
            var manifest = expected.items().get(index);
            assertThat(item.getPosition()).isEqualTo(manifest.position());
            assertThat(item.getQuantity()).isEqualTo(manifest.quantity());
            assertThat(item.getUnitPrice()).isEqualByComparingTo(manifest.unitPrice());
            assertThat(loaded.isLoaded(item, "product")).isTrue();
            assertThat(item.getProduct().getId()).isEqualTo(manifest.productId());
            assertThat(item.getProduct().getName()).isEqualTo(manifest.productName());
            assertThat(loaded.isLoaded(item.getProduct(), "categories")).isFalse();
        }
    }

    private List<OrderManifest> owned(String identity) {
        return switch (identity) {
            case "client" -> scenario.expectedClientOrders();
            case "other" -> List.of(scenario.insertedOrders().get(5), scenario.insertedOrders().get(1));
            case "admin" -> List.of(scenario.insertedOrders().get(3));
            case "both" -> List.of(scenario.insertedOrders().get(7));
            default -> throw new IllegalArgumentException("Unsupported fixture owner");
        };
    }

    private long ownerId(String identity) {
        return switch (identity) {
            case "empty" -> scenario.emptyBuyer().id();
            case "absent" -> Long.MAX_VALUE;
            default -> owned(identity).getFirst().buyerId();
        };
    }

    private long clientId() { return scenario.accounts().client().id(); }
    private static List<Long> ids(List<OrderManifest> orders) { return orders.stream().map(OrderManifest::id).toList(); }
    private static String normalized(String statement) { return statement.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim(); }

    private void assertStableDatabase() {
        assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url")).isEqualTo(stableUrl);
        assertThat(jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL()))
                .isEqualTo(stableUrl.split(";", 2)[0]);
    }

    private void assertEmptyDatabase() {
        var snapshot = database.snapshot();
        assertThat(snapshot.accountIds()).isEmpty();
        assertThat(snapshot.roles()).isEmpty();
        assertThat(snapshot.data().orders()).isEmpty();
        assertThat(snapshot.data().items()).isEmpty();
        assertThat(snapshot.data().products()).isEmpty();
        assertThat(snapshot.data().categories()).isEmpty();
        assertThat(snapshot.data().associations()).isEmpty();
    }

    private static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
}
