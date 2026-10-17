package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToLongFunction;

/** Independent JDBC reads only: no application pool, EntityManager or thread-bound connection. */
public final class AdminProductDatabaseProbe {

    private final DriverManagerDataSource independent;

    public AdminProductDatabaseProbe(String databaseUrl) {
        if (databaseUrl == null || !databaseUrl.startsWith("jdbc:h2:mem:admin-product-test-")) {
            throw new IllegalArgumentException("Probe requires the isolated admin product H2 database");
        }
        independent = new DriverManagerDataSource(databaseUrl, "sa", "");
    }

    /** Each call obtains a new driver connection, even inside an application transaction. */
    public Connection openConnection() throws SQLException {
        return independent.getConnection();
    }

    public Snapshot snapshot() {
        return read(connection -> new Snapshot(
                query(connection, "select id, name, description, image, price from tb_product order by id",
                        row -> new ProductRow(row.getLong("id"), row.getString("name"),
                                row.getString("description"), row.getString("image"), row.getBigDecimal("price"))),
                query(connection, "select id, name from tb_category order by id",
                        row -> new CategoryRow(row.getLong("id"), row.getString("name"))),
                uniquePairs(query(connection,
                        "select product_id, category_id from tb_product_category order by product_id, category_id",
                        row -> new AdminProductTestFixture.ProductCategory(row.getLong("product_id"),
                                row.getLong("category_id")))),
                query(connection, "select id, buyer_id, created_at, status from tb_order order by id",
                        row -> new OrderRow(row.getLong("id"), row.getLong("buyer_id"),
                                row.getTimestamp("created_at").toInstant(), row.getString("status"))),
                query(connection, "select id, order_id, position, product_id, quantity, unit_price "
                                + "from tb_order_item order by order_id, position, id",
                        row -> new ItemRow(row.getLong("id"), row.getLong("order_id"), row.getInt("position"),
                                row.getLong("product_id"), row.getInt("quantity"), row.getBigDecimal("unit_price")))));
    }

    public List<Long> accountIds() {
        return read(connection -> query(connection, "select id from tb_user_account order by id",
                row -> row.getLong("id")));
    }

    public Set<UserRole> roles(long accountId) {
        return read(connection -> {
            try (var statement = connection.prepareStatement(
                    "select role from tb_user_account_role where user_account_id = ? order by role")) {
                statement.setLong(1, accountId);
                try (var rows = statement.executeQuery()) {
                    var roles = new HashSet<UserRole>();
                    while (rows.next()) {
                        roles.add(UserRole.valueOf(rows.getString("role")));
                    }
                    return Set.copyOf(roles);
                }
            }
        });
    }

    private static Set<AdminProductTestFixture.ProductCategory> uniquePairs(
            List<AdminProductTestFixture.ProductCategory> pairs) {
        var unique = Set.copyOf(pairs);
        if (unique.size() != pairs.size()) {
            throw new IllegalStateException("Duplicate persisted product/category pair");
        }
        return unique;
    }

    private <T> T read(SqlRead<T> action) {
        try (var connection = openConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                var result = action.read(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Independent fixture database read failed", failure);
        }
    }

    private static <T> List<T> query(Connection connection, String sql, RowRead<T> mapper) throws SQLException {
        try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
            var values = new ArrayList<T>();
            while (rows.next()) {
                values.add(mapper.read(rows));
            }
            return List.copyOf(values);
        }
    }

    /** Compare complete, consecutive pages against fixture IDs; never obtains the expected IDs from a query. */
    public static <T> List<Long> assertCompletePages(List<PageResponseDTO<T>> pages, int size,
                                                    List<Long> expectedIds, ToLongFunction<T> id) {
        check(size > 0, "Page size must be positive");
        check(new HashSet<>(expectedIds).size() == expectedIds.size(), "Fixture oracle must have unique IDs");
        int totalPages = (int) ((expectedIds.size() + (long) size - 1) / size);
        check(pages.size() >= Math.max(1, totalPages), "Complete navigation is required");
        var actual = new ArrayList<Long>();
        for (int index = 0; index < pages.size(); index++) {
            var page = pages.get(index);
            check(page.page() == index, "Pages must be consecutive starting at zero");
            check(page.size() == size, "Requested page size must remain constant");
            check(page.totalElements() == expectedIds.size(), "Incorrect totalElements");
            check(page.totalPages() == totalPages, "Incorrect totalPages");
            int expectedSize = (int) Math.min(size, Math.max(0L, expectedIds.size() - (long) index * size));
            check(page.content().size() == expectedSize, "Incorrect page content size");
            page.content().forEach(value -> actual.add(id.applyAsLong(value)));
        }
        check(new HashSet<>(actual).size() == actual.size(), "Product repeated within/across pages");
        check(actual.equals(expectedIds), "Navigation lost products or changed their order/IDs");
        return List.copyOf(actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface SqlRead<T> { T read(Connection connection) throws SQLException; }

    @FunctionalInterface
    private interface RowRead<T> { T read(ResultSet row) throws SQLException; }

    public record ProductRow(long id, String name, String description, String image, BigDecimal price) { }
    public record CategoryRow(long id, String name) { }
    public record OrderRow(long id, long buyerId, Instant createdAt, String status) { }
    public record ItemRow(long id, long orderId, int position, long productId, int quantity, BigDecimal unitPrice) { }

    public record Snapshot(List<ProductRow> products, List<CategoryRow> categories,
                           Set<AdminProductTestFixture.ProductCategory> associations,
                           List<OrderRow> orders, List<ItemRow> items) {
        public Snapshot {
            products = List.copyOf(products);
            categories = List.copyOf(categories);
            associations = Set.copyOf(associations);
            orders = List.copyOf(orders);
            items = List.copyOf(items);
        }
    }

    /** Register explicitly through a test HibernatePropertiesCustomizer. Does not observe direct JDBC or bind values. */
    public static final class SqlObservation implements StatementInspector {
        private final List<SqlEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            events.add(new SqlEvent(sql, TransactionSynchronizationManager.isActualTransactionActive()));
            return sql;
        }

        public List<SqlEvent> events() { return List.copyOf(events); }
        public void clear() { events.clear(); }
    }

    public record SqlEvent(String sql, boolean transactionActive) { }
}
