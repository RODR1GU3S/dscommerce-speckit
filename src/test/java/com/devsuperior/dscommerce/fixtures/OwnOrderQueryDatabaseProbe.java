package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlEvent;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Independent committed JDBC observations for the isolated AdminProductTestConfig database.
 * Reuses the existing domain snapshot and adds all account columns and account/role rows.
 * The two read transactions are not a global snapshot under concurrent writers; callers finish
 * setup/login first and keep cleanup outside observeRead. This probe never writes domain data.
 */
public final class OwnOrderQueryDatabaseProbe {

    private final AdminProductDatabaseProbe domain;

    public OwnOrderQueryDatabaseProbe(String databaseUrl) {
        domain = new AdminProductDatabaseProbe(databaseUrl);
    }

    /** A new driver connection, without the application pool or transaction-bound EntityManager. */
    public Connection openConnection() throws SQLException {
        return domain.openConnection();
    }

    public Snapshot snapshot() {
        try {
            var data = domain.snapshot();
            try (var connection = openConnection()) {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                try {
                    var accounts = new ArrayList<AccountState>();
                    try (var statement = connection.prepareStatement(
                            "select id, name, password_hash from tb_user_account order by id");
                         var rows = statement.executeQuery()) {
                        while (rows.next()) {
                            accounts.add(new AccountState(rows.getLong("id"), rows.getString("name"),
                                    rows.getString("password_hash")));
                        }
                    }
                    var roles = new ArrayList<AccountRole>();
                    try (var statement = connection.prepareStatement(
                            "select user_account_id, role from tb_user_account_role order by user_account_id, role");
                         var rows = statement.executeQuery()) {
                        while (rows.next()) {
                            roles.add(new AccountRole(rows.getLong("user_account_id"),
                                    UserRole.valueOf(rows.getString("role"))));
                        }
                    }
                    connection.commit();
                    return new Snapshot(data, accounts, roles);
                } catch (SQLException | RuntimeException failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        } catch (SQLException | RuntimeException failure) {
            // Neither a JDBC cause nor its parameters/credentials belong in comparison diagnostics.
            throw new IllegalStateException("Independent own-order snapshot could not be read");
        }
    }

    /**
     * Captures only the operation, after setup/login and before caller cleanup.
     * StatementInspector does not see these snapshots or other direct JDBC statements.
     * A failing operation propagates its exception; it must not become an observation success.
     */
    public <T> Observation<T> observeRead(SqlObservation sql, Supplier<T> operation) {
        Objects.requireNonNull(sql);
        Objects.requireNonNull(operation);
        var before = snapshot();
        sql.clear();
        var result = operation.get();
        var events = sql.events();
        var after = snapshot();
        return new Observation<>(result, before, after, events);
    }

    /** Only table names are reported, never account values, hashes, SQL, JWTs or supplied results. */
    public static void assertUnchanged(Snapshot before, Snapshot after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        var changed = new ArrayList<String>();
        if (!before.data.products().equals(after.data.products())) { changed.add("tb_product"); }
        if (!before.data.categories().equals(after.data.categories())) { changed.add("tb_category"); }
        if (!before.data.associations().equals(after.data.associations())) { changed.add("tb_product_category"); }
        if (!before.accounts.equals(after.accounts)) { changed.add("tb_user_account"); }
        if (!before.roles.equals(after.roles)) { changed.add("tb_user_account_role"); }
        if (!before.data.orders().equals(after.data.orders())) { changed.add("tb_order"); }
        if (!before.data.items().equals(after.data.items())) { changed.add("tb_order_item"); }
        if (!changed.isEmpty()) {
            throw new AssertionError("Observed database state changed: " + String.join(", ", changed));
        }
    }

    public record AccountRole(long accountId, UserRole role) { }

    // Opaque account values participate in equality; there are no public credential accessors.
    private record AccountState(long id, String name, String passwordHash) {
        @Override
        public String toString() { return "AccountState[credentials=redacted]"; }
    }

    public static final class Snapshot {
        private final AdminProductDatabaseProbe.Snapshot data;
        private final List<AccountState> accounts;
        private final List<AccountRole> roles;

        private Snapshot(AdminProductDatabaseProbe.Snapshot data, List<AccountState> accounts,
                         List<AccountRole> roles) {
            this.data = Objects.requireNonNull(data);
            this.accounts = List.copyOf(accounts);
            this.roles = List.copyOf(roles);
        }

        public AdminProductDatabaseProbe.Snapshot data() { return data; }
        public List<Long> accountIds() { return accounts.stream().map(AccountState::id).toList(); }
        public List<AccountRole> roles() { return roles; }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Snapshot that
                    && data.equals(that.data) && accounts.equals(that.accounts) && roles.equals(that.roles);
        }

        @Override
        public int hashCode() { return Objects.hash(data, accounts, roles); }

        @Override
        public String toString() {
            return "OwnOrderQuerySnapshot[products=" + data.products().size()
                    + ", categories=" + data.categories().size() + ", associations=" + data.associations().size()
                    + ", accounts=" + accounts.size() + ", roles=" + roles.size()
                    + ", orders=" + data.orders().size() + ", items=" + data.items().size()
                    + ", credentials=redacted]";
        }
    }

    public record Observation<T>(T result, Snapshot before, Snapshot after, List<SqlEvent> events) {
        public Observation {
            Objects.requireNonNull(before);
            Objects.requireNonNull(after);
            events = List.copyOf(events);
        }

        /**
         * Requires unchanged committed state and SELECT-only observed Hibernate SQL.
         * Unknown/non-SELECT statements fail closed; this is not a general SQL parser.
         * Equal snapshots cannot detect a transient direct-JDBC write restored inside the window.
         */
        public void assertNoWrites() {
            assertUnchanged(before, after);
            for (var event : events) {
                var statement = event.sql() == null ? "" : event.sql().replaceFirst(
                        "(?s)^(?:\\s|/\\*.*?\\*/|--[^\\r\\n]*(?:\\r?\\n|$))*", "");
                if (!statement.matches("(?is)^select\\b.*")) {
                    throw new AssertionError("Non-SELECT Hibernate SQL observed in the read window");
                }
            }
        }

        @Override
        public String toString() {
            return "OwnOrderQueryObservation[before=" + before + ", after=" + after
                    + ", sqlEvents=" + events.size() + ", result=redacted]";
        }
    }
}
