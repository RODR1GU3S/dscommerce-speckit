package com.devsuperior.dscommerce.fixtures;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit JDBC-only observations for T006 databases. No Spring bean, pool, EntityManager, domain
 * helper, normalization, cached connection or cached snapshot. Construction opens nothing.
 * Finish all writers before snapshot() and keep cleanup outside the observation window: the eight
 * SELECTs share a READ_COMMITTED transaction, not an atomic image under concurrent writes.
 */
public final class CustomerRegistrationDatabaseProbe {

    private static final List<Table> TABLES = List.of(
            new Table("TB_USER_ACCOUNT", "ID"),
            new Table("TB_USER_ACCOUNT_ROLE", "USER_ACCOUNT_ID, ROLE"),
            new Table("TB_CATEGORY", "ID"),
            new Table("TB_PRODUCT", "ID"),
            new Table("TB_PRODUCT_CATEGORY", "PRODUCT_ID, CATEGORY_ID"),
            new Table("TB_ORDER", "ID"),
            new Table("TB_ORDER_ITEM", "ORDER_ID, POSITION, ID"),
            new Table("TB_PAYMENT", "ORDER_ID"));

    private final String databaseUrl;
    private final String databaseName;

    public CustomerRegistrationDatabaseProbe(String databaseUrl) {
        if (databaseUrl == null || !databaseUrl.matches(
                "jdbc:h2:mem:customer-registration-test-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                        + "[0-9a-f]{4}-[0-9a-f]{12};DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")) {
            throw new IllegalArgumentException("Probe requires the isolated CustomerRegistrationTestConfig URL");
        }
        this.databaseUrl = databaseUrl;
        databaseName = databaseUrl.split(";", 2)[0];
    }

    /**
     * New physical driver connection per call, even inside a caller's application transaction.
     * Caller owns/ closes it with try-with-resources. IFEXISTS avoids creating a different empty DB.
     * Observe the driver's initial isolation/autocommit; never set them to manufacture a PASS.
     */
    public Connection openConnection() throws SQLException {
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(databaseUrl + ";IFEXISTS=TRUE", "sa", "");
            if (!databaseName.equals(connection.getMetaData().getURL()) || !connection.getAutoCommit()
                    || connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new SQLException("Independent connection has unexpected database or transaction settings");
            }
            return connection;
        } catch (SQLException | RuntimeException failure) {
            if (connection != null) {
                try { connection.close(); } catch (SQLException closeFailure) { /* Failure stays explicit below. */ }
            }
            // Driver causes may contain SQL, credentials or account values; do not attach them.
            throw new SQLException("Independent customer registration connection could not be opened");
        }
    }

    /**
     * All actual columns/rows, including future nullable registration columns when they exist.
     * Missing required table/order column or unsupported value type fails, never an empty fallback.
     * Registration columns not yet mapped remain absent from the column list, not invented NULLs.
     */
    public Snapshot snapshot() {
        return read(connection -> {
            var tables = new LinkedHashMap<String, TableSnapshot>();
            for (var table : TABLES) {
                tables.put(table.name(), readTable(connection, table));
            }
            return new Snapshot(tables);
        });
    }

    /** Runtime connection identity/settings and real columns, keys, indexes and constraint names. */
    public Metadata metadata() {
        return read(connection -> {
            var metadata = connection.getMetaData();
            var sessionIds = query(connection, "select session_id()", row -> row.getLong(1));
            if (sessionIds.size() != 1) {
                throw new SQLException("Expected one independent connection identity");
            }
            var info = new ConnectionInfo(metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion(),
                    metadata.getDriverName(), metadata.getDriverVersion(), connection.getTransactionIsolation(),
                    connection.getAutoCommit(), connection.isReadOnly(), sessionIds.getFirst());
            var tables = new LinkedHashMap<String, TableMetadata>();
            for (var table : TABLES) {
                tables.put(table.name(), readTableMetadata(connection, table.name()));
            }
            return new Metadata(info, tables);
        });
    }

    /** Comparison diagnostics list only table names, never row values, SQL, passwords or hashes. */
    public static void assertUnchanged(Snapshot before, Snapshot after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        var changed = new ArrayList<String>();
        for (var table : TABLES) {
            if (!Objects.equals(before.tables().get(table.name()), after.tables().get(table.name()))) {
                changed.add(table.name());
            }
        }
        if (!changed.isEmpty()) {
            throw new AssertionError("Observed database state changed: " + String.join(", ", changed));
        }
    }

