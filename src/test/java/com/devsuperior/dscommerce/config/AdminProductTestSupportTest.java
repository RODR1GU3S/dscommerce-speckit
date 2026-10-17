package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.security.OrderJwtAuthenticationConverter;
import com.devsuperior.dscommerce.services.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.assertCompletePages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Infrastructure proofs only; no administrative endpoint behavior is assumed. */
class AdminProductTestSupportTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=test", "spring.h2.console.enabled=false",
                    "spring.jpa.open-in-view=false")
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withInitializer(new TestComponentExclusion())
            .withInitializer(new AdminProductTestConfig())
            .withUserConfiguration(DscommerceApplication.class, ObservationConfig.class);

    @Test
    void shouldStartSeedFreeOnlyWhenExplicitlyInitializedAndKeepRealSecurity() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = probe(context);
            try (var connection = probe.openConnection()) {
                assertThat(connection.getMetaData().getURL())
                        .as("H2 metadata identifies the database without connection options")
                        .isEqualTo(context.getEnvironment().getRequiredProperty("spring.datasource.url").split(";", 2)[0]);
            }
            assertEmpty(probe.snapshot());
            assertThat(probe.accountIds()).isEmpty();
            assertThat(context.getEnvironment().getProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(context.getEnvironment().getProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files"))
                    .isEmpty();
            assertThat(context).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class)
                    .hasSingleBean(PasswordEncoder.class).hasSingleBean(FilterChainProxy.class);
            assertThat(context.getBean(JwtProperties.class).getSecret().length).isEqualTo(32);
            assertThat(context.getBean(JwtDecoder.class).getClass().getName()).contains("NimbusJwtDecoder");
            assertThat(context.getBean(PasswordEncoder.class).getClass().getName()).contains("BCryptPasswordEncoder");
            assertThat(context.getBeansOfType(AdminProductTestConfig.class)).isEmpty();
            assertThat(context.getBeansOfType(OrderTestConfig.class)).isEmpty();
            try (var fixture = new AdminProductTestFixture(context)) {
                assertEmpty(probe.snapshot());
                assertThat(probe.accountIds()).isEmpty();
            }
        });
        var uninitialized = new WebApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=test",
                        "spring.datasource.url=" + OrderTestConfig.isolatedDatabaseUrl(),
                        "spring.h2.console.enabled=false")
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withInitializer(new TestComponentExclusion())
                .withUserConfiguration(DscommerceApplication.class);
        uninitialized.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getPropertySources().contains("adminProductTestContext")).isFalse();
            assertThat(context.getBeansOfType(SqlObservation.class)).isEmpty();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("select count(*) from tb_product", Long.class))
                    .isEqualTo(52L);
            assertThat(context.getBean(JdbcTemplate.class).queryForObject("select count(*) from tb_category", Long.class))
                    .isEqualTo(3L);
            assertThatThrownBy(() -> new AdminProductTestFixture(context)).isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void shouldPrepareExactV03ManifestAndCleanOnlyOwnedRowsAfterAssertions() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = probe(context);
            try (var fixture = new AdminProductTestFixture(context); var other = new AdminProductTestFixture(context)) {
                var manifest = fixture.prepareV03();
                var snapshot = probe.snapshot();
                assertThat(snapshot.products()).containsExactlyElementsOf(manifest.insertedProducts().stream()
                        .map(AdminProductTestSupportTest::productRow).toList());
                assertThat(snapshot.categories()).containsExactlyElementsOf(manifest.categories().stream()
                        .map(category -> new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name())).toList());
                assertThat(snapshot.associations()).isEqualTo(manifest.associations()).hasSize(2);
                assertThat(snapshot.products()).hasSize(6);
                assertThat(snapshot.products()).allSatisfy(product -> {
                    assertThat(product.description()).isEqualTo("Descricao da fixture V03");
                    assertThat(product.image()).isEqualTo("https://example.invalid/v03.jpg");
                    assertThat(product.price()).isEqualByComparingTo("10.00");
                });
                assertThat(snapshot.categories()).extracting(AdminProductDatabaseProbe.CategoryRow::name)
                        .containsExactly("Categoria V03", "Categoria V03");
                assertThat(snapshot.products().stream().filter(p -> !p.name().equals("Cabo")).count()).isEqualTo(5);
                var inserted = manifest.insertedProducts();
                assertThat(inserted).extracting(ProductData::name)
                        .containsExactly("Telefone D", "Fone C", "Fone B", "Fone B", "Fone A", "Cabo");
                assertThat(manifest.filteredOrder()).containsExactly(inserted.get(4).id(), inserted.get(2).id(),
                        inserted.get(3).id(), inserted.get(1).id(), inserted.get(0).id());
                assertThat(inserted.get(2).id()).isLessThan(inserted.get(3).id());
                assertThat(manifest.unfilteredOrder()).containsExactly(inserted.get(5).id(), inserted.get(4).id(),
                        inserted.get(2).id(), inserted.get(3).id(), inserted.get(1).id(), inserted.get(0).id());
                assertThat(snapshot.associations()).allSatisfy(pair -> assertThat(pair.productId()).isEqualTo(inserted.get(4).id()));
                assertThatThrownBy(() -> snapshot.products().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> manifest.filteredOrder().add(-1L)).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> manifest.insertedProducts().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> manifest.categories().clear()).isInstanceOf(UnsupportedOperationException.class);
                assertThatThrownBy(() -> manifest.associations().clear()).isInstanceOf(UnsupportedOperationException.class);
                var preservedProduct = other.createProduct("Other owner", "Other owner's product",
                        "https://example.invalid/other.jpg", new BigDecimal("7.00"));
                var preservedCategory = other.createCategory("Other owner");
                var preservedPair = other.associate(preservedProduct.id(), preservedCategory.id());
                var preservedAccount = other.prepareBuyer();
                assertThatThrownBy(() -> fixture.associate(preservedProduct.id(), preservedCategory.id()))
                        .isInstanceOf(IllegalArgumentException.class);
                fixture.cleanUp();
                fixture.cleanUp();
                var after = probe.snapshot();
                assertThat(after.products()).containsExactly(productRow(preservedProduct));
                assertThat(after.categories()).containsExactly(
                        new AdminProductDatabaseProbe.CategoryRow(preservedCategory.id(), preservedCategory.name()));
                assertThat(after.associations()).containsExactly(preservedPair);
                assertThat(probe.accountIds()).containsExactly(preservedAccount.id());
                assertThat(probe.roles(preservedAccount.id())).containsExactly(UserRole.CLIENT);
            }
            assertEmpty(probe.snapshot());
            assertThat(probe.accountIds()).isEmpty();
        });
    }

    @Test
    void shouldIsolateDatabaseKeyFixtureAndCleanupAcrossTwoLiveContexts() {
        runner.run(first -> {
            assertThat(first).hasNotFailed();
            var firstProbe = probe(first);
            try (var fixture = new AdminProductTestFixture(first)) {
                fixture.prepareV03();
                var account = fixture.prepareBuyer();
                var token = fixture.login(account);
                runner.run(second -> {
                    assertThat(second).hasNotFailed();
                    var secondProbe = probe(second);
                    assertThat(first.getEnvironment().getProperty("spring.datasource.url"))
                            .isNotEqualTo(second.getEnvironment().getProperty("spring.datasource.url"));
                    assertThat(Arrays.equals(first.getBean(JwtProperties.class).getSecret(),
                            second.getBean(JwtProperties.class).getSecret())).isFalse();
                    assertEmpty(secondProbe.snapshot());
                    assertThat(secondProbe.accountIds()).isEmpty();
                    assertThatThrownBy(() -> second.getBean(JwtDecoder.class).decode(token))
                            .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
                    try (var other = new AdminProductTestFixture(second)) {
                        other.prepareV03();
                        var independentBefore = secondProbe.snapshot();
                        fixture.cleanUp();
                        assertEmpty(firstProbe.snapshot());
                        assertThat(secondProbe.snapshot()).isEqualTo(independentBefore);
                    }
                    assertEmpty(secondProbe.snapshot());
                });
            }
        });
    }

    @Test
    void shouldReadCommittedFixtureThroughAnotherConnectionEvenDuringCallerRollback() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = probe(context);
            try (var fixture = new AdminProductTestFixture(context)) {
                var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                    context.getBean(JdbcTemplate.class).update("insert into tb_product (name, description, image, price) "
                            + "values ('Uncommitted caller', 'Caller only', 'caller-image', 1.00)");
                    assertThat(probe.snapshot().products()).isEmpty();
                    var committed = fixture.createProduct("Committed fixture", "Committed separately",
                            "https://example.invalid/committed.jpg", new BigDecimal("10.00"));
                    assertThat(probe.snapshot().products()).containsExactly(productRow(committed));
                    throw new IllegalStateException("Rollback caller only");
                })).isInstanceOf(IllegalStateException.class).hasMessage("Rollback caller only");
                assertThat(probe.snapshot().products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactly("Committed fixture");
                try (var a = probe.openConnection(); var b = probe.openConnection()) {
                    assertThat(a).isNotSameAs(b);
                }
            }
            assertEmpty(probe.snapshot());
        });
    }

    @Test
    void shouldKeepZeroCategoriesLegacyAndLiteralScenariosSeparateAndImmutable() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = probe(context);
            try (var fixture = new AdminProductTestFixture(context)) {
                var legacy = fixture.prepareWithoutCategories();
                var snapshot = probe.snapshot();
                assertThat(snapshot.products()).containsExactly(productRow(legacy));
                assertThat(legacy.price()).isEqualByComparingTo("0.00");
                assertThat(snapshot.categories()).isEmpty();
                assertThat(snapshot.associations()).isEmpty();
                fixture.cleanUp();
                var selection = fixture.prepareCategorySelection();
                assertThat(probe.snapshot().products()).isEmpty();
                assertThat(probe.snapshot().categories()).containsExactly(
                        new AdminProductDatabaseProbe.CategoryRow(selection.sameName().get(0).id(), "Categoria repetida"),
                        new AdminProductDatabaseProbe.CategoryRow(selection.sameName().get(1).id(), "Categoria repetida"),
                        new AdminProductDatabaseProbe.CategoryRow(selection.unused().id(), "Categoria sem produto"));
                assertThat(selection.sameName()).extracting(AdminProductTestFixture.CategoryData::name)
                        .containsExactly("Categoria repetida", "Categoria repetida");
                assertThat(selection.sameName().get(0).id()).isNotEqualTo(selection.sameName().get(1).id());
                assertThat(probe.snapshot().associations()).isEmpty();
                fixture.cleanUp();
                var expectedNames = List.of(List.of("Café", "Cafe"), List.of("Fone A", "Fone  A"),
                        List.of("Fone %", "Fone _", "Fone /", "Fone '"));
                var expectedFilters = List.of(Set.of("café", "cafe"), Set.of("fone a", "fone  a"),
                        Set.of("%", "_", "/", "'"));
                int scenario = 0;
                for (var prepare : List.<java.util.function.Supplier<AdminProductTestFixture.SearchManifest>>of(
                        fixture::prepareAccentScenario, fixture::prepareSpacingScenario, fixture::prepareSpecialCharactersScenario)) {
                    assertEmpty(probe.snapshot());
                    var manifest = prepare.get();
                    assertThat(manifest.products()).extracting(ProductData::name).containsExactlyElementsOf(expectedNames.get(scenario));
                    assertThat(manifest.expectedMatches().keySet()).isEqualTo(expectedFilters.get(scenario++));
                    assertThat(probe.snapshot().products()).containsExactlyElementsOf(
                            manifest.products().stream().map(AdminProductTestSupportTest::productRow).toList());
                    assertThat(probe.snapshot().categories()).isEmpty();
                    assertThatThrownBy(() -> manifest.expectedMatches().clear()).isInstanceOf(UnsupportedOperationException.class);
                    manifest.expectedMatches().forEach((literal, ids) -> {
                        assertThat(ids).hasSize(1);
                        var product = manifest.products().stream().filter(p -> p.id() == ids.get(0)).findFirst().orElseThrow();
                        assertThat(product.name().toLowerCase(java.util.Locale.ROOT)).contains(literal);
                        assertThatThrownBy(() -> ids.add(-1L)).isInstanceOf(UnsupportedOperationException.class);
                    });
                    fixture.cleanUp();
                }
                assertEmpty(probe.snapshot());
            }
        });
    }

    @Test
    void shouldCommitRoleAccountsAndObtainRealLoginTokensWithoutCallerTransaction() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = probe(context);
            try (var fixture = new AdminProductTestFixture(context)) {
                var accounts = fixture.prepareAccessAccounts();
                assertThat(accounts.admin().roles()).containsExactly(UserRole.ADMIN);
                assertThat(accounts.client().roles()).containsExactly(UserRole.CLIENT);
                assertThat(accounts.both().roles()).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.CLIENT);
                assertThat(accounts.none().roles()).isEmpty();
                assertThat(probe.accountIds()).hasSize(4);
                for (var account : List.of(accounts.admin(), accounts.client(), accounts.both(), accounts.none())) {
                    assertThat(probe.roles(account.id())).isEqualTo(account.roles());
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    var token = fixture.login(account);
                    var jwt = context.getBean(JwtDecoder.class).decode(token);
                    assertThat(jwt.getSubject()).isEqualTo(Long.toString(account.id()));
                    var authentication = context.getBean(OrderJwtAuthenticationConverter.class).convert(jwt);
                    assertThat(authentication.getAuthorities()).extracting(a -> a.getAuthority())
                            .containsExactlyInAnyOrderElementsOf(account.roles().stream().map(role -> "ROLE_" + role.name()).toList());
                    assertThat(account.toString().contains(account.password()) || account.toString().contains(account.name())).isFalse();
                }
                var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                outer.executeWithoutResult(status -> assertThatThrownBy(() -> fixture.login(accounts.admin()))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("without a caller transaction"));
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            }
            assertThat(probe.accountIds()).isEmpty();
            assertEmpty(probe.snapshot());
        });
    }

    @Test
    void shouldSnapshotFiveTablesAfterRealHttpAndProxyCompletionAndObserveHibernateSql() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var probe = probe(context);
            var jdbc = context.getBean(JdbcTemplate.class);
            var createdOrders = new ArrayList<Long>();
            try (var fixture = new AdminProductTestFixture(context)) {
                var buyer = fixture.prepareBuyer();
                var product = fixture.createProduct("Support control", "Positive commit control",
                        "https://example.invalid/control.jpg", new BigDecimal("10.00"));
                var category = fixture.createCategory("Support control");
                var pair = fixture.associate(product.id(), category.id());
                var token = fixture.login(buyer);
                var observer = context.getBean(SqlObservation.class);
                observer.clear();
                var mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                var response = mvc.perform(post("/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":" + product.id() + ",\"quantity\":2}]}"))
                        .andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(201);
                var httpId = context.getBean(ObjectMapper.class).readTree(response.getContentAsByteArray()).get("id").asLong();
                createdOrders.add(httpId);
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                var afterHttp = probe.snapshot();
                assertThat(afterHttp.orders()).hasSize(1);
                assertThat(afterHttp.items()).hasSize(1);
                assertThat(afterHttp.orders().get(0).id()).isEqualTo(httpId);
                var service = context.getBean(OrderService.class);
                assertThat(AopUtils.isAopProxy(service)).isTrue();
                var result = service.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(product.id(), 3))),
                        new AuthenticatedAccountPrincipal(buyer.id()));
                createdOrders.add(result.id());
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                var snapshot = probe.snapshot();
                assertThat(snapshot.products()).containsExactly(productRow(product));
                assertThat(snapshot.categories()).containsExactly(new AdminProductDatabaseProbe.CategoryRow(category.id(), category.name()));
                assertThat(snapshot.associations()).containsExactly(pair);
                assertThat(snapshot.orders()).extracting(AdminProductDatabaseProbe.OrderRow::id).containsExactlyElementsOf(createdOrders);
                assertThat(snapshot.orders()).allSatisfy(order -> {
                    assertThat(order.buyerId()).isEqualTo(buyer.id());
                    assertThat(order.createdAt()).isNotNull();
                    assertThat(order.status()).isEqualTo("WAITING_PAYMENT");
                });
                assertThat(snapshot.orders().get(1).createdAt()).isEqualTo(result.createdAt());
                assertThat(snapshot.items()).hasSize(2);
                for (int index = 0; index < snapshot.items().size(); index++) {
                    var item = snapshot.items().get(index);
                    assertThat(item.id()).isPositive();
                    assertThat(item.orderId()).isEqualTo(createdOrders.get(index));
                    assertThat(item.position()).isZero();
                    assertThat(item.productId()).isEqualTo(product.id());
                    assertThat(item.quantity()).isEqualTo(index + 2);
                    assertThat(item.unitPrice()).isEqualByComparingTo("10.00");
                    assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                            .isEqualByComparingTo(index == 0 ? "20.00" : "30.00");
                }
                assertThat(observer.events().stream().anyMatch(event -> event.sql().startsWith("select"))).isTrue();
                assertThat(observer.events().stream().anyMatch(event -> event.sql().startsWith("insert into tb_order"))).isTrue();
                assertThat(observer.events().stream().filter(event -> event.sql().startsWith("insert"))
                        .allMatch(AdminProductDatabaseProbe.SqlEvent::transactionActive)).isTrue();
                var cleanup = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                cleanup.executeWithoutResult(status -> createdOrders.forEach(id -> {
                    jdbc.update("delete from tb_order_item where order_id = ?", id);
                    jdbc.update("delete from tb_order where id = ?", id);
                }));
            }
            assertEmpty(probe.snapshot());
            assertThat(probe.accountIds()).isEmpty();
        });
    }

    @Test
    void shouldCompareCompleteSyntheticPagesWithFixtureIdsIncludingTheEmptyNextPage() {
        var expected = List.of(5L, 3L, 4L, 2L, 1L);
        var pages = pages(expected, 2);
        var extended = new ArrayList<>(pages);
        extended.add(new PageResponseDTO<>(List.of(), 3, 2, 5, 3));
        assertThat(assertCompletePages(extended, 2, expected, Long::longValue)).isEqualTo(expected);
        var all = List.of(6L, 5L, 3L, 4L, 2L, 1L);
        assertThat(assertCompletePages(pages(all, 2), 2, all, Long::longValue)).isEqualTo(all);
        assertThat(assertCompletePages(List.of(new PageResponseDTO<Long>(List.of(), 0, 2, 0, 0)),
                2, List.of(), Long::longValue)).isEmpty();
    }

    @Test
    void shouldRejectDuplicatedLostReorderedOrInconsistentSyntheticPages() {
        var expected = List.of(5L, 3L, 4L, 2L, 1L);
        var good = pages(expected, 2);
        var invalidFirstPages = List.of(
                new PageResponseDTO<>(List.of(5L, 5L), 0, 2, 5, 3),
                new PageResponseDTO<>(List.of(3L, 5L), 0, 2, 5, 3),
                new PageResponseDTO<>(List.of(5L), 0, 2, 5, 3),
                new PageResponseDTO<>(List.of(5L, 3L), 0, 2, 6, 3),
                new PageResponseDTO<>(List.of(5L, 3L), 0, 2, 5, 4),
                new PageResponseDTO<>(List.of(5L, 3L), 1, 2, 5, 3),
                new PageResponseDTO<>(List.of(5L, 3L), 0, 3, 5, 3));
        for (var invalid : invalidFirstPages) {
            var input = new ArrayList<>(good);
            input.set(0, invalid);
            assertThatThrownBy(() -> assertCompletePages(input, 2, expected, Long::longValue)).isInstanceOf(AssertionError.class);
        }
        assertThatThrownBy(() -> assertCompletePages(good.subList(0, 2), 2, expected, Long::longValue))
                .isInstanceOf(AssertionError.class);
        var repeated = new ArrayList<>(good);
        repeated.set(1, new PageResponseDTO<>(List.of(3L, 4L), 1, 2, 5, 3));
        assertThatThrownBy(() -> assertCompletePages(repeated, 2, expected, Long::longValue)).isInstanceOf(AssertionError.class);
    }

    private static List<PageResponseDTO<Long>> pages(List<Long> ids, int size) {
        var pages = new ArrayList<PageResponseDTO<Long>>();
        int totalPages = (ids.size() + size - 1) / size;
        for (int page = 0; page < totalPages; page++) {
            pages.add(new PageResponseDTO<>(List.copyOf(ids.subList(page * size, Math.min(ids.size(), (page + 1) * size))),
                    page, size, ids.size(), totalPages));
        }
        return pages;
    }

    private static AdminProductDatabaseProbe probe(WebApplicationContext context) {
        return new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
    }

    private static AdminProductDatabaseProbe.ProductRow productRow(ProductData product) {
        return new AdminProductDatabaseProbe.ProductRow(product.id(), product.name(), product.description(), product.image(), product.price());
    }

    private static void assertEmpty(AdminProductDatabaseProbe.Snapshot snapshot) {
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
    }

    // Plain bean holder: only withUserConfiguration registers it, never component scanning.
    static class ObservationConfig {
        @Bean
        SqlObservation adminSupportSqlObservation() { return new SqlObservation(); }

        @Bean
        HibernatePropertiesCustomizer adminSupportHibernateObservation(SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
