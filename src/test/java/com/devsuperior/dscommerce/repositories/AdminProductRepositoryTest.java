package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.assertCompletePages;
import static org.assertj.core.api.Assertions.assertThat;

/** T020: JPA/H2 pagination and literal search against independent, committed fixture manifests. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductRepositoryTest.SqlConfig.class)
class AdminProductRepositoryTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductRepository repository;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;

    @BeforeEach
    void prepareIsolatedContextWithoutCallerTransaction() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        sql.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterAssertions() {
        assertNoCallerTransaction();
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        sql.clear();
    }

    @ParameterizedTest(name = "V03 repository filter={0}")
    @EnumSource(Filter.class)
    void shouldNavigateTheExactV03ManifestOncePerIdWithCorrectTotalsAndTieOrder(Filter filter) {
        var manifest = fixture.prepareV03();
        var before = database.snapshot();
        assertThat(before.products()).hasSize(6).containsExactlyElementsOf(manifest.insertedProducts().stream()
                .map(product -> new AdminProductDatabaseProbe.ProductRow(product.id(), product.name(),
                        product.description(), product.image(), product.price())).toList());
        assertThat(manifest.insertedProducts()).extracting(ProductData::name)
                .containsExactly("Telefone D", "Fone C", "Fone B", "Fone B", "Fone A", "Cabo");
        assertThat(before.categories()).hasSize(2).containsExactlyElementsOf(manifest.categories().stream()
                .map(category -> new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name())).toList());
        assertThat(before.associations()).hasSize(2).isEqualTo(manifest.associations());
        var multipleCategoryId = manifest.insertedProducts().get(4).id();
        assertThat(before.associations()).allSatisfy(pair -> assertThat(pair.productId()).isEqualTo(multipleCategoryId));
        var expectedIds = filter == Filter.NONE ? manifest.unfilteredOrder() : manifest.filteredOrder();
        assertThat(expectedIds).hasSize(filter == Filter.NONE ? 6 : 5).doesNotHaveDuplicates();
        var pages = new ArrayList<PageResponseDTO<Product>>();

        for (int page = 0; page <= 3; page++) {
            var result = readPage(filter.text, page, 2);
            var expectedProducts = expectedIds.stream().skip((long) page * 2).limit(2)
                    .map(id -> manifest.insertedProducts().stream().filter(product -> product.id() == id)
                            .findFirst().orElseThrow()).toList();
            assertThat(result.content()).extracting(product -> new ProductData(product.getId(), product.getName(),
                    product.getDescription(), product.getImage(), product.getPrice()))
                    .containsExactlyElementsOf(expectedProducts);
            pages.add(result);
        }

        assertThat(pages).extracting(page -> page.content().size())
                .containsExactly(2, 2, filter == Filter.NONE ? 2 : 1, 0);
        var actualIds = assertCompletePages(pages, 2, expectedIds, Product::getId);
        assertThat(actualIds).containsExactlyElementsOf(expectedIds).doesNotHaveDuplicates();
        assertThat(actualIds.stream().filter(id -> id == multipleCategoryId).count()).isEqualTo(1);
        assertThat(database.snapshot()).isEqualTo(before);
        System.out.printf("T020_V03_REPOSITORY filter=%s actualIds=%s oracleIds=%s totalElements=%d totalPages=%d%n",
                filter, actualIds, expectedIds, pages.getFirst().totalElements(), pages.getFirst().totalPages());
    }

    @ParameterizedTest(name = "literal repository scenario={0}, filter={1}")
    @MethodSource("literalCases")
    void shouldMatchLiteralCaseAccentSpaceAndSpecialCharactersWithoutChangingTheFixture(
            Scenario scenario, String filter, String oracleKey) {
        var manifest = switch (scenario) {
            case ACCENTS -> fixture.prepareAccentScenario();
            case SPACES -> fixture.prepareSpacingScenario();
            case SPECIAL -> fixture.prepareSpecialCharactersScenario();
        };
        var before = database.snapshot();
        assertThat(before.products()).hasSize(manifest.products().size()).containsExactlyElementsOf(manifest.products().stream()
                .map(product -> new AdminProductDatabaseProbe.ProductRow(product.id(), product.name(),
                        product.description(), product.image(), product.price())).toList());
        var expectedIds = manifest.expectedMatches().getOrDefault(oracleKey, List.of());

        var result = readPage(filter, 0, 1);

        assertThat(result.content()).extracting(Product::getId).containsExactlyElementsOf(expectedIds).doesNotHaveDuplicates();
        assertThat(result.totalElements()).isEqualTo(expectedIds.size());
        assertThat(result.totalPages()).isEqualTo(expectedIds.size());
        assertThat(database.snapshot()).isEqualTo(before);
        System.out.printf("T020_LITERAL_REPOSITORY scenario=%s filter=%s actualIds=%s oracleIds=%s totalElements=%d%n",
                scenario, filter, result.content().stream().map(Product::getId).toList(), expectedIds, result.totalElements());
    }

    static Stream<Arguments> literalCases() {
        return Stream.of(Arguments.of(Scenario.ACCENTS, "cafe", "cafe"), Arguments.of(Scenario.ACCENTS, "CAFÉ", "café"),
                Arguments.of(Scenario.SPACES, "fone a", "fone a"), Arguments.of(Scenario.SPACES, "fone  a", "fone  a"),
                Arguments.of(Scenario.SPECIAL, "%", "%"), Arguments.of(Scenario.SPECIAL, "_", "_"),
                Arguments.of(Scenario.SPECIAL, "/", "/"), Arguments.of(Scenario.SPECIAL, "'", "'"),
                Arguments.of(Scenario.SPECIAL, "' OR 1=1 --", "no-match"));
    }

    private PageResponseDTO<Product> readPage(String filter, int page, int size) {
        assertNoCallerTransaction();
        var before = database.snapshot();
        sql.clear();
        var request = PageRequest.of(page, size);
        var result = filter == null ? repository.findAdminPage(request) : repository.findPageByName(filter, request);
        assertNoCallerTransaction();
        assertThat(database.snapshot()).as("independent snapshot after repository return, before cleanup").isEqualTo(before);
        assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
            assertThat(event.transactionActive()).isTrue();
            assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ")
                    .doesNotContain(" join ", "tb_category", "tb_product_category");
        });
        System.out.printf("T020_REPOSITORY_PAGE page=%d size=%d rows=%d totalElements=%d totalPages=%d selects=%d snapshotEqual=true%n",
                page, size, result.getNumberOfElements(), result.getTotalElements(), result.getTotalPages(), sql.events().size());
        return new PageResponseDTO<>(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
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

    enum Filter {
        NONE(null), LOWER("fone"), UPPER("FONE");
        final String text;
        Filter(String text) { this.text = text; }
    }
    enum Scenario { ACCENTS, SPACES, SPECIAL }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation adminProductRepositoryObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer adminProductRepositorySqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
