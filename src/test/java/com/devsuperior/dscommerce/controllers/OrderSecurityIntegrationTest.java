package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Real login/encoder/decoder/filters; successful order authorization remains a future assertion. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(OrderTestConfig.class)
class OrderSecurityIntegrationTest extends OrderHttpTestSupport {

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @ParameterizedTest(name = "database profiles {0}, token variant {1}")
    @MethodSource("authorizedScenarios")
    void shouldUseSubjectAndDatabaseProfilesRegardlessOfNameAndRolesClaims(
            Set<UserRole> roles, TokenVariant variant) throws Exception {
        var account = login(roles);
        var decoyName = "other-owner-" + UUID.randomUUID();
        var decoyId = fixture.createAccount(decoyName, "other-owner-test-password", Set.of());
        ownedAccounts.add(decoyId);
        assertThat(decoyId).isNotEqualTo(account.id());
        assertDatabaseRoles(decoyId, Set.of());
        var product = fixture.createProduct("security-positive-price", new BigDecimal("10.00"));
        var token = account.token();
        if (variant != TokenVariant.LOGIN) {
            var falseRoles = variant == TokenVariant.OTHER_NAME_EMPTY_ROLES
                    ? List.<String>of() : List.of("ROLE_AUDITOR");
            var claims = JwtClaimsSet.builder().claims(values -> values.putAll(account.decoded().getClaims()))
                    .claim("name", decoyName).claim("roles", falseRoles).build();
            // Existing real HS256 encoder and disposable test key, no token postprocessor/mock decoder.
            token = fixture.signedToken(claims);
            var decoded = decodeWithoutLeakingToken(token);
            assertThat(decoded.getSubject()).isEqualTo(account.decoded().getSubject());
            assertThat(decoded.getIssuedAt()).isEqualTo(account.decoded().getIssuedAt());
            assertThat(decoded.getExpiresAt()).isEqualTo(account.decoded().getExpiresAt());
            assertThat(decoded.getClaimAsString("name")).isEqualTo(decoyName);
            assertThat(decoded.getClaimAsStringList("roles")).containsExactlyElementsOf(falseRoles);
        }
        // No CLIENT/ADMIN claims are needed: permission must come from the existing account's DB roles.
        assertDatabaseRoles(account.id(), roles);
        var body = """
                {"items":[{"productId":%d,"quantity":2}]}
                """.formatted(product);

        var response = assertConfirmation(token, body, account.id(),
                List.of(new ExpectedLine(product, 2, "10.00", "20.00")), "20.00");

        assertThat(response.path("accountId").longValue()).isNotEqualTo(decoyId);
        // Order SQL is reached only after HTTP 201; fresh connections, no external test transaction.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        var independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        var orderId = response.path("id").longValue();
        Long storedBuyer = independent.queryForObject("select buyer_id from tb_order where id = ?", Long.class, orderId);
        assertThat(storedBuyer).isEqualTo(account.id());
        assertThat(independent.queryForObject("select count(*) from tb_order where buyer_id = ?",
                Integer.class, account.id())).isEqualTo(1);
        assertThat(independent.queryForObject("select count(*) from tb_order where buyer_id = ?",
                Integer.class, decoyId)).isZero();
    }

    static Stream<Arguments> authorizedScenarios() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN), Set.of(UserRole.CLIENT, UserRole.ADMIN))
                .flatMap(roles -> Stream.of(TokenVariant.values()).map(variant -> Arguments.of(roles, variant)));
    }

    enum TokenVariant {
        LOGIN, OTHER_NAME_EMPTY_ROLES, OTHER_NAME_UNRELATED_ROLES
    }
}
