package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/** T017 component verification; the complete V03 repository/HTTP checkpoint remains in T020. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(ProductRepositoryAdminQueryComponentTest.SqlProbeConfig.class)
class ProductRepositoryAdminQueryComponentTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository repository;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;

    @BeforeEach
    void prepareIsolatedContext() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        sql.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRows() {
        assertNoCallerTransaction();
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        sql.clear();
    }

    @Test
    void shouldPageWithoutFilterInOriginalNameIdOrderWithoutFetchingCategories() {
        product("Z produto");
        var first = product("A produto");
        var second = product("A produto");
        product("B produto");
        var categoryA = fixture.createCategory("Categoria igual");
        var categoryB = fixture.createCategory("Categoria igual");
        fixture.associate(first.id(), categoryA.id());
        fixture.associate(first.id(), categoryB.id());

        var result = read(() -> repository.findAdminPage(PageRequest.of(0, 2)), 2);

        assertThat(result.getContent()).extracting(Product::getId).containsExactly(first.id(), second.id());
        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertPageSql(false);
    }

    @Test
    void shouldUseTheSameLiteralFilterForSelectionAndCount() {
        product("Cabo");
        product("Telefone preto");
        var first = product("Fone azul");
        product("Fone azul");

        var result = read(() -> repository.findPageByName("FONE", PageRequest.of(0, 1)), 2);

        assertThat(result.getContent()).extracting(Product::getId).containsExactly(first.id());
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertPageSql(true);
    }

    @Test
    void shouldKeepFilteredTotalsBeyondTheLastPageAndReturnZeroForNoMatch() {
        product("Fone");
        product("Cabo");

        var beyond = read(() -> repository.findPageByName("fone", PageRequest.of(3, 1)), 2);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(1);
        assertThat(beyond.getTotalPages()).isEqualTo(1);
        assertPageSql(true);
        assertThat(sql.events().get(0).sql().toLowerCase(Locale.ROOT)).contains("offset");

        var absent = read(() -> repository.findPageByName("inexistente", PageRequest.of(0, 1)), 1);
        assertThat(absent.getContent()).isEmpty();
        assertThat(absent.getTotalElements()).isZero();
        assertThat(absent.getTotalPages()).isZero();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            cafe       | Cafe claro       | Café especial
            CAFÉ       | Café especial    | Cafe claro
            fone  azul | Fone  Azul       | Fone Azul
            10%        | Oferta 10%       | Oferta 100
            kit_       | Kit_A            | KitXA
            /          | Cabo/USB         | Cabo USB
            d'agua     | Kit d'agua       | Kit dagua
            """)
    void shouldBindLiteralTextWithoutAccentSpaceOrWildcardNormalization(String filter, String matching, String other) {
        var expected = product(matching);
        product(other);

        var result = read(() -> repository.findPageByName(filter, PageRequest.of(0, 1)), 2);

        assertThat(result.getContent()).extracting(Product::getId).containsExactly(expected.id());
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertPageSql(true);
    }

    @Test
    void shouldLoadDetailCategoriesByIdWithoutMergingEqualNames() {
        var target = product("Mesmo nome");
        product("Mesmo nome");
        var first = fixture.createCategory("Igual");
        var second = fixture.createCategory("Igual");
        fixture.associate(target.id(), first.id());
        fixture.associate(target.id(), second.id());

        var detail = read(() -> repository.findAdminByIdWithCategories(target.id()), 1).orElseThrow();

        assertThat(detail.getId()).isEqualTo(target.id());
        // Access after the repository transaction, with open-in-view disabled.
        assertThat(detail.getCategories()).extracting(Category::getId).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(detail.getCategories()).extracting(Category::getName).containsExactlyInAnyOrder("Igual", "Igual");
    }

    @Test
    void shouldReadUnassociatedLegacyDetailAndReturnEmptyForAbsentId() {
        var legacy = fixture.createProduct("X", "curta", "imagem-legada", new BigDecimal("0.00"));

        var detail = read(() -> repository.findAdminByIdWithCategories(legacy.id()), 1).orElseThrow();

        assertThat(detail.getName()).isEqualTo("X");
        assertThat(detail.getDescription()).isEqualTo("curta");
        assertThat(detail.getImage()).isEqualTo("imagem-legada");
        assertThat(detail.getPrice()).isEqualByComparingTo("0.00");
        assertThat(detail.getCategories()).isEmpty();
        assertThat(read(() -> repository.findAdminByIdWithCategories(Math.addExact(legacy.id(), 1L)), 1)).isEmpty();
    }

    private AdminProductTestFixture.ProductData product(String name) {
        return fixture.createProduct(name, "Descricao componente T017", "imagem-legada", new BigDecimal("10.00"));
    }

    private <T> T read(Supplier<T> query, int selects) {
        assertNoCallerTransaction();
        var before = database.snapshot();
        sql.clear();
        var result = query.get();
        assertNoCallerTransaction();
        assertThat(database.snapshot()).as("independent snapshot after return, before cleanup").isEqualTo(before);
        assertThat(sql.events()).hasSize(selects).allSatisfy(event -> {
            assertThat(event.transactionActive()).isTrue();
            assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
        });
        System.out.printf("T017_COMPONENT selects=%d snapshotEqual=true callerTransaction=false%n", selects);
        return result;
    }

    private void assertPageSql(boolean filtered) {
        assertThat(sql.events()).allSatisfy(event -> {
            var statement = event.sql().toLowerCase(Locale.ROOT);
            assertThat(statement).doesNotContain(" join ", "tb_category", "tb_product_category");
            if (filtered) assertThat(statement).contains("locate(lower(?),lower(");
        });
        assertThat(sql.events().get(0).sql().toLowerCase(Locale.ROOT)).contains("order by", "fetch first");
        assertThat(sql.events().get(1).sql().toLowerCase(Locale.ROOT)).contains("count(");
    }

    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    private void assertEmptyDatabase() {
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlProbeConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation productQuerySqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer productQuerySqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
