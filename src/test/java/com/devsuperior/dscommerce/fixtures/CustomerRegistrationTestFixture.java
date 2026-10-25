package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit JDBC setup for the opt-in T006 context, with no component registration or legacy helper.
 * Construction writes nothing. Acquire the fixture before setup in try-with-resources (or close it
 * in finally), including when preparation fails. Finish/join all request workers before closing.
 * One instance has one setup/cleanup owner; it is not intended for concurrent mutation.
 */
public final class CustomerRegistrationTestFixture implements AutoCloseable {

    public static final Instant ORDER_INSTANT = Instant.parse("2026-01-02T10:00:00.123Z");
    public static final Instant PAYMENT_INSTANT = Instant.parse("2026-01-03T10:00:00.456Z");

    /**
     * Public-domain crypt_blowfish vectors, copied as data, never encoded during fixture setup.
     * https://github.com/openwall/crypt_blowfish/blob/master/wrapper.c (tests array).
     * LONG retains the complete 98-byte input, including the historical BCrypt suffix beyond 72.
     * This does not assert that BCrypt distinguishes variants beyond its historical limit.
     */
    public enum LegacyCredential {
        SHORT("U*U", "$2a$05$CCCCCCCCCCCCCCCCCCCCC.E5YPO9kmyuRGyh0XouQYb4YMJKvyOeW"),
        LONG("0123456789abcdefghijklmnopqrstuvwxyz"
                + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789chars after 72 are ignored",
                "$2a$05$abcdefghijklmnopqrstuu5s2v8.iXieOjg/.AySBTTZIIVFJeBui");

        private final String password;
        private final String passwordHash;

        LegacyCredential(String password, String passwordHash) {
            this.password = password;
            this.passwordHash = passwordHash;
        }

        public String password() {
            return password;
        }

