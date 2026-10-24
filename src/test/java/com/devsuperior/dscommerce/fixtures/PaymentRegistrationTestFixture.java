package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.AccessAccounts;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
 * Explicit owned JDBC setup, without payment production types or a synthetic payment schema.
 * Construction inserts nothing. Keep the fixture reachable by finally/@AfterEach/@AfterAll,
 * including when setup or an assertion fails; close() removes only its own rows.
 * A fixture has one setup/cleanup owner and is not intended for concurrent mutation.
 */
public final class PaymentRegistrationTestFixture implements AutoCloseable {

    private final AdminProductTestFixture catalog;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate committed;
    private final Set<Long> accounts = new LinkedHashSet<>();
    private final Map<Long, ProductData> products = new LinkedHashMap<>();
    private final Map<Long, List<Long>> orders = new LinkedHashMap<>();

    public PaymentRegistrationTestFixture(WebApplicationContext context) {
        noCallerTransaction();
        // Reuse the approved profile, initializer, unique H2 URL and real password/JWT checks.
        catalog = new AdminProductTestFixture(Objects.requireNonNull(context));
        jdbc = context.getBean(JdbcTemplate.class);
        committed = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        committed.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public AccessAccounts prepareAccessAccounts() {
        noCallerTransaction();
        var access = catalog.prepareAccessAccounts();
        for (var account : List.of(access.admin(), access.client(), access.both(), access.none())) {
            accounts.add(account.id());
        }
        return access;
    }

    public Account prepareBuyer() {
        noCallerTransaction();
        var buyer = catalog.prepareBuyer();
        accounts.add(buyer.id());
        return buyer;
    }

    public Account createAccount(String name, String password, Set<UserRole> roles) {
        noCallerTransaction();
        var account = catalog.createAccount(name, password, roles);
        accounts.add(account.id());
        return account;
    }

    public ProductData createProduct(String name, BigDecimal currentPrice) {
        noCallerTransaction();
        var product = catalog.createProduct(name, "Produto para registro de pagamento",
                "https://example.invalid/payment-registration.jpg", currentPrice);
        products.put(product.id(), product);
        return product;
    }

    /**
     * Complete WAITING_PAYMENT order committed before return; no Payment row is manufactured.
     * The scenario supplies historical money/oracles, independently of production total mapping.
     * Reuse the 007 manifest types and ordered-line JDBC setup, without its payment-unaware cleanup.
     */
    public OrderManifest createOrder(Account buyer, Instant createdAt, List<ItemInput> items,
                                     String expectedTotal) {
        noCallerTransaction();
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(expectedTotal);
        var supplied = List.copyOf(items);
        if (!accounts.contains(buyer.id()) || supplied.isEmpty()
                || supplied.stream().anyMatch(item -> !products.containsKey(item.productId()))) {
            throw new IllegalArgumentException("Use an owned buyer and nonempty owned product lines");
        }
        var order = Objects.requireNonNull(committed.execute(status -> {
            var id = insert("insert into tb_order (buyer_id, created_at, status) values (?, ?, ?)",
                    buyer.id(), Timestamp.from(createdAt), "WAITING_PAYMENT");
            var lines = new ArrayList<ItemManifest>();
            for (int position = 0; position < supplied.size(); position++) {
                var item = supplied.get(position);
                var itemId = insert("insert into tb_order_item "
                                + "(order_id, product_id, position, quantity, unit_price) values (?, ?, ?, ?, ?)",
                        id, item.productId(), position, item.quantity(), new BigDecimal(item.unitPrice()));
                lines.add(new ItemManifest(itemId, item.productId(), products.get(item.productId()).name(),
                        position, item.quantity(),
                        item.unitPrice(), item.expectedSubtotal()));
            }
            return new OrderManifest(id, buyer.id(), createdAt, "WAITING_PAYMENT", expectedTotal, lines);
        }));
        // Track IDs after a successful committed return; insert failures roll back the whole local setup.
        orders.put(order.id(), order.items().stream().map(ItemManifest::id).toList());
        return order;
    }

    public Set<Long> ownedOrderIds() {
        return Set.copyOf(orders.keySet());
    }

    public void cleanUp() {
        noCallerTransaction();
        committed.executeWithoutResult(status -> {
            // This metadata tolerance belongs ONLY to cleanup before T008 creates the real table.
            // If it exists, any DELETE failure propagates; no missing-schema fallback in the probe.
            if (!orders.isEmpty() && paymentTableExistsForCleanup()) {
                for (var orderId : orders.keySet()) {
                    jdbc.update("delete from tb_payment where order_id = ?", orderId);
                }
            }
            for (var order : orders.entrySet()) {
                for (var itemId : order.getValue()) {
                    jdbc.update("delete from tb_order_item where id = ? and order_id = ?", itemId, order.getKey());
                }
            }
            for (var orderId : orders.keySet()) {
                jdbc.update("delete from tb_order where id = ?", orderId);
            }
            // Role changes by a scenario must not strand a new role before deleting its owned account.
            // The account ID is owned, so this remains selective even after revocation/grant tests.
            for (var accountId : accounts) {
                jdbc.update("delete from tb_user_account_role where user_account_id = ?", accountId);
            }
        });
        // Retain order ownership until the first cleanup transaction commits successfully.
        orders.clear();
        // Catalog cleanup retains its own ownership on failure; retry remains possible after this phase.
        catalog.cleanUp();
        accounts.clear();
        products.clear();
    }

    @Override
    public void close() {
        cleanUp();
    }

    private boolean paymentTableExistsForCleanup() {
        return Objects.requireNonNull(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            try (var tables = connection.getMetaData().getTables(null, "PUBLIC", "TB_PAYMENT", null)) {
                return tables.next();
            }
        }), "Cleanup schema metadata must return a result");
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

    private static void noCallerTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Payment fixture setup/cleanup requires no caller transaction");
        }
    }
}
