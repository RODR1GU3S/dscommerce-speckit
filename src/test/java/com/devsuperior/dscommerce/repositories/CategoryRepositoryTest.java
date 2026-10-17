package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.CategoryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T016 repository behavior only: actual JPA/H2, committed isolated fixtures, no HTTP/query service. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(CategoryRepositoryTest.SqlProbeConfig.class)
class CategoryRepositoryTest {

    @Autowired private WebApplicationContext context;
    @Autowired private CategoryRepository repository;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;

    @BeforeEach
    void startWithoutSeedsOrCallerTransaction() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        assertThat(AopUtils.isAopProxy(repository)).isTrue();
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

    @Test
    void shouldReadAllCategoriesInNameIdOrderIncludingUnusedEqualNamesAndLegacyLinks() {
        var expected = prepareCategories();

        var actual = read(repository::findAllByOrderByNameAscIdAsc, "ordered-options", true);

        assertThat(actual).extracting(Category::getId).containsExactlyElementsOf(expected.stream().map(CategoryData::id).toList());
        assertThat(actual).extracting(Category::getName).containsExactly("0 category", "A category", "A category", "Z category");
        assertThat(actual).extracting(Category::getId).doesNotHaveDuplicates();
        assertThat(actual).hasSize(4);
    }

    @Test
    void shouldReadOnlyRequestedExistingIdsInOneBatchWithoutMergingEqualNames() {
        var categories = prepareCategories();
        long missing = nextAbsentId(categories);
        var requested = List.of(categories.get(2).id(), missing, categories.get(1).id(), categories.get(2).id());

        var actual = read(() -> repository.findAllByIdIn(requested), "batch-existing-repeated-and-missing", true);

        assertThat(actual).hasSize(2).extracting(Category::getId)
                .containsExactlyInAnyOrder(categories.get(1).id(), categories.get(2).id()).doesNotHaveDuplicates();
        assertThat(actual).extracting(Category::getName).containsExactlyInAnyOrder("A category", "A category");
        assertThat(actual).noneMatch(category -> category.getId() == missing);
    }

    @Test
    void shouldReturnNoCategoriesForAnEmptyRequestedBatch() {
        prepareCategories();

        var actual = read(() -> repository.findAllByIdIn(Set.of()), "batch-empty-input", false);

        assertThat(actual).isEmpty();
    }

    @Test
    void shouldReturnNoCategoriesWhenEveryRequestedIdIsAbsent() {
        var categories = prepareCategories();
        long missing = nextAbsentId(categories);

        var actual = read(() -> repository.findAllByIdIn(Set.of(missing, Math.addExact(missing, 1L))),
                "batch-all-absent", true);

        assertThat(actual).isEmpty();
    }

    @Test
    void shouldKeepEmptyCategoryReadsCompatibleWithAnUnassociatedLegacyProduct() {
        var legacy = fixture.prepareWithoutCategories();
        var snapshot = database.snapshot();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.products()).hasSize(1);
        assertThat(snapshot.products().get(0).id()).isEqualTo(legacy.id());
        assertThat(snapshot.products().get(0).price()).isEqualByComparingTo("0.00");

        assertThat(read(repository::findAllByOrderByNameAscIdAsc, "options-no-categories", true)).isEmpty();
        assertThat(read(() -> repository.findAllByIdIn(Set.of(1L, 2L)), "batch-no-categories", true)).isEmpty();
    }

    /** Fixture oracle is assembled before the repository calls, never from the query under test. */
    private List<CategoryData> prepareCategories() {
        var lastByName = fixture.createCategory("Z category");
        var tiedFirst = fixture.createCategory("A category");
        var tiedSecond = fixture.createCategory("A category");
        var firstByName = fixture.createCategory("0 category");
        var legacy = fixture.createProduct("X", "curta", "imagem-legada", new BigDecimal("0.00"));
        var firstPair = fixture.associate(legacy.id(), tiedFirst.id());
        var secondPair = fixture.associate(legacy.id(), tiedSecond.id());
        var persisted = database.snapshot();
        assertThat(persisted.categories()).hasSize(4);
        assertThat(persisted.categories()).extracting(AdminProductDatabaseProbe.CategoryRow::id)
                .containsExactlyInAnyOrder(lastByName.id(), tiedFirst.id(), tiedSecond.id(), firstByName.id());
        assertThat(persisted.associations()).containsExactlyInAnyOrder(firstPair, secondPair);
        assertThat(tiedFirst.id()).isLessThan(tiedSecond.id());
        assertThat(persisted.associations()).noneMatch(pair -> pair.categoryId() == firstByName.id()
                || pair.categoryId() == lastByName.id());
        assertThat(persisted.products()).containsExactly(new AdminProductDatabaseProbe.ProductRow(
                legacy.id(), "X", "curta", "imagem-legada", new BigDecimal("0.00")));
        return List.of(firstByName, tiedFirst, tiedSecond, lastByName);
    }

    private long nextAbsentId(List<CategoryData> categories) {
        long missing = Math.addExact(categories.stream().mapToLong(CategoryData::id).max().orElseThrow(), 1L);
        assertThat(database.snapshot().categories()).noneMatch(category -> category.id() == missing);
        return missing;
    }

    private List<Category> read(Supplier<List<Category>> query, String label, boolean requireOneSelect) {
        assertNoCallerTransaction();
        var before = database.snapshot();
        sql.clear();
        var result = query.get();
        assertNoCallerTransaction();
        var after = database.snapshot();
        assertThat(after).as("independent five-table snapshot after repository return and before cleanup").isEqualTo(before);
        assertThat(database.accountIds()).isEmpty();
        var events = sql.events();
        if (requireOneSelect) assertThat(events).hasSize(1);
        else assertThat(events).hasSizeLessThanOrEqualTo(1);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.transactionActive()).as("repository query transaction").isTrue();
            assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
        }).noneMatch(event -> isDml(event.sql()));
        System.out.printf("T016_REPOSITORY case=%s resultCount=%d selects=%d snapshotEqual=%s callerTransaction=false%n",
                label, result.size(), events.size(), before.equals(after));
        return result;
    }

    private static boolean isDml(String sql) {
        var normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(normalized::startsWith);
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
        @Bean AdminProductDatabaseProbe.SqlObservation categorySqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer categorySqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
