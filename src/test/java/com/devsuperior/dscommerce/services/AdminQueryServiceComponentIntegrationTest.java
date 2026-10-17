package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.CategoryOptionDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Direct service proxies only: no administrative HTTP endpoint or complete V03 checkpoint. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminQueryServiceComponentIntegrationTest.SqlConfig.class)
class AdminQueryServiceComponentIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private AdminProductQueryService products;
    @Autowired private AdminCategoryQueryService categories;
    @Autowired private QuerySqlProbe sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;

    @BeforeEach
    void prepareCommittedIsolatedFixtures() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        assertThat(AopUtils.isAopProxy(products)).isTrue();
        assertThat(AopUtils.isAopProxy(categories)).isTrue();
        sql.events.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRows() {
        assertNoCallerTransaction();
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        sql.events.clear();
    }

    @Test
    void shouldReadAndMapThroughReadOnlyProxiesWithCurrentAdminGuardAndNoDatabaseMutation() {
        var admin = fixture.createAccount("T018 Admin", "Disposable-T018", Set.of(UserRole.ADMIN));
        var principal = new AuthenticatedAccountPrincipal(admin.id());
        var legacy = fixture.createProduct(" Fone  Café ", "curta", "imagem-legada", new BigDecimal("0.00"));
        var second = fixture.createCategory("A");
        var third = fixture.createCategory("Z");
        var first = fixture.createCategory("A");
        fixture.associate(legacy.id(), first.id());
        fixture.associate(legacy.id(), second.id());

        var page = read(() -> products.listProducts("\u00a0 FONE  Café \u2007", 0, 12, principal), "filtered-page");
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(legacy.id());
            assertThat(item.name()).isEqualTo(" Fone  Café ");
            assertThat(item.image()).isEqualTo("imagem-legada");
            assertThat(item.price()).isEqualTo("0.00");
        });
        var detail = read(() -> products.findProductDetails(legacy.id(), principal), "detail");
        assertThat(detail.description()).isEqualTo("curta");
        assertThat(detail.categories()).containsExactly(new CategoryOptionDTO(second.id(), "A"),
                new CategoryOptionDTO(first.id(), "A"));
        var options = read(() -> categories.listCategories(principal), "category-options");
        assertThat(options).containsExactly(new CategoryOptionDTO(second.id(), "A"),
                new CategoryOptionDTO(first.id(), "A"), new CategoryOptionDTO(third.id(), "Z"));
        var unfiltered = read(() -> products.listProducts("\u00a0\u2007", 0, 12, principal), "unfiltered-page");
        assertThat(unfiltered.content()).isEqualTo(page.content());
    }

    @Test
    void shouldResolveActualClientAndRejectAllThreeReadsBeforeProductOrCategorySql() {
        var client = fixture.createAccount("T018 Client", "Disposable-T018", Set.of(UserRole.CLIENT));
        var principal = new AuthenticatedAccountPrincipal(client.id());
        for (Runnable query : List.<Runnable>of(() -> products.listProducts(null, 0, 12, principal),
                () -> products.findProductDetails(999L, principal), () -> categories.listCategories(principal))) {
            var before = database.snapshot();
            sql.events.clear();
            assertThatThrownBy(query::run).isInstanceOf(AccessDeniedException.class);
            assertNoCallerTransaction();
            assertThat(database.snapshot()).isEqualTo(before);
            assertThat(sql.events).hasSize(1);
            assertReadOnlySelects();
            assertThat(sql.events.getFirst().statement()).contains("tb_user_account").doesNotContain("tb_product", "tb_category");
        }
    }

    private <T> T read(Supplier<T> query, String label) {
        assertNoCallerTransaction();
        var before = database.snapshot();
        sql.events.clear();
        var result = query.get();
        assertNoCallerTransaction();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(sql.events).hasSizeGreaterThanOrEqualTo(2);
        assertReadOnlySelects();
        assertThat(sql.events.getFirst().statement()).contains("tb_user_account").doesNotContain("tb_product", "tb_category");
        System.out.printf("T018_PROXY case=%s selects=%d readOnly=true snapshotEqual=true callerTransaction=false%n", label, sql.events.size());
        return result;
    }

    private void assertReadOnlySelects() {
        assertThat(sql.events).allSatisfy(event -> {
            assertThat(event.active()).isTrue();
            assertThat(event.readOnly()).isTrue();
            assertThat(event.statement().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
        });
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

    record QueryEvent(String statement, boolean active, boolean readOnly) { }

    static class QuerySqlProbe implements org.hibernate.resource.jdbc.spi.StatementInspector {
        final List<QueryEvent> events = new CopyOnWriteArrayList<>();
        public String inspect(String statement) {
            events.add(new QueryEvent(statement, TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
            return statement;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean QuerySqlProbe adminQuerySqlProbe() { return new QuerySqlProbe(); }
        @Bean HibernatePropertiesCustomizer adminQuerySqlCustomizer(QuerySqlProbe probe) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", probe);
        }
    }
}
