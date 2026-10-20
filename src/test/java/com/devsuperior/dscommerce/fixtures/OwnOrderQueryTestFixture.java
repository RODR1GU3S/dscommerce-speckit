package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.AccessAccounts;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit committed JDBC setup, activated only in an AdminProductTestConfig context.
 * Construction inserts nothing; callers explicitly prepare data and close the fixture.
 * Expected money is supplied by the scenario, never derived through production order code.
 * One instance owns its rows and is not intended for concurrent setup/cleanup.
 */
public final class OwnOrderQueryTestFixture implements AutoCloseable {

    public static final Instant FIRST_INSTANT = Instant.parse("2026-10-19T12:00:00.123Z");

    private final AdminProductTestFixture catalog;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate committed;
    private final Set<Long> accounts = new LinkedHashSet<>();
    private final Map<Long, ProductData> products = new LinkedHashMap<>();
    private final Map<Long, List<Long>> orders = new LinkedHashMap<>();

    public OwnOrderQueryTestFixture(WebApplicationContext context) {
        // Reuse the existing profile/initializer/database checks and real security support.
        catalog = new AdminProductTestFixture(context);
        jdbc = context.getBean(JdbcTemplate.class);
        committed = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AccessAccounts prepareAccessAccounts() {
        var access = catalog.prepareAccessAccounts();
        for (var account : List.of(access.client(), access.admin(), access.both(), access.none())) {
            accounts.add(account.id());
        }
        return access;
    }

    public Account prepareBuyer() {
        var buyer = catalog.prepareBuyer();
        accounts.add(buyer.id());
        return buyer;
    }

    public ProductData createProduct(String name, BigDecimal currentPrice) {
        var product = catalog.createProduct(name, "Produto para consulta de pedidos",
                "https://example.invalid/own-order-query.jpg", currentPrice);
        products.put(product.id(), product);
        return product;
    }

    /** Existing login helper uses an internal MockMvc dispatch, not a network HTTP request. */
    public String login(Account account) {
        return catalog.login(account);
    }

    /**
     * Inserts an entire order in its own transaction, including during a caller rollback.
     * Only owned accounts/products may be used; the existing database enforces row constraints.
     * No new commercial item/quantity/price limit is imposed by this test support.
     */
    public OrderManifest createOrder(Account buyer, Instant createdAt, List<ItemInput> items,
                                     String expectedTotal) {
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expectedTotal);
        var supplied = List.copyOf(items);
        if (!accounts.contains(buyer.id()) || supplied.isEmpty()
                || supplied.stream().anyMatch(item -> !products.containsKey(item.productId()))) {
            throw new IllegalArgumentException("Use an owned buyer and nonempty owned product lines");
        }
        var result = Objects.requireNonNull(committed.execute(status -> {
            var orderId = insert("insert into tb_order (buyer_id, created_at, status) values (?, ?, ?)",
                    buyer.id(), Timestamp.from(createdAt), "WAITING_PAYMENT");
            var expectedItems = new ArrayList<ItemManifest>();
            for (int position = 0; position < supplied.size(); position++) {
                var item = supplied.get(position);
                var itemId = insert("insert into tb_order_item "
                                + "(order_id, product_id, position, quantity, unit_price) values (?, ?, ?, ?, ?)",
                        orderId, item.productId(), position, item.quantity(), new BigDecimal(item.unitPrice()));
                expectedItems.add(new ItemManifest(itemId, item.productId(),
                        products.get(item.productId()).name(), position, item.quantity(), item.unitPrice(),
                        item.expectedSubtotal()));
            }
            return new OrderManifest(orderId, buyer.id(), createdAt, "WAITING_PAYMENT", expectedTotal, expectedItems);
        }));
        // Record ownership only after all rows have committed successfully.
        orders.put(result.id(), result.items().stream().map(ItemManifest::id).toList());
        return result;
    }