        public String passwordHash() {
            return passwordHash;
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate committed;
    private final Set<Long> accounts = new LinkedHashSet<>();
    private final Set<Long> categories = new LinkedHashSet<>();
    private final Set<Long> products = new LinkedHashSet<>();
    private final Set<ProductCategory> pairs = new LinkedHashSet<>();
    private final Map<Long, OrderData> orders = new LinkedHashMap<>();

    public CustomerRegistrationTestFixture(ConfigurableApplicationContext context) {
        noCallerTransaction();
        Objects.requireNonNull(context);
        var environment = context.getEnvironment();
        var settings = environment.getPropertySources().get(CustomerRegistrationTestConfig.PROPERTY_SOURCE_NAME);
        if (!environment.acceptsProfiles(Profiles.of("test")) || settings == null) {
            throw new IllegalArgumentException("Customer fixtures require explicit CustomerRegistrationTestConfig");
        }
        jdbc = context.getBean(JdbcTemplate.class);
        var configuredUrl = Objects.toString(settings.getProperty("spring.datasource.url"), "");
        var actualUrl = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        // H2 metadata omits URL options. Reject a global, legacy or overridden datasource.
        if (!configuredUrl.startsWith("jdbc:h2:mem:customer-registration-test-")
                || !configuredUrl.equals(environment.getProperty("spring.datasource.url"))
                || !configuredUrl.split(";", 2)[0].equals(actualUrl)) {
            throw new IllegalArgumentException("Customer fixtures require their own configured H2 database");
        }
        committed = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        committed.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Only existing columns are written: future nullable registration fields remain NULL. */
    public Account createAccount(String name, LegacyCredential credential, Set<UserRole> roles) {
        noCallerTransaction();
        Objects.requireNonNull(name);
        Objects.requireNonNull(credential);
        var suppliedRoles = Set.copyOf(roles);
        var account = Objects.requireNonNull(committed.execute(status -> {
            var id = insert("insert into tb_user_account (name, password_hash) values (?, ?)",
                    name, credential.passwordHash());
            for (var role : suppliedRoles) {
                jdbc.update("insert into tb_user_account_role (user_account_id, role) values (?, ?)",
                        id, role.name());
            }
            return new Account(id, name, credential, suppliedRoles);
        }));
        accounts.add(account.id());
        return account;
    }

    public AccessAccounts prepareAccessAccounts() {
        return new AccessAccounts(
                createAccount(disposableName("client"), LegacyCredential.SHORT, Set.of(UserRole.CLIENT)),
                createAccount(disposableName("admin"), LegacyCredential.LONG, Set.of(UserRole.ADMIN)),
                createAccount(disposableName("both"), LegacyCredential.LONG, Set.of(UserRole.CLIENT, UserRole.ADMIN)),
                createAccount(disposableName("none"), LegacyCredential.SHORT, Set.of()));
    }

    public CategoryData createCategory(String name) {
        noCallerTransaction();
        Objects.requireNonNull(name);
        var category = Objects.requireNonNull(committed.execute(status ->
                new CategoryData(insert("insert into tb_category (name) values (?)", name), name)));
        categories.add(category.id());
        return category;
    }

    public ProductData createProduct(String name, BigDecimal currentPrice, List<CategoryData> selectedCategories) {
        noCallerTransaction();
        Objects.requireNonNull(name);
        requireMoney(currentPrice, false);
        var selected = List.copyOf(selectedCategories);
        if (selected.stream().anyMatch(category -> !categories.contains(category.id()))
                || selected.stream().map(CategoryData::id).distinct().count() != selected.size()) {
            throw new IllegalArgumentException("Use distinct owned categories");
        }
        var product = Objects.requireNonNull(committed.execute(status -> {
            var id = insert("insert into tb_product (name, description, image, price) values (?, ?, ?, ?)",
                    name, "Catalogo previo ao cadastro009", "https://example.invalid/customer009.jpg", currentPrice);
            for (var category : selected) {
                jdbc.update("insert into tb_product_category (product_id, category_id) values (?, ?)",
                        id, category.id());
            }
            return new ProductData(id, name, currentPrice);
        }));
        products.add(product.id());
        selected.forEach(category -> pairs.add(new ProductCategory(product.id(), category.id())));
        return product;
    }

    /** Null paidAt means WAITING_PAYMENT/no payment; otherwise commit PAID and its payment together. */
    public OrderData createOrder(Account buyer, Instant createdAt, List<ItemInput> items, Instant paidAt) {
        noCallerTransaction();
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(createdAt);
        var supplied = List.copyOf(items);
        if (!accounts.contains(buyer.id()) || supplied.isEmpty()
                || supplied.stream().anyMatch(item -> !products.contains(item.productId()))
                || supplied.stream().map(ItemInput::productId).distinct().count() != supplied.size()) {
            throw new IllegalArgumentException("Use an owned buyer and distinct owned product lines");
        }
        if (!createdAt.equals(createdAt.truncatedTo(ChronoUnit.MILLIS))
                || (paidAt != null && (paidAt.isBefore(createdAt)
                || !paidAt.equals(paidAt.truncatedTo(ChronoUnit.MILLIS))))) {
            throw new IllegalArgumentException("Use millisecond instants and payment at or after creation");
        }
        var result = Objects.requireNonNull(committed.execute(status -> {
            var orderStatus = paidAt == null ? "WAITING_PAYMENT" : "PAID";
            var id = insert("insert into tb_order (buyer_id, created_at, status) values (?, ?, ?)",
                    buyer.id(), Timestamp.from(createdAt), orderStatus);
            var lines = new ArrayList<ItemData>();
            for (var position = 0; position < supplied.size(); position++) {
                var item = supplied.get(position);
                var itemId = insert("insert into tb_order_item "
                                + "(order_id, product_id, position, quantity, unit_price) values (?, ?, ?, ?, ?)",
                        id, item.productId(), position, item.quantity(), item.unitPrice());
                lines.add(new ItemData(itemId, item.productId(), position, item.quantity(), item.unitPrice()));
            }
            PaymentData payment = null;
            if (paidAt != null) {
                jdbc.update("insert into tb_payment (order_id, occurred_at) values (?, ?)", id, Timestamp.from(paidAt));
                payment = new PaymentData(id, paidAt);
            }
            return new OrderData(id, buyer.id(), createdAt, orderStatus, lines, payment);
        }));
        // Track ownership only after the transaction template returns from commit, not after flush.
        orders.put(result.id(), result);
        return result;
    }

    /** Explicit historical prices differ from today's catalog; expected totals are scenario literals. */
    public Scenario prepareScenario() {
        var access = prepareAccessAccounts();
        var category = createCategory("Catalogo anterior009");
        var first = createProduct("Produto anterior A", new BigDecimal("99.00"), List.of(category));
        var second = createProduct("Produto anterior B", new BigDecimal("12.00"), List.of(category));
        var waiting = createOrder(access.client(), ORDER_INSTANT,
                List.of(new ItemInput(first.id(), 3, new BigDecimal("7.50")),
                        new ItemInput(second.id(), 2, new BigDecimal("10.00"))), null);
        var paid = createOrder(access.both(), ORDER_INSTANT,
                List.of(new ItemInput(first.id(), 1, new BigDecimal("7.50"))), PAYMENT_INSTANT);
        return new Scenario(access, category, List.of(first, second), waiting, paid,
                new BigDecimal("42.50"), new BigDecimal("7.50"));
    }

    public Ownership ownership() {
        return new Ownership(accounts, categories, products, pairs, orders.keySet(),
                orders.values().stream().flatMap(order -> order.items().stream()).map(ItemData::id)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    /**
     * Call in finally after workers finish. Never discover ownership by name or clear whole tables.
     * A payment subsequently added to an owned order and roles added to an owned account belong to
     * those owned aggregates. Other dependent rows are not erased: an FK failure remains visible.
     * All deletes share one transaction; keep every ID on failure so cleanup can be retried.
     */
    public void cleanUp() {
        noCallerTransaction();
        committed.executeWithoutResult(status -> {
            for (var order : orders.values()) {
                jdbc.update("delete from tb_payment where order_id = ?", order.id());
            }
            for (var order : orders.values()) {
                for (var item : order.items()) {
                    jdbc.update("delete from tb_order_item where id = ? and order_id = ?", item.id(), order.id());
                }
            }
            for (var orderId : orders.keySet()) {
                jdbc.update("delete from tb_order where id = ?", orderId);
            }
            for (var pair : pairs) {
                jdbc.update("delete from tb_product_category where product_id = ? and category_id = ?",
                        pair.productId(), pair.categoryId());
            }
            for (var productId : products) {
                jdbc.update("delete from tb_product where id = ?", productId);
            }
            for (var categoryId : categories) {
                jdbc.update("delete from tb_category where id = ?", categoryId);
            }
            for (var accountId : accounts) {
                jdbc.update("delete from tb_user_account_role where user_account_id = ?", accountId);
                jdbc.update("delete from tb_user_account where id = ?", accountId);
            }
        });
        orders.clear();
        pairs.clear();
        products.clear();
        categories.clear();
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
            for (var index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey(), "Fixture insert must return an ID").longValue();
    }

    private static String disposableName(String role) {
        return "c009-" + role + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void noCallerTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Customer fixture setup/cleanup requires no caller transaction");
        }
    }

    private static void requireMoney(BigDecimal value, boolean strictlyPositive) {
        Objects.requireNonNull(value);
        if (value.scale() != 2 || value.precision() > 19
                || (strictlyPositive ? value.signum() <= 0 : value.signum() < 0)) {
            throw new IllegalArgumentException("Use exact money with two decimal places within the existing schema");
        }
    }

    // Credentials stay behind a symbolic enum; generated record toString never includes password/hash.
    public record Account(long id, String name, LegacyCredential credential, Set<UserRole> roles) {
        public Account {
            Objects.requireNonNull(credential);
            roles = Set.copyOf(roles);
        }

        public String password() {
            return credential.password();
        }

        public String passwordHash() {
            return credential.passwordHash();
        }
    }

    public record AccessAccounts(Account client, Account admin, Account both, Account none) { }
    public record CategoryData(long id, String name) { }
    public record ProductData(long id, String name, BigDecimal currentPrice) { }
    public record ProductCategory(long productId, long categoryId) { }
    public record ItemInput(long productId, int quantity, BigDecimal unitPrice) {
        public ItemInput {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Use a positive historical quantity");
            }
            requireMoney(unitPrice, true);
        }
    }
    public record ItemData(long id, long productId, int position, int quantity, BigDecimal unitPrice) { }
    public record PaymentData(long orderId, Instant occurredAt) { }
    public record OrderData(long id, long buyerId, Instant createdAt, String status,
                            List<ItemData> items, PaymentData payment) {
        public OrderData {
            items = List.copyOf(items);
        }
    }
    public record Scenario(AccessAccounts access, CategoryData category, List<ProductData> products,
                           OrderData waiting, OrderData paid, BigDecimal waitingTotal, BigDecimal paidTotal) {
        public Scenario {
            products = List.copyOf(products);
        }
    }
    public record Ownership(Set<Long> accountIds, Set<Long> categoryIds, Set<Long> productIds,
                            Set<ProductCategory> productCategories, Set<Long> orderIds, Set<Long> itemIds) {
        public Ownership {
            accountIds = Set.copyOf(accountIds);
            categoryIds = Set.copyOf(categoryIds);
            productIds = Set.copyOf(productIds);
            productCategories = Set.copyOf(productCategories);
            orderIds = Set.copyOf(orderIds);
            itemIds = Set.copyOf(itemIds);
        }
    }
}
