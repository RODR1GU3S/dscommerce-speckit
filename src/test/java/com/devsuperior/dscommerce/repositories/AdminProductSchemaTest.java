package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.services.ProductInputValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** T035: real schema and direct SQL bypass Bean Validation deliberately; not administrative maintenance. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductSchemaTest.SqlConfig.class)
class AdminProductSchemaTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private EntityManagerFactory entityManagers;
    @Autowired private ProductInputValidationService local;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductTestFixture fixture;
    private AdminProductDatabaseProbe database;
    private JdbcTemplate independent;
    private AdminProductDatabaseProbe.Snapshot baseline;
    private long firstProduct, secondProduct, firstCategory, secondCategory;
    private Long order;
    private final List<Long> extraProducts = new ArrayList<>();
    private final List<Pair> extraPairs = new ArrayList<>();

    @BeforeEach
    void prepareCommittedOwnedDataInSeedFreeContext() {
        assertNoCallerTransaction();
        var url = context.getEnvironment().getRequiredProperty("spring.datasource.url");
        database = new AdminProductDatabaseProbe(url);
        independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        firstProduct = fixture.createProduct("Fone legado", "Legado", "imagem-legada", new BigDecimal("0.00")).id();
        secondProduct = fixture.createProduct("Fone atual", "Descricao completa", "https://example.invalid/a", new BigDecimal("10.00")).id();
        firstCategory = fixture.createCategory("Categoria igual").id();
        secondCategory = fixture.createCategory("Categoria igual").id();
        fixture.associate(firstProduct, firstCategory);
        var buyer = fixture.prepareBuyer();
        order = insertIdentity("tb_order", Map.of("buyer_id", buyer.id(), "created_at", OffsetDateTime.parse("2026-10-01T12:00:00Z"), "status", "WAITING_PAYMENT"));
        independent.update("insert into tb_order_item(order_id,product_id,position,quantity,unit_price) values (?,?,0,2,7.50)", order, secondProduct);
        baseline = database.snapshot();
        assertThat(baseline.orders()).hasSize(1);
        assertThat(baseline.items()).hasSize(1);
        assertThat(baseline.items().getFirst().unitPrice()).isEqualByComparingTo("7.50");
        sql.clear();
    }

    @AfterEach
    void cleanOnlyOwnedDataAfterIndependentAssertions() {
        assertNoCallerTransaction();
        if (order != null) {
            independent.update("delete from tb_order_item where order_id=?", order);
            independent.update("delete from tb_order where id=?", order);
        }
        for (var pair : extraPairs) independent.update("delete from tb_product_category where product_id=? and category_id=?", pair.product(), pair.category());
        for (long id : extraProducts) {
            independent.update("delete from tb_product_category where product_id=?", id);
            independent.update("delete from tb_product where id=?", id);
        }
        if (fixture != null) { fixture.close(); assertEmptyDatabase(); }
    }

    @Test
    void shouldExposeNumericLobsNotNullPairKeyAndRealForeignKeys() throws Exception {
        var before = database.snapshot();
        try (var connection = database.openConnection()) {
            var metadata = connection.getMetaData();
            var price = column(metadata, "TB_PRODUCT", "PRICE");
            assertThat(price.type()).isIn(Types.NUMERIC, Types.DECIMAL);
            assertThat(price.size()).isEqualTo(19);
            assertThat(price.scale()).isEqualTo(2);
            assertThat(column(metadata, "TB_PRODUCT", "DESCRIPTION").type()).isEqualTo(Types.CLOB);
            assertThat(column(metadata, "TB_PRODUCT", "IMAGE").type()).isEqualTo(Types.CLOB);
            for (String field : List.of("ID", "NAME", "DESCRIPTION", "IMAGE", "PRICE")) assertThat(column(metadata, "TB_PRODUCT", field).nullable()).as(field).isEqualTo(DatabaseMetaData.columnNoNulls);
            for (String field : List.of("PRODUCT_ID", "CATEGORY_ID")) assertThat(column(metadata, "TB_PRODUCT_CATEGORY", field).nullable()).as(field).isEqualTo(DatabaseMetaData.columnNoNulls);
            var pair = Set.of("PRODUCT_ID", "CATEGORY_ID");
            var primary = new HashSet<String>();
            try (var rows = metadata.getPrimaryKeys(null, "PUBLIC", "TB_PRODUCT_CATEGORY")) { while (rows.next()) primary.add(rows.getString("COLUMN_NAME")); }
            var indexes = new LinkedHashMap<String, Set<String>>();
            try (var rows = metadata.getIndexInfo(null, "PUBLIC", "TB_PRODUCT_CATEGORY", true, false)) {
                while (rows.next()) if (rows.getString("COLUMN_NAME") != null) indexes.computeIfAbsent(rows.getString("INDEX_NAME"), ignored -> new HashSet<>()).add(rows.getString("COLUMN_NAME"));
            }
            assertThat(primary.equals(pair) || indexes.containsValue(pair)).as("equivalent pair PK/unique from JDBC metadata").isTrue();
            var keys = new LinkedHashMap<String, Set<String>>();
            independent.query("""
                    select tc.constraint_name,kcu.column_name from information_schema.table_constraints tc
                    join information_schema.key_column_usage kcu on tc.constraint_catalog=kcu.constraint_catalog
                    and tc.constraint_schema=kcu.constraint_schema and tc.constraint_name=kcu.constraint_name
                    where tc.table_schema='PUBLIC' and tc.table_name='TB_PRODUCT_CATEGORY'
                    and tc.constraint_type in ('PRIMARY KEY','UNIQUE')
                    """, (org.springframework.jdbc.core.RowCallbackHandler) row -> keys.computeIfAbsent(row.getString(1), ignored -> new HashSet<>()).add(row.getString(2)));
            assertThat(keys.values().stream().filter(pair::equals).count()).as("one equivalent protection, no duplicate constraint").isEqualTo(1);
            var references = new ArrayList<ForeignKey>();
            try (var rows = metadata.getImportedKeys(null, "PUBLIC", "TB_PRODUCT_CATEGORY")) {
                while (rows.next()) references.add(new ForeignKey(rows.getString("FK_NAME"), rows.getString("FKCOLUMN_NAME"), rows.getString("PKTABLE_NAME"), rows.getString("PKCOLUMN_NAME"), rows.getShort("DELETE_RULE")));
            }
            assertThat(references).hasSize(2).allSatisfy(fk -> { assertThat(fk.parentColumn()).isEqualTo("ID"); assertThat(fk.deleteRule()).isIn((short)DatabaseMetaData.importedKeyRestrict, (short)DatabaseMetaData.importedKeyNoAction); });
            assertThat(references.stream().map(fk -> fk.column()+"->"+fk.parent()).toList()).containsExactlyInAnyOrder("PRODUCT_ID->TB_PRODUCT", "CATEGORY_ID->TB_CATEGORY");
            System.out.printf("T035_METADATA pricePrecision=%d scale=%d descriptionImage=CLOB pairKeys=%s foreignKeys=%s%n", price.size(), price.scale(), keys, references);
        }
        assertThat(database.snapshot()).isEqualTo(before);
    }

    @ParameterizedTest(name = "SQL exact money: {0}")
    @ValueSource(strings = {"0.01", "99999999999999999.99", "0.00", "1.00"})
    void shouldStoreRepresentableMoneyExactlyIncludingLegacyZero(String value) {
        var price = new BigDecimal(value);
        var id = insertProduct(productValues("Descricao completa", "https://example.invalid/a", price));
        assertThat(independent.queryForObject("select price from tb_product where id=?", BigDecimal.class, id)).isEqualTo(price);
        assertHistoryAndPriorRowsUnchanged();
        System.out.printf("T035_MONEY value=%s committed=true independentExact=true%n", value);
    }

    @Test
    void shouldRejectOverflowBeyondNumericCapacityByDirectSql() {
        assertSqlRejected("22001", () -> insertProduct(productValues("Descricao completa", "https://example.invalid/a", new BigDecimal("100000000000000000.00"))));
    }

    @ParameterizedTest(name = "SQL product NOT NULL: {0}")
    @ValueSource(strings = {"name", "description", "image", "price"})
    void shouldRejectNullProductColumnsByDirectSql(String field) {
        var values = productValues("Descricao completa", "https://example.invalid/a", new BigDecimal("1.00"));
        values.put(field, null);
        assertSqlRejected("23502", () -> insertProduct(values));
    }

    @ParameterizedTest(name = "SQL pair NOT NULL: {0}")
    @ValueSource(strings = {"product_id", "category_id"})
    void shouldRejectNullAssociationColumnsByDirectSql(String field) {
        Long product = field.equals("product_id") ? null : secondProduct;
        Long category = field.equals("category_id") ? null : secondCategory;
        assertSqlRejected("23502", () -> independent.update("insert into tb_product_category(product_id,category_id) values (?,?)", product, category));
    }

    @ParameterizedTest(name = "SQL pair absent reference: {0}")
    @ValueSource(strings = {"product_id", "category_id"})
    void shouldRejectAbsentReferencesByDirectSql(String field) {
        var parent = field.equals("product_id") ? "tb_product" : "tb_category";
        assertThat(independent.queryForObject("select count(*) from " + parent + " where id=?", Integer.class, Long.MAX_VALUE)).isZero();
        long product = field.equals("product_id") ? Long.MAX_VALUE : secondProduct;
        long category = field.equals("category_id") ? Long.MAX_VALUE : secondCategory;
        assertSqlRejected("23506", () -> independent.update("insert into tb_product_category(product_id,category_id) values (?,?)", product, category));
    }

    @Test
    void shouldRejectOnlyDuplicatePairsWithoutConflatingSharedProductsOrCategories() {
        assertSqlRejected("23505", () -> independent.update("insert into tb_product_category(product_id,category_id) values (?,?)", firstProduct, firstCategory));
        insertPair(firstProduct, secondCategory);
        insertPair(secondProduct, firstCategory);
        assertThat(database.snapshot().associations()).hasSize(3);
        assertHistoryAndPriorRowsUnchanged();
    }

    @ParameterizedTest(name = "SQL full text description={0}, image={1}")
    @CsvSource({"256,64", "32,256", "4096,8192"})
    void shouldStoreValidLargeTextByDirectSqlWithoutTruncation(int descriptionSize, int imageSize) throws Exception {
        var description = description(descriptionSize); var image = image(imageSize);
        assertLocallyValid(description, image);
        var id = insertProduct(productValues(description, image, new BigDecimal("1.50")));
        assertExactIndependentText(id, description, image);
        assertHistoryAndPriorRowsUnchanged();
        System.out.printf("T035_SQL_TEXT descriptionChars=%d imageChars=%d committed=true independentExact=true%n", description.length(), image.length());
    }

    @ParameterizedTest(name = "JPA proxy commit description={0}, image={1}")
    @CsvSource({"256,64", "32,256", "4096,8192"})
    void shouldCommitLargeTextThroughRepositoryProxyBeforeIndependentAndHttpReads(int descriptionSize, int imageSize) throws Exception {
        assertThat(AopUtils.isAopProxy(products)).isTrue();
        assertNoCallerTransaction();
        var description = description(descriptionSize); var image = image(imageSize);
        assertLocallyValid(description, image);
        var product = new Product(null, "Produto schema", description, image, new BigDecimal("1.50"));
        product.getCategories().addAll(categories.findAllByIdIn(List.of(firstCategory, secondCategory)));
        sql.clear();
        var saved = products.saveAndFlush(product);
        extraProducts.add(saved.getId());
        assertNoCallerTransaction();
        assertThat(saved.getId()).isPositive();
        assertThat(sql.events()).filteredOn(event -> event.sql().stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("insert ")).isNotEmpty().allSatisfy(event -> assertThat(event.transactionActive()).isTrue());
        assertExactIndependentText(saved.getId(), description, image);
        try (var fresh = entityManagers.createEntityManager()) {
            fresh.getTransaction().begin();
            try {
                var read = fresh.find(Product.class, saved.getId());
                assertThat(read).isNotSameAs(saved);
                assertThat(read.getDescription()).isEqualTo(description);
                assertThat(read.getImage()).isEqualTo(image);
                assertThat(read.getCategories()).extracting(category -> category.getId()).containsExactlyInAnyOrder(firstCategory, secondCategory);
                fresh.getTransaction().commit();
            } catch (RuntimeException | Error failure) { if (fresh.getTransaction().isActive()) fresh.getTransaction().rollback(); throw failure; }
        }
        assertExistingHttpReads(saved.getId(), description, image);
        assertHistoryAndPriorRowsUnchanged();
        System.out.printf("T035_JPA_COMMIT descriptionChars=%d imageChars=%d repositoryProxy=true insertTransactionActive=true noCallerTransaction=true afterProxyIndependentExact=true freshJpaExact=true%n", description.length(), image.length());
    }

    private void assertExistingHttpReads(long id, String description, String image) throws Exception {
        var admin = fixture.createAccount("schema-admin", "Disposable-T035", Set.of(UserRole.ADMIN));
        var token = fixture.login(admin);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        var before = database.snapshot();
        for (String prefix : List.of("", "/api")) for (boolean administrative : List.of(false, true)) {
            String path = (administrative ? "/admin/products/" : "/products/") + id;
            var request = get(prefix + path).servletPath(path).accept(MediaType.APPLICATION_JSON);
            if (!prefix.isEmpty()) request.contextPath(prefix);
            if (administrative) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            var response = mvc.perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(200);
            var json = mapper.readTree(response.getContentAsByteArray());
            assertThat(json.path("description").asText()).isEqualTo(description);
            assertThat(json.path("image").asText()).isEqualTo(image);
            if (administrative) { assertThat(json.path("price").isTextual()).isTrue(); assertThat(json.path("price").asText()).isEqualTo("1.50"); assertThat(json.path("categories").size()).isEqualTo(2); }
            else { assertThat(json.path("price").isNumber()).isTrue(); assertThat(json.path("price").decimalValue()).isEqualByComparingTo("1.50"); assertThat(json.path("categories").size()).isEqualTo(1); }
        }
        assertThat(database.snapshot()).isEqualTo(before);
    }

    private void assertExactIndependentText(long id, String description, String image) throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.prepareStatement("select description,image,price from tb_product where id=?")) {
            assertThat(connection.getAutoCommit()).isTrue(); statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(description); assertThat(rows.getString(2)).isEqualTo(image);
                assertThat(rows.getClob(1).length()).isEqualTo(description.length()); assertThat(rows.getClob(2).length()).isEqualTo(image.length());
                assertThat(rows.getBigDecimal(3)).isEqualTo(new BigDecimal("1.50")); assertThat(rows.next()).isFalse();
            }
        }
    }

    private void assertLocallyValid(String description, String image) {
        var result = local.validate(new ProductWriteRequestDTO("Produto schema", description, "1.50", image, List.of(firstCategory, secondCategory)));
        assertThat(result.diagnostic().errors()).isEmpty(); assertThat(result.normalizedValues()).isNotNull();
    }

    private void assertSqlRejected(String state, Runnable statement) {
        assertNoCallerTransaction(); var before = database.snapshot();
        assertThatThrownBy(statement::run).isInstanceOf(DataIntegrityViolationException.class).satisfies(error -> {
            var cause = ((DataIntegrityViolationException) error).getMostSpecificCause(); assertThat(cause).isInstanceOf(SQLException.class); assertThat(((SQLException) cause).getSQLState()).isEqualTo(state);
        });
        assertThat(database.snapshot()).isEqualTo(before); assertHistoryAndPriorRowsUnchanged();
        System.out.printf("T035_SQL_REJECTION state=%s directJdbc=true snapshotEqual=true historyUnchanged=true%n", state);
    }

    private void assertHistoryAndPriorRowsUnchanged() {
        var after = database.snapshot(); assertThat(after.orders()).isEqualTo(baseline.orders()); assertThat(after.items()).isEqualTo(baseline.items());
        assertThat(after.categories()).isEqualTo(baseline.categories());
        assertThat(after.products().stream().filter(row -> row.id() == firstProduct || row.id() == secondProduct).toList()).isEqualTo(baseline.products());
    }

    private long insertProduct(Map<String, Object> values) { long id = insertIdentity("tb_product", values); extraProducts.add(id); return id; }
    private void insertPair(long product, long category) { independent.update("insert into tb_product_category(product_id,category_id) values (?,?)", product, category); extraPairs.add(new Pair(product, category)); }
    private long insertIdentity(String table, Map<String, Object> values) {
        var keys = new GeneratedKeyHolder(); String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
        independent.update(connection -> { var statement = connection.prepareStatement("insert into " + table + "(" + String.join(",", values.keySet()) + ") values (" + placeholders + ")", new String[]{"id"}); int index = 1; for (var value : values.values()) statement.setObject(index++, value); return statement; }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }
    private static Map<String, Object> productValues(String description, String image, BigDecimal price) {
        var values = new LinkedHashMap<String, Object>(); values.put("name", "Produto schema"); values.put("description", description); values.put("image", image); values.put("price", price); return values;
    }
    private static Column column(DatabaseMetaData metadata, String table, String name) throws SQLException {
        try (var rows = metadata.getColumns(null, "PUBLIC", table, name)) { assertThat(rows.next()).as(table + "." + name).isTrue(); var result = new Column(rows.getInt("DATA_TYPE"), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"), rows.getInt("NULLABLE")); assertThat(rows.next()).isFalse(); return result; }
    }
    private static String description(int length) { String unit = "Descrição 😀 e\u0301 | "; String tail = "T035_TEXT_END"; String prefix = unit.repeat(length / unit.length() + 2).substring(0, length - tail.length()); if (Character.isHighSurrogate(prefix.charAt(prefix.length() - 1))) prefix = prefix.substring(0, prefix.length() - 1) + "x"; return prefix + tail; }
    private static String image(int length) { String prefix = "https://example.invalid/schema/"; String tail = "?proof=T035_END#original"; return prefix + "x".repeat(length - prefix.length() - tail.length()) + tail; }
    private static void assertNoCallerTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse(); }
    private void assertEmptyDatabase() { var state = database.snapshot(); assertThat(state.products()).isEmpty(); assertThat(state.categories()).isEmpty(); assertThat(state.associations()).isEmpty(); assertThat(state.orders()).isEmpty(); assertThat(state.items()).isEmpty(); assertThat(database.accountIds()).isEmpty(); }
    private record Column(int type, int size, int scale, int nullable) { }
    private record Pair(long product, long category) { }
    private record ForeignKey(String name, String column, String parent, String parentColumn, short deleteRule) { }
    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation sqlObservation() { return new AdminProductDatabaseProbe.SqlObservation(); }
        @Bean HibernatePropertiesCustomizer sqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) { return properties -> properties.put("hibernate.session_factory.statement_inspector", observation); }
    }
}
