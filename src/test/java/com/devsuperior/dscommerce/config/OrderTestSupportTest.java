package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.services.AuthenticationService;
import com.devsuperior.dscommerce.services.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderTestSupportTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withInitializer(new TestComponentExclusion())
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=test")
            .withUserConfiguration(DscommerceApplication.class, OrderTestConfig.class);

    @Test
    void shouldControlTimeAndShareItAcrossZoneViews() {
        var initial = Instant.parse("2030-01-01T12:00:00.123Z");
        var clock = new OrderTestConfig.ControlledClock(initial);
        var otherZone = clock.withZone(ZoneId.of("America/Sao_Paulo"));

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
        assertThat(otherZone.getZone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
        assertThat(clock.instant()).isEqualTo(initial);
        clock.advance(Duration.ofMillis(877));
        assertThat(otherZone.instant()).isEqualTo(initial.plusMillis(877));
        clock.setInstant(initial.minusSeconds(1));
        assertThat(otherZone.instant()).isEqualTo(initial.minusSeconds(1));
    }

    @Test
    void shouldCommitFixturesAndCleanOnlyOwnedRowsInIsolatedContexts() {
        var firstUrl = OrderTestConfig.isolatedDatabaseUrl();
        var secondUrl = OrderTestConfig.isolatedDatabaseUrl();
        assertThat(firstUrl).isNotEqualTo(secondUrl);

        runner.withPropertyValues("spring.datasource.url=" + firstUrl).run(first -> {
            assertThat(first).hasNotFailed();
            assertThat(first.getEnvironment().getActiveProfiles()).contains("test");
            var fixture = first.getBean(OrderTestFixture.class);
            var independent = new JdbcTemplate(new DriverManagerDataSource(firstUrl, "sa", ""));
            var seedProducts = independent.queryForList("select * from tb_product order by id");
            var seedAccounts = independent.queryForList("select * from tb_user_account order by id");
            var seedLinks = independent.queryForList(
                    "select * from tb_product_category order by product_id, category_id");
            assertThat(seedProducts).hasSize(52);
            assertThat(seedAccounts).hasSize(1);

            var accountId = new AtomicLong();
            var productId = new AtomicLong();
            try {
                // A fixture commits independently even when the caller's transaction rolls back.
                var outer = new TransactionTemplate(first.getBean(PlatformTransactionManager.class));
                assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                    accountId.set(fixture.createAccount("order-support-account", "test-password"));
                    productId.set(fixture.createProduct("order-support-product", new BigDecimal("10.00")));
                    throw new IllegalStateException("rollback caller only");
                })).isInstanceOf(IllegalStateException.class).hasMessage("rollback caller only");

                assertThat(independent.queryForObject(
                        "select name from tb_user_account where id = ?", String.class, accountId.get()))
                        .isEqualTo("order-support-account");
                assertThat(independent.queryForObject(
                        "select price from tb_product where id = ?", BigDecimal.class, productId.get()))
                        .isEqualByComparingTo("10.00");
                fixture.setProductPrice(productId.get(), new BigDecimal("-1.00"));
                assertThat(independent.queryForObject(
                        "select price from tb_product where id = ?", BigDecimal.class, productId.get()))
                        .isEqualByComparingTo("-1.00");
                assertThatThrownBy(() -> fixture.setProductPrice(1L, BigDecimal.ZERO))
                        .isInstanceOf(IllegalArgumentException.class);

                independent.update("insert into tb_product_category (product_id, category_id) values (?, 1)",
                        productId.get());
                runner.withPropertyValues("spring.datasource.url=" + secondUrl).run(second -> {
                    assertThat(second).hasNotFailed();
                    var otherJdbc = second.getBean(JdbcTemplate.class);
                    assertThat(otherJdbc.queryForList("select * from tb_product order by id"))
                            .isEqualTo(seedProducts);
                    assertThat(otherJdbc.queryForList("select * from tb_user_account order by id"))
                            .isEqualTo(seedAccounts);
                });
            } finally {
                fixture.cleanUp();
            }
            // Cleanup is explicit and idempotent; seeded data and links remain untouched.
            fixture.cleanUp();
            assertThat(independent.queryForList("select * from tb_product order by id"))
                    .isEqualTo(seedProducts);
            assertThat(independent.queryForList("select * from tb_user_account order by id"))
                    .isEqualTo(seedAccounts);
            assertThat(independent.queryForList(
                    "select * from tb_product_category order by product_id, category_id"))
                    .isEqualTo(seedLinks);
        });
    }

    @Test
    void shouldCommitAllRoleFixturesAndCleanOnlyOwnedRows() {
        var url = OrderTestConfig.isolatedDatabaseUrl();
        runner.withPropertyValues("spring.datasource.url=" + url).run(context -> {
            assertThat(context).hasNotFailed();
            var fixture = context.getBean(OrderTestFixture.class);
            var independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
            var seedAccounts = independent.queryForList("select * from tb_user_account order by id");
            var seedRoles = independent.queryForList(
                    "select * from tb_user_account_role order by user_account_id, role");
            var otherOwner = new OrderTestFixture(context.getBean(JdbcTemplate.class),
                    context.getBean(PlatformTransactionManager.class), context.getBean(PasswordEncoder.class),
                    context.getBean(JwtEncoder.class));
            try {
                var otherId = otherOwner.createAccount("role-fixture-other-owner", "test-password", Set.of(UserRole.ADMIN));
                var preservedAccounts = independent.queryForList("select * from tb_user_account order by id");
                var preservedRoles = independent.queryForList(
                        "select * from tb_user_account_role order by user_account_id, role");
                var expected = new LinkedHashMap<String, Set<UserRole>>();
                expected.put("role-fixture-client", Set.of(UserRole.CLIENT));
                expected.put("role-fixture-admin", Set.of(UserRole.ADMIN));
                expected.put("role-fixture-both", Set.of(UserRole.CLIENT, UserRole.ADMIN));
                expected.put("role-fixture-none", Set.of());
                var ids = new LinkedHashMap<String, Long>();
                var outer = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
                    expected.forEach((name, roles) -> ids.put(name,
                            fixture.createAccount(name, "test-password", roles)));
                    throw new IllegalStateException("rollback caller only");
                })).isInstanceOf(IllegalStateException.class).hasMessage("rollback caller only");

                assertThat(ids.values()).doesNotHaveDuplicates().hasSize(4);
                expected.forEach((name, roles) -> {
                    var id = ids.get(name);
                    assertThat(independent.queryForObject(
                            "select name from tb_user_account where id = ?", String.class, id)).isEqualTo(name);
                    assertRoles(independent, id, roles);
                });
                assertRoles(independent, otherId, Set.of(UserRole.ADMIN));
                fixture.cleanUp();
                fixture.cleanUp();
                assertThat(independent.queryForList("select * from tb_user_account order by id"))
                        .isEqualTo(preservedAccounts);
                assertThat(independent.queryForList(
                        "select * from tb_user_account_role order by user_account_id, role"))
                        .isEqualTo(preservedRoles);
            } finally {
                fixture.cleanUp();
                otherOwner.cleanUp();
            }
            assertThat(independent.queryForList("select * from tb_user_account order by id")).isEqualTo(seedAccounts);
            assertThat(independent.queryForList(
                    "select * from tb_user_account_role order by user_account_id, role")).isEqualTo(seedRoles);
        });
    }

    @Test
    void shouldIsolateRoleFixturesAndTheirCleanupAcrossContexts() {
        var firstUrl = OrderTestConfig.isolatedDatabaseUrl();
        var secondUrl = OrderTestConfig.isolatedDatabaseUrl();
        assertThat(firstUrl).isNotEqualTo(secondUrl);
        runner.withPropertyValues("spring.datasource.url=" + firstUrl).run(first -> {
            assertThat(first).hasNotFailed();
            var firstFixture = first.getBean(OrderTestFixture.class);
            var firstJdbc = new JdbcTemplate(new DriverManagerDataSource(firstUrl, "sa", ""));
            var firstAccounts = firstJdbc.queryForList("select * from tb_user_account order by id");
            var firstRoles = firstJdbc.queryForList(
                    "select * from tb_user_account_role order by user_account_id, role");
            try {
                var firstId = firstFixture.createAccount("same-role-fixture-name", "test-password", Set.of(UserRole.CLIENT));
                runner.withPropertyValues("spring.datasource.url=" + secondUrl).run(second -> {
                    assertThat(second).hasNotFailed();
                    var secondFixture = second.getBean(OrderTestFixture.class);
                    var secondJdbc = new JdbcTemplate(new DriverManagerDataSource(secondUrl, "sa", ""));
                    assertThat(secondJdbc.queryForList("select * from tb_user_account order by id"))
                            .isEqualTo(firstAccounts);
                    assertThat(secondJdbc.queryForList(
                            "select * from tb_user_account_role order by user_account_id, role")).isEqualTo(firstRoles);
                    try {
                        var secondId = secondFixture.createAccount("same-role-fixture-name", "test-password", Set.of(UserRole.ADMIN));
                        assertRoles(firstJdbc, firstId, Set.of(UserRole.CLIENT));
                        assertRoles(secondJdbc, secondId, Set.of(UserRole.ADMIN));
                        firstFixture.cleanUp();
                        assertThat(firstJdbc.queryForList("select * from tb_user_account order by id"))
                                .isEqualTo(firstAccounts);
                        assertThat(firstJdbc.queryForList(
                                "select * from tb_user_account_role order by user_account_id, role")).isEqualTo(firstRoles);
                        assertRoles(secondJdbc, secondId, Set.of(UserRole.ADMIN));
                        assertThat(secondJdbc.queryForObject(
                                "select name from tb_user_account where id = ?", String.class, secondId))
                                .isEqualTo("same-role-fixture-name");
                    } finally {
                        secondFixture.cleanUp();
                    }
                    assertThat(secondJdbc.queryForList("select * from tb_user_account order by id"))
                            .isEqualTo(firstAccounts);
                    assertThat(secondJdbc.queryForList(
                            "select * from tb_user_account_role order by user_account_id, role")).isEqualTo(firstRoles);
                });
            } finally {
                firstFixture.cleanUp();
            }
        });
    }

    @Test
    void shouldReuseRealEncodersAndDecoderWithTheTestProfileKey() {
        runner.withPropertyValues("spring.datasource.url=" + OrderTestConfig.isolatedDatabaseUrl())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class)
                            .hasSingleBean(PasswordEncoder.class);
                    assertThat(context.getBeansOfType(Clock.class)).containsOnlyKeys("orderClock", "orderTestClock");
                    var clock = context.getBean(OrderTestConfig.ControlledClock.class);
                    assertThat(context.getBean("orderTestClock")).isSameAs(clock);
                    assertThat(clock.instant()).isEqualTo(OrderTestConfig.INITIAL_INSTANT);
                    var productionClock = context.getBean("orderClock", Clock.class);
                    assertThat(productionClock).isNotSameAs(clock);
                    assertThat(productionClock.getZone()).isEqualTo(ZoneOffset.UTC);
                    assertThat(context.getBean(Clock.class)).isSameAs(clock);
                    OrderService target = AopTestUtils.getUltimateTargetObject(context.getBean(OrderService.class));
                    assertThat(ReflectionTestUtils.getField(target, "clock")).isSameAs(clock);
                    var fixture = context.getBean(OrderTestFixture.class);
                    var jdbc = context.getBean(JdbcTemplate.class);
                    try {
                        var accountId = fixture.createAccount("order-support-login", "test-password");
                        assertRoles(jdbc, accountId, Set.of());
                        var hash = jdbc.queryForObject(
                                "select password_hash from tb_user_account where id = ?", String.class, accountId);
                        assertThat(hash).isNotEqualTo("test-password");
                        assertThat(context.getBean(PasswordEncoder.class).matches("test-password", hash)).isTrue();

                        var response = context.getBean(AuthenticationService.class)
                                .login("order-support-login", "test-password");
                        assertThat(response.tokenType()).isEqualTo("Bearer");
                        var decoder = context.getBean(JwtDecoder.class);
                        var loginToken = decoder.decode(response.accessToken());
                        assertThat(loginToken.getSubject()).isEqualTo(Long.toString(accountId));
                        assertThat(loginToken.getClaims()).doesNotContainKeys("roles", "passwordHash", "secret");
                        var mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                                .addFilters(context.getBean(FilterChainProxy.class)).build();
                        var result = mvc.perform(post("/login").contentType("application/json")
                                        .content("{\"name\":\"order-support-login\",\"password\":\"test-password\"}"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.tokenType").value("Bearer")).andReturn();
                        var payload = context.getBean(ObjectMapper.class)
                                .readTree(result.getResponse().getContentAsString());
                        assertThat(payload.size()).isEqualTo(2);
                        assertThat(decoder.decode(payload.get("accessToken").asText()).getSubject())
                                .isEqualTo(Long.toString(accountId));
                        assertRoles(jdbc, accountId, Set.of());
                        var now = Instant.now();
                        var customToken = fixture.signedToken(JwtClaimsSet.builder()
                                .subject(Long.toString(accountId)).issuedAt(now)
                                .expiresAt(now.plusSeconds(300)).build());
                        assertThat(decoder.decode(customToken).getHeaders().get("alg")).hasToString("HS256");
                    } catch (Exception exception) {
                        throw new AssertionError("The account without roles must still log in through HTTP", exception);
                    } finally {
                        fixture.cleanUp();
                    }
                });
    }

    private static void assertRoles(JdbcTemplate jdbc, long accountId, Set<UserRole> expected) {
        assertThat(jdbc.queryForList(
                "select role from tb_user_account_role where user_account_id = ?", String.class, accountId))
                .as("persisted roles for account %s", accountId)
                .containsExactlyInAnyOrderElementsOf(expected.stream().map(UserRole::name).toList());
    }
}
