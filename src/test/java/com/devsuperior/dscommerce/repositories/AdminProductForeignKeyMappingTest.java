package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** T058: generated schema, through independent JDBC; no deletion service or HTTP acceptance. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductForeignKeyMappingTest {
    private static final String TABLES = "('TB_PRODUCT','TB_CATEGORY','TB_PRODUCT_CATEGORY','TB_ORDER','TB_ORDER_ITEM')";
    @Autowired private Environment environment;

    @ParameterizedTest(name = "{0}.{1} -> {2}: {3}")
    @CsvSource({
            "TB_ORDER_ITEM,PRODUCT_ID,TB_PRODUCT,FK_ORDER_ITEM_PRODUCT",
            "TB_PRODUCT_CATEGORY,PRODUCT_ID,TB_PRODUCT,FK_PRODUCT_CATEGORY_PRODUCT",
            "TB_PRODUCT_CATEGORY,CATEGORY_ID,TB_CATEGORY,FK_PRODUCT_CATEGORY_CATEGORY"
    })
    void generatedForeignKeyHasStableNameAndRemainsRequiredAndRestrictive(
            String table, String column, String parent, String expectedName) throws Exception {
        try (var connection = independentConnection()) {
            var metadata = connection.getMetaData();
            var keys = new ArrayList<List<String>>();
            try (var rows = metadata.getImportedKeys(null, "PUBLIC", table)) {
                while (rows.next()) {
                    if (!column.equals(rows.getString("FKCOLUMN_NAME"))) continue;
                    assertThat(rows.getString("FKTABLE_NAME")).isEqualTo(table);
                    assertThat(rows.getString("PKTABLE_NAME")).isEqualTo(parent);
                    assertThat(rows.getString("PKCOLUMN_NAME")).isEqualTo("ID");
                    assertThat(rows.getShort("KEY_SEQ")).isEqualTo((short) 1);
                    assertThat(rows.getShort("DELETE_RULE")).isIn(
                            (short) DatabaseMetaData.importedKeyRestrict, (short) DatabaseMetaData.importedKeyNoAction);
                    assertThat(rows.getShort("UPDATE_RULE")).isIn(
                            (short) DatabaseMetaData.importedKeyRestrict, (short) DatabaseMetaData.importedKeyNoAction);
                    assertThat(rows.getShort("DEFERRABILITY")).isEqualTo((short) DatabaseMetaData.importedKeyNotDeferrable);
                    keys.add(List.of(rows.getString("FK_NAME"), rows.getString("FKCOLUMN_NAME"),
                            rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"),
                            rows.getString("DELETE_RULE"), rows.getString("UPDATE_RULE"), rows.getString("DEFERRABILITY")));
                }
            }
            assertThat(keys).hasSize(1);
            try (var rows = metadata.getColumns(null, "PUBLIC", table, column)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNoNulls);
                assertThat(rows.next()).isFalse();
            }
            System.out.printf("T058_FK table=%s actual=%s expected=%s required=true restrictive=true%n",
                    table, keys.getFirst(), expectedName);
            assertThat(keys.getFirst().getFirst()).isEqualTo(expectedName);
        }
    }

    @Test
    void otherGeneratedConstraintsAndHistoricalStorageRemainIntactWithoutDuplicatePairKey() throws Exception {
        try (var connection = independentConnection()) {
            for (var table : List.of("TB_PRODUCT", "TB_CATEGORY", "TB_PRODUCT_CATEGORY", "TB_ORDER", "TB_ORDER_ITEM")) {
                assertThat(query(connection, "select count(*) from " + table)).containsExactly("0");
            }
            var constraints = query(connection, """
                    select table_name, constraint_name, constraint_type
                    from information_schema.table_constraints where table_schema='PUBLIC' and table_name in
                    """ + TABLES + " order by table_name, constraint_name");
            var columns = query(connection, """
                    select table_name, column_name, data_type, is_nullable, numeric_precision, numeric_scale,
                           character_maximum_length, column_default, is_identity
                    from information_schema.columns where table_schema='PUBLIC' and table_name in
                    """ + TABLES + " order by table_name, ordinal_position");
            var keyColumns = query(connection, """
                    select table_name, constraint_name, column_name, ordinal_position
                    from information_schema.key_column_usage where table_schema='PUBLIC' and table_name in
                    """ + TABLES + " order by table_name, constraint_name, ordinal_position");
            var checks = query(connection, """
                    select cc.constraint_name, cc.check_clause from information_schema.check_constraints cc
                    join information_schema.table_constraints tc on tc.constraint_schema=cc.constraint_schema
                    and tc.constraint_name=cc.constraint_name where tc.table_schema='PUBLIC' and tc.table_name in
                    """ + TABLES + " order by cc.constraint_name");
            for (var row : constraints) System.out.println("T058_CONSTRAINT " + row);
            for (var row : columns) System.out.println("T058_COLUMN " + row);
            for (var row : keyColumns) System.out.println("T058_KEY_COLUMN " + row);
            for (var row : checks) System.out.println("T058_CHECK " + row);

            assertThat(constraints).contains(
                    "TB_ORDER_ITEM|UK_ORDER_ITEM_ORDER_PRODUCT|UNIQUE",
                    "TB_ORDER_ITEM|UK_ORDER_ITEM_ORDER_POSITION|UNIQUE",
                    "TB_ORDER_ITEM|CK_ORDER_ITEM_QUANTITY_POSITIVE|CHECK",
                    "TB_ORDER_ITEM|CK_ORDER_ITEM_POSITION_NONNEGATIVE|CHECK",
                    "TB_ORDER_ITEM|CK_ORDER_ITEM_UNIT_PRICE_POSITIVE|CHECK");
            assertThat(keyColumns).contains(
                    "TB_ORDER_ITEM|UK_ORDER_ITEM_ORDER_PRODUCT|ORDER_ID|1",
                    "TB_ORDER_ITEM|UK_ORDER_ITEM_ORDER_PRODUCT|PRODUCT_ID|2",
                    "TB_ORDER_ITEM|UK_ORDER_ITEM_ORDER_POSITION|ORDER_ID|1",
                    "TB_ORDER_ITEM|UK_ORDER_ITEM_ORDER_POSITION|POSITION|2");
            var pairKeys = constraints.stream().filter(row -> row.startsWith("TB_PRODUCT_CATEGORY|")
                    && (row.endsWith("|PRIMARY KEY") || row.endsWith("|UNIQUE"))).toList();
            assertThat(pairKeys).hasSize(1);
            var pairName = pairKeys.getFirst().split("\\|")[1];
            assertThat(keyColumns.stream().filter(row -> row.startsWith("TB_PRODUCT_CATEGORY|" + pairName + "|"))
                    .map(row -> row.split("\\|")[2]).toList()).containsExactlyInAnyOrder("PRODUCT_ID", "CATEGORY_ID");
            assertThat(query(connection, "select column_name from information_schema.columns "
                    + "where table_schema='PUBLIC' and table_name='TB_ORDER_ITEM' and is_nullable='NO'"))
                    .containsExactlyInAnyOrder("ID", "ORDER_ID", "PRODUCT_ID", "POSITION", "QUANTITY", "UNIT_PRICE");
            assertThat(query(connection, "select numeric_precision, numeric_scale from information_schema.columns "
                    + "where table_schema='PUBLIC' and table_name='TB_ORDER_ITEM' and column_name='UNIT_PRICE'"))
                    .containsExactly("19|2");
            System.out.println("T058_CONTROL emptyIsolatedDatabase=true pairKeyCount=1 historicalStorage=NUMERIC(19,2)");
        }
    }

    private Connection independentConnection() throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return DriverManager.getConnection(environment.getRequiredProperty("spring.datasource.url"), "sa", "");
    }

    private List<String> query(Connection connection, String sql) throws Exception {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (var rows = statement.executeQuery(sql)) {
                while (rows.next()) {
                    var values = new ArrayList<String>();
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) values.add(String.valueOf(rows.getString(i)));
                    result.add(String.join("|", values));
                }
            }
        }
        return result;
    }
}
