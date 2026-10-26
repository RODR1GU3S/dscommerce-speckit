package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.UserAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserAccountInsertRepositoryImpl implements UserAccountInsertRepository {

    private static final String TABLE = "TB_USER_ACCOUNT";
    private static final String METADATA_UNAVAILABLE = "Customer registration identity metadata is unavailable";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void insert(UserAccount account) {
        if (account == null || account.getId() != null) {
            throw new IllegalArgumentException("A new account without an identity is required");
        }
        entityManager.persist(account);
    }

    @Override
    public RegistrationIdentityMetadata registrationIdentityMetadata() {
        try {
            return entityManager.unwrap(Session.class).doReturningWork(this::readIdentityMetadata);
        } catch (RuntimeException failure) {
            // Do not retain driver messages, SQL or values in a public-facing exception chain.
            throw new IllegalStateException(METADATA_UNAVAILABLE);
        }
    }

    private RegistrationIdentityMetadata readIdentityMetadata(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        String product = metadata.getDatabaseProductName();
        String version = metadata.getDatabaseProductVersion();
        require("H2".equals(product) && version != null
                && (version.equals("2.2.224") || version.startsWith("2.2.224 ")));
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        require(catalog != null && !catalog.isBlank() && schema != null && !schema.isBlank());
        var columns = readColumns(metadata, catalog, schema);
        var constraints = readUniqueConstraints(connection, catalog, schema);
        var indexes = readUniqueIndexes(metadata, catalog, schema);
        var name = identityKey(columns, constraints, indexes, "NAME", "UK_USER_ACCOUNT_NAME",
                Types.VARCHAR, 255, DatabaseMetaData.columnNoNulls);
        var emailKey = identityKey(columns, constraints, indexes, "EMAIL_KEY", "UK_USER_ACCOUNT_EMAIL_KEY",
                Types.VARBINARY, 1016, DatabaseMetaData.columnNullable);
        return new RegistrationIdentityMetadata(product, version, catalog, schema, TABLE, name, emailKey);
    }

    private static Map<String, ColumnDefinition> readColumns(DatabaseMetaData metadata, String catalog,
                                                             String schema) throws SQLException {
        var columns = new LinkedHashMap<String, ColumnDefinition>();
        try (var rows = metadata.getColumns(catalog, schema, TABLE, null)) {
            while (rows.next()) {
                // getColumns takes patterns: underscores must not admit another physical table.
                if (!catalog.equals(rows.getString("TABLE_CAT")) || !schema.equals(rows.getString("TABLE_SCHEM"))
                        || !TABLE.equals(rows.getString("TABLE_NAME"))) { continue; }
                String column = rows.getString("COLUMN_NAME");
                Integer nullable = rows.getObject("NULLABLE", Integer.class);
                require(column != null && !column.isBlank() && nullable != null);
                var previous = columns.put(column, new ColumnDefinition(rows.getInt("DATA_TYPE"),
                        rows.getString("TYPE_NAME"), rows.getInt("COLUMN_SIZE"), nullable));
                require(previous == null);
            }
        }
        return columns;
    }

    private static Map<String, List<KeyColumn>> readUniqueConstraints(Connection connection, String catalog,
                                                                      String schema) throws SQLException {
        var constraints = new LinkedHashMap<String, List<KeyColumn>>();
        try (var statement = connection.prepareStatement(
                "select tc.CONSTRAINT_NAME, kcu.COLUMN_NAME, kcu.ORDINAL_POSITION "
                        + "from INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc "
                        + "join INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu "
                        + "on kcu.CONSTRAINT_CATALOG = tc.CONSTRAINT_CATALOG "
                        + "and kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA "
                        + "and kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME "
                        + "where tc.TABLE_CATALOG = ? and tc.TABLE_SCHEMA = ? and tc.TABLE_NAME = ? "
                        + "and tc.CONSTRAINT_TYPE = 'UNIQUE'")) {
            statement.setString(1, catalog);
            statement.setString(2, schema);
            statement.setString(3, TABLE);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString("CONSTRAINT_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    int position = rows.getInt("ORDINAL_POSITION");
                    require(name != null && !name.isBlank() && column != null && position > 0);
                    constraints.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new KeyColumn(column, position));
                }
            }
        }
        return constraints;
    }

    private static Map<String, List<KeyColumn>> readUniqueIndexes(DatabaseMetaData metadata, String catalog,
                                                                  String schema) throws SQLException {
        var indexes = new LinkedHashMap<String, List<KeyColumn>>();
        try (var rows = metadata.getIndexInfo(catalog, schema, TABLE, true, false)) {
            while (rows.next()) {
                if (rows.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) { continue; }
                boolean nonUnique = rows.getBoolean("NON_UNIQUE");
                require(!rows.wasNull());
                if (nonUnique) { continue; }
                require(catalog.equals(rows.getString("TABLE_CAT")) && schema.equals(rows.getString("TABLE_SCHEM"))
                        && TABLE.equals(rows.getString("TABLE_NAME")));
                String name = rows.getString("INDEX_NAME");
                String column = rows.getString("COLUMN_NAME");
                int position = rows.getInt("ORDINAL_POSITION");
                require(name != null && !name.isBlank() && column != null && position > 0);
                indexes.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new KeyColumn(column, position));
            }
        }
        return indexes;
    }

    private static IdentityKey identityKey(Map<String, ColumnDefinition> columns,
                                           Map<String, List<KeyColumn>> constraints, Map<String, List<KeyColumn>> indexes,
                                           String column, String expectedConstraint, int jdbcType, int capacity,
                                           int nullability) throws SQLException {
        var definition = columns.get(column);
        require(definition != null);
        require(definition.jdbcType() == jdbcType && definition.capacity() == capacity
                && definition.nullability() == nullability && definition.typeName() != null
                && !definition.typeName().isBlank());
        var constraintNames = matchingKeys(constraints, column);
        var indexNames = matchingKeys(indexes, column);
        require(constraintNames.size() == 1 && constraintNames.getFirst().equals(expectedConstraint));
        require(indexNames.size() == 1);
        return new IdentityKey(column, definition.jdbcType(), definition.typeName(), definition.capacity(),
                nullability == DatabaseMetaData.columnNullable, constraintNames.getFirst(), indexNames.getFirst());
    }

    private static List<String> matchingKeys(Map<String, List<KeyColumn>> keys, String column) {
        var expected = List.of(new KeyColumn(column, 1));
        return keys.entrySet().stream().filter(entry -> entry.getValue().stream()
                        .sorted(Comparator.comparingInt(KeyColumn::position)).toList().equals(expected))
                .map(Map.Entry::getKey).toList();
    }

    private static void require(boolean condition) throws SQLException {
        if (!condition) { throw new SQLException(METADATA_UNAVAILABLE); }
    }

    private record ColumnDefinition(int jdbcType, String typeName, int capacity, int nullability) { }
    private record KeyColumn(String column, int position) { }
}
