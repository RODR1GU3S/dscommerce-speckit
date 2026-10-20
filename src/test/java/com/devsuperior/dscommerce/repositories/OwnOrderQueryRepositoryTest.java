package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe.Observation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.Scenario;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.FIRST_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;

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
