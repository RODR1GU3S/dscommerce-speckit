package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.services.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTestSupportTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
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
    void shouldReuseRealEncodersAndDecoderWithTheTestProfileKey() {
        runner.withPropertyValues("spring.datasource.url=" + OrderTestConfig.isolatedDatabaseUrl())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class)
                            .hasSingleBean(PasswordEncoder.class).hasSingleBean(Clock.class);
                    var clock = context.getBean(OrderTestConfig.ControlledClock.class);
                    assertThat(context.getBean(Clock.class)).isSameAs(clock);
                    var fixture = context.getBean(OrderTestFixture.class);
                    var jdbc = context.getBean(JdbcTemplate.class);
                    try {
                        var accountId = fixture.createAccount("order-support-login", "test-password");
                        var hash = jdbc.queryForObject(
                                "select password_hash from tb_user_account where id = ?", String.class, accountId);
                        assertThat(hash).isNotEqualTo("test-password");
                        assertThat(context.getBean(PasswordEncoder.class).matches("test-password", hash)).isTrue();

                        var response = context.getBean(AuthenticationService.class)
                                .login("order-support-login", "test-password");
                        var decoder = context.getBean(JwtDecoder.class);
                        assertThat(decoder.decode(response.accessToken()).getSubject())
                                .isEqualTo(Long.toString(accountId));
                        var now = Instant.now();
                        var customToken = fixture.signedToken(JwtClaimsSet.builder()
                                .subject(Long.toString(accountId)).issuedAt(now)
                                .expiresAt(now.plusSeconds(300)).build());
                        assertThat(decoder.decode(customToken).getHeaders().get("alg")).hasToString("HS256");
                    } finally {
                        fixture.cleanUp();
                    }
                });
    }
}
