package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Spring proxy, repository and isolated H2; the test itself has no transaction. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@Import({OrderTestConfig.class, AuthenticatedAccountServiceIntegrationTest.ObservationConfig.class})
class AuthenticatedAccountServiceIntegrationTest {

    @Autowired
    private AuthenticatedAccountService service;

    @Autowired
    private OrderTestFixture fixture;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Environment environment;

    @Autowired
    private ResolutionObservation observation;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @AfterEach
    void cleanOnlyOwnedFixtures() {
        fixture.cleanUp();
        observation.clear();
    }

    @ParameterizedTest(name = "real H2 roles {0}")
    @MethodSource("roleSets")
    void shouldResolveThroughReadOnlyProxyAndUseValuesAfterTransaction(Set<UserRole> roles) {
        var accountId = fixture.createAccount("proxy-account", "proxy-test-password", roles);
        observation.clear();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertNoPersistenceContextOrTransaction();

        var result = service.resolve(accountId);

        assertNoPersistenceContextOrTransaction();
        assertThat(observation.queries).hasSize(1).allSatisfy(query -> {
            assertThat(query.sql()).contains("left join tb_user_account_role");
            assertThat(query.active()).isTrue();
            assertThat(query.readOnly()).isTrue();
        });
        // The inner observer only observes the existing proxy; it never opens a transaction.
        assertThat(observation.mappings).hasSize(1).allSatisfy(mapping -> {
            assertThat(mapping.active()).isTrue();
            assertThat(mapping.readOnly()).isTrue();
            assertThat(mapping.authorityCount()).isEqualTo(roles.size());
        });

        // Remove the backing rows before using the detached, immutable security values.
        fixture.cleanUp();
        assertThat(jdbc.queryForObject("select count(*) from tb_user_account where id = ?",
                Integer.class, accountId)).isZero();
        assertThat(result.principal().accountId()).isEqualTo(accountId);
        assertThat(result.authorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrderElementsOf(roles.stream().map(role -> "ROLE_" + role.name()).toList());
        assertThatThrownBy(() -> result.authorities().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertNoPersistenceContextOrTransaction();
    }

    @Test
    void shouldRejectAnAbsentAccountWithoutLeavingATransactionOpen() {
        observation.clear();
        assertNoPersistenceContextOrTransaction();

        assertThatThrownBy(() -> service.resolve(Long.MAX_VALUE))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Authentication required or invalid");

        assertNoPersistenceContextOrTransaction();
        assertThat(observation.queries).hasSize(1).allSatisfy(query -> {
            assertThat(query.active()).isTrue();
            assertThat(query.readOnly()).isTrue();
        });
    }

    @Test
    void shouldKeepARealDatabaseFailureDistinctFromAnAbsentAccount() {
        var accountId = fixture.createAccount("failing-proxy-account", "proxy-test-password", Set.of(UserRole.CLIENT));
        // SQL failure is confined to this test's H2 and restored before fixture cleanup.
        jdbc.execute("alter table tb_user_account_role rename to t009_hidden_account_role");
        try {
            observation.clear();
            assertNoPersistenceContextOrTransaction();

            assertThatThrownBy(() -> service.resolve(accountId))
                    .isInstanceOf(OrderAccountResolutionException.class)
                    .isNotInstanceOf(BadCredentialsException.class)
                    .hasMessage("Account resolution could not be completed")
                    .hasCauseInstanceOf(DataAccessException.class);

            assertNoPersistenceContextOrTransaction();
            assertThat(observation.queries).hasSize(1).allSatisfy(query -> {
                assertThat(query.active()).isTrue();
                assertThat(query.readOnly()).isTrue();
            });
        } finally {
            jdbc.execute("alter table t009_hidden_account_role rename to tb_user_account_role");
        }
    }

    private void assertNoPersistenceContextOrTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.hasResource(entityManagerFactory)).isFalse();
    }

    static Stream<Set<UserRole>> roleSets() {
        return Stream.of(Set.of(UserRole.CLIENT), Set.of(UserRole.ADMIN),
                Set.of(UserRole.CLIENT, UserRole.ADMIN), Set.of());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {

        @Bean
        static ResolutionObservation resolutionObservation() {
            return new ResolutionObservation();
        }

        @Bean
        HibernatePropertiesCustomizer inspectAccountSql(ResolutionObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                    (StatementInspector) sql -> {
                        var normalized = sql.toLowerCase(Locale.ROOT);
                        if (normalized.contains("from tb_user_account ")) {
                            observation.queries.add(new QueryObservation(normalized,
                                    TransactionSynchronizationManager.isActualTransactionActive(),
                                    TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
                        }
                        return sql;
                    });
        }

        @Bean
        static BeanPostProcessor observeMappingInsideExistingProxy(ResolutionObservation observation) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof AuthenticatedAccountService && bean instanceof Advised proxy) {
                        // Append inside the existing TransactionInterceptor, without replacing it.
                        proxy.addAdvice((MethodInterceptor) invocation -> {
                            var result = invocation.proceed();
                            if (result instanceof AuthenticatedAccountService.ResolvedAccount resolved) {
                                observation.mappings.add(new MappingObservation(
                                        TransactionSynchronizationManager.isActualTransactionActive(),
                                        TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                                        resolved.authorities().size()));
                            }
                            return result;
                        });
                    }
                    return bean;
                }
            };
        }
    }

    static class ResolutionObservation {
        private final List<QueryObservation> queries = new CopyOnWriteArrayList<>();
        private final List<MappingObservation> mappings = new CopyOnWriteArrayList<>();

        void clear() {
            queries.clear();
            mappings.clear();
        }
    }

    record QueryObservation(String sql, boolean active, boolean readOnly) {
    }

    record MappingObservation(boolean active, boolean readOnly, int authorityCount) {
    }
}