    private static TableSnapshot readTable(Connection connection, Table table) throws SQLException {
        // Identifiers/order come only from the fixed allowlist, never from a supplied field/value.
        try (var statement = connection.prepareStatement(
                "select * from PUBLIC." + table.name() + " order by " + table.orderBy());
             var rows = statement.executeQuery()) {
            var metadata = rows.getMetaData();
            var columns = new ArrayList<SnapshotColumn>();
            for (var index = 1; index <= metadata.getColumnCount(); index++) {
                columns.add(new SnapshotColumn(metadata.getColumnName(index), metadata.getColumnType(index),
                        metadata.getColumnTypeName(index)));
            }
            var observed = new ArrayList<Row>();
            while (rows.next()) {
                var values = new LinkedHashMap<String, JdbcValue>();
                for (var index = 1; index <= columns.size(); index++) {
                    if (values.put(columns.get(index - 1).name(), detach(rows.getObject(index))) != null) {
                        throw new SQLException("Duplicate observed column name");
                    }
                }
                observed.add(new Row(values));
            }
            return new TableSnapshot(columns, observed);
        }
    }

    private static TableMetadata readTableMetadata(Connection connection, String table) throws SQLException {
        var metadata = connection.getMetaData();
        var catalog = connection.getCatalog();
        var schema = "PUBLIC";
        var columns = new ArrayList<Column>();
        // getColumns accepts a pattern: filter exact table/schema so '_' cannot broaden this result.
        try (var rows = metadata.getColumns(catalog, schema, table, null)) {
            while (rows.next()) {
                if (table.equals(rows.getString("TABLE_NAME")) && schema.equals(rows.getString("TABLE_SCHEM"))) {
                    columns.add(new Column(rows.getString("COLUMN_NAME"), rows.getInt("DATA_TYPE"),
                            rows.getString("TYPE_NAME"), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"),
                            rows.getInt("NULLABLE"), rows.getInt("ORDINAL_POSITION"), rows.getString("COLUMN_DEF"),
                            rows.getString("IS_AUTOINCREMENT"), rows.getString("IS_GENERATEDCOLUMN")));
                }
            }
        }
        if (columns.isEmpty()) {
            throw new SQLException("Required table metadata is absent");
        }
        columns.sort(Comparator.comparingInt(Column::position));
        var primaryKeys = new ArrayList<PrimaryKeyColumn>();
        try (var rows = metadata.getPrimaryKeys(catalog, schema, table)) {
            while (rows.next()) {
                primaryKeys.add(new PrimaryKeyColumn(rows.getString("PK_NAME"), rows.getInt("KEY_SEQ"),
                        rows.getString("COLUMN_NAME")));
            }
        }
        var foreignKeys = new ArrayList<ForeignKeyColumn>();
        try (var rows = metadata.getImportedKeys(catalog, schema, table)) {
            while (rows.next()) {
                foreignKeys.add(new ForeignKeyColumn(rows.getString("FK_NAME"), rows.getInt("KEY_SEQ"),
                        rows.getString("FKCOLUMN_NAME"), rows.getString("PKTABLE_SCHEM"),
                        rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"),
                        rows.getInt("UPDATE_RULE"), rows.getInt("DELETE_RULE")));
            }
        }
        var indexes = new ArrayList<IndexColumn>();
        try (var rows = metadata.getIndexInfo(catalog, schema, table, false, false)) {
            while (rows.next()) {
                if (rows.getShort("TYPE") != DatabaseMetaData.tableIndexStatistic) {
                    indexes.add(new IndexColumn(rows.getString("INDEX_NAME"), rows.getBoolean("NON_UNIQUE"),
                            rows.getInt("ORDINAL_POSITION"), rows.getString("COLUMN_NAME"),
                            rows.getString("ASC_OR_DESC")));
                }
            }
        }
        // Constraint names are distinct from H2's backing index names; retain both without guessing.
        var constraints = new ArrayList<ConstraintColumn>();
        try (var statement = connection.prepareStatement(
                "select tc.CONSTRAINT_NAME, tc.CONSTRAINT_TYPE, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION "
                        + "from INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "left join INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu "
                        + "on kcu.CONSTRAINT_CATALOG = tc.CONSTRAINT_CATALOG "
                        + "and kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA "
                        + "and kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                        + "where tc.TABLE_SCHEMA = ? and tc.TABLE_NAME = ? "
                        + "order by tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    constraints.add(new ConstraintColumn(rows.getString("CONSTRAINT_NAME"),
                            rows.getString("CONSTRAINT_TYPE"), rows.getString("COLUMN_NAME"),
                            rows.getObject("ORDINAL_POSITION", Integer.class)));
                }
            }
        }
        return new TableMetadata(columns, primaryKeys, foreignKeys, indexes, constraints);
    }

    private <T> T read(SqlRead<T> operation) {
        try (var connection = openConnection()) {
            connection.setReadOnly(true); // JDBC hint; the probe's statements themselves are read-only.
            connection.setAutoCommit(false);
            try {
                var result = operation.read(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) { /* No successful result returned. */ }
                throw failure;
            }
        } catch (SQLException | RuntimeException failure) {
            throw new IllegalStateException("Independent customer registration database observation failed");
        }
    }

    /** Detach LOBs/dates before closing JDBC resources; preserve text, NULLs and binary content. */
    private static JdbcValue detach(Object value) throws SQLException {
        if (value instanceof Clob clob) {
            try { return new JdbcValue(clob.getSubString(1, Math.toIntExact(clob.length()))); }
            finally { clob.free(); }
        }
        if (value instanceof Blob blob) {
            try { return new JdbcValue(blob.getBytes(1, Math.toIntExact(blob.length()))); }
            finally { blob.free(); }
        }
        if (value instanceof java.sql.Timestamp timestamp) { return new JdbcValue(timestamp.toInstant()); }
        if (value instanceof OffsetDateTime dateTime) { return new JdbcValue(dateTime.toInstant()); }
        if (value instanceof java.sql.Date date) { return new JdbcValue(date.toLocalDate()); }
        if (value instanceof java.sql.Time time) { return new JdbcValue(time.toLocalTime()); }
        if (value == null || value instanceof String || value instanceof byte[] || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof BigDecimal || value instanceof Float || value instanceof Double
                || value instanceof Instant || value instanceof LocalDate || value instanceof LocalTime
                || value instanceof OffsetTime) {
            return new JdbcValue(value);
        }
        throw new SQLException("Unsupported observed JDBC value type");
    }

    private static <T> List<T> query(Connection connection, String sql, RowRead<T> mapper) throws SQLException {
        try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
            var values = new ArrayList<T>();
            while (rows.next()) { values.add(mapper.read(rows)); }
            return List.copyOf(values);
        }
    }

    @FunctionalInterface
    private interface SqlRead<T> { T read(Connection connection) throws SQLException; }
    @FunctionalInterface
    private interface RowRead<T> { T read(ResultSet row) throws SQLException; }
    private record Table(String name, String orderBy) { }

    /** Explicit value access is for assertions only; never log/serialize returned secrets. */
    public static final class JdbcValue {
        private final Object content;

        private JdbcValue(Object content) {
            this.content = content instanceof byte[] bytes ? bytes.clone() : content;
        }

        public Object value() { return content instanceof byte[] bytes ? bytes.clone() : content; }
        public boolean isNull() { return content == null; }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof JdbcValue that && Objects.deepEquals(content, that.content);
        }

        @Override
        public int hashCode() { return content instanceof byte[] bytes ? Arrays.hashCode(bytes) : Objects.hashCode(content); }

        @Override
        public String toString() { return "JdbcValue[redacted]"; }
    }

    public record SnapshotColumn(String name, int jdbcType, String typeName) { }
    public record Row(Map<String, JdbcValue> values) {
        public Row { values = Map.copyOf(values); }
        @Override
        public String toString() { return "Row[values=redacted]"; }
    }
    public record TableSnapshot(List<SnapshotColumn> columns, List<Row> rows) {
        public TableSnapshot {
            columns = List.copyOf(columns);
            rows = List.copyOf(rows);
            if (columns.isEmpty()) { throw new IllegalArgumentException("Snapshot requires actual column metadata"); }
        }
        @Override
        public String toString() { return "TableSnapshot[rows=" + rows.size() + ", values=redacted]"; }
    }
    public record Snapshot(Map<String, TableSnapshot> tables) {
        public Snapshot {
            tables = Map.copyOf(tables);
            if (!tables.keySet().equals(TABLES.stream().map(Table::name).collect(java.util.stream.Collectors.toSet()))) {
                throw new IllegalArgumentException("Snapshot requires all eight observed tables");
            }
        }
        @Override
        public String toString() { return "CustomerRegistrationSnapshot[tables=" + tables.size() + ", values=redacted]"; }
    }
    public record ConnectionInfo(String databaseProduct, String databaseVersion, String driver, String driverVersion,
                                 int isolation, boolean autoCommit, boolean readOnly, long sessionId) { }
    public record Column(String name, int jdbcType, String typeName, int size, int decimalDigits, int nullable,
                         int position, String defaultValue, String autoIncrement, String generated) {
        @Override
        public String toString() { return "Column[name=" + name + ", jdbcType=" + jdbcType + ", default=redacted]"; }
    }
    public record PrimaryKeyColumn(String name, int position, String column) { }
    public record ForeignKeyColumn(String name, int position, String column, String referencedSchema,
                                   String referencedTable, String referencedColumn, int updateRule, int deleteRule) { }
    public record IndexColumn(String name, boolean nonUnique, int position, String column, String direction) { }
    /** One row per key column; non-key constraints retain null column/position rather than disappear. */
    public record ConstraintColumn(String name, String type, String column, Integer position) { }
    public record TableMetadata(List<Column> columns, List<PrimaryKeyColumn> primaryKeys,
                                List<ForeignKeyColumn> foreignKeys, List<IndexColumn> indexes,
                                List<ConstraintColumn> constraints) {
        public TableMetadata {
            columns = List.copyOf(columns);
            primaryKeys = List.copyOf(primaryKeys);
            foreignKeys = List.copyOf(foreignKeys);
            indexes = List.copyOf(indexes);
            constraints = List.copyOf(constraints);
        }
    }
    public record Metadata(ConnectionInfo connection, Map<String, TableMetadata> tables) {
        public Metadata { Objects.requireNonNull(connection); tables = Map.copyOf(tables); }
    }
}