    /** Five CLIENT orders, interleaved foreign orders, and a three-way tie across page boundaries. */
    public Scenario preparePaginationScenario() {
        if (!accounts.isEmpty() || !products.isEmpty() || !orders.isEmpty()) {
            throw new IllegalStateException("Clean this fixture's previous scenario first");
        }
        var access = prepareAccessAccounts();
        var other = prepareBuyer();
        var empty = prepareBuyer();
        var productA = createProduct(" Produto A atual ", new BigDecimal("99.00"));
        var productB = createProduct("Produto B atual", new BigDecimal("12.00"));
        var category = catalog.createCategory("Categoria de pedidos");
        catalog.associate(productA.id(), category.id());
        catalog.associate(productB.id(), category.id());

        var first = createOrder(access.client(), FIRST_INSTANT,
                List.of(new ItemInput(productB.id(), 2, "10.00", "20.00"),
                        new ItemInput(productA.id(), 3, "7.50", "22.50")), "42.50");
        var foreignFirst = createOrder(other, FIRST_INSTANT.plusSeconds(5),
                List.of(new ItemInput(productA.id(), 1, "4.00", "4.00")), "4.00");
        var second = createOrder(access.client(), FIRST_INSTANT.plusSeconds(10),
                List.of(new ItemInput(productB.id(), 2, "10.00", "20.00")), "20.00");
        var admin = createOrder(access.admin(), FIRST_INSTANT.plusSeconds(20),
                List.of(new ItemInput(productA.id(), 1, "7.50", "7.50")), "7.50");
        var tiedInstant = FIRST_INSTANT.plusSeconds(60);
        var third = createOrder(access.client(), tiedInstant,
                List.of(new ItemInput(productB.id(), 1, "0.01", "0.01")), "0.01");
        var foreignSecond = createOrder(other, FIRST_INSTANT.plusSeconds(40),
                List.of(new ItemInput(productB.id(), 1, "12.00", "12.00")), "12.00");
        var fourth = createOrder(access.client(), tiedInstant,
                List.of(new ItemInput(productA.id(), 1, "9007199254740993.01", "9007199254740993.01")),
                "9007199254740993.01");
        var both = createOrder(access.both(), tiedInstant,
                List.of(new ItemInput(productA.id(), 1, "7.50", "7.50")), "7.50");
        var fifth = createOrder(access.client(), tiedInstant,
                List.of(new ItemInput(productB.id(), 1, "15.25", "15.25")), "15.25");

        // Explicit oracle order, not an application query, mapper, comparator or entity total.
        return new Scenario(access, other, empty, productA, productB,
                List.of(first, foreignFirst, second, admin, third, foreignSecond, fourth, both, fifth),
                List.of(fifth, fourth, third, second, first));
    }

    public void cleanUp() {
        committed.executeWithoutResult(status -> {
            for (var order : orders.entrySet()) {
                for (var itemId : order.getValue()) {
                    jdbc.update("delete from tb_order_item where id = ? and order_id = ?", itemId, order.getKey());
                }
            }
            for (var orderId : orders.keySet()) {
                jdbc.update("delete from tb_order where id = ?", orderId);
            }
        });
        // Retain ownership on failure, allowing retry after foreign dependencies are removed.
        orders.clear();
        catalog.cleanUp();
        products.clear();
        accounts.clear();
    }

    @Override
    public void close() {
        cleanUp();
    }

    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public record ItemInput(long productId, int quantity, String unitPrice, String expectedSubtotal) {
        public ItemInput {
            Objects.requireNonNull(unitPrice);
            Objects.requireNonNull(expectedSubtotal);
        }
    }

    public record ItemManifest(long id, long productId, String productName, int position, int quantity,
                               String unitPrice, String subtotal) { }

    public record OrderManifest(long id, long buyerId, Instant createdAt, String status, String total,
                                List<ItemManifest> items) {
        public OrderManifest {
            items = List.copyOf(items);
        }
    }

    public record Scenario(AccessAccounts accounts, Account otherBuyer, Account emptyBuyer,
                           ProductData productA, ProductData productB,
                           List<OrderManifest> insertedOrders, List<OrderManifest> expectedClientOrders) {
        public Scenario {
            insertedOrders = List.copyOf(insertedOrders);
            expectedClientOrders = List.copyOf(expectedClientOrders);
        }
    }
}
