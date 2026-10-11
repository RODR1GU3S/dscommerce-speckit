package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.config.OrderClockConfig;
import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.dto.OrderResponseDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.InvalidOrderException;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.devsuperior.dscommerce.services.exceptions.InvalidOrderException.Reason.UNREPRESENTABLE_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real bean/proxy/JPA verification; no test transaction, mock repository, endpoint or JWT adaptation. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@Import(OrderServiceContextTest.ObservationConfig.class)
class OrderServiceContextTest {

    @Autowired private OrderService service;
    @Autowired private Clock clock;
    @Autowired private ApplicationContext context;
    @Autowired private TransactionAttributeSource transactionAttributes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private Observation observation;

    private OrderTestFixture fixture;
    private Long buyerId;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void prepareOwnedFixtures() {
        // Do not import OrderTestConfig: the production Clock must satisfy injection here.
        fixture = new OrderTestFixture(jdbc, transactionManager, passwordEncoder, jwtEncoder);
        observation.sql.clear();
        observation.mappings.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRows() {
        if (buyerId != null) {
            jdbc.update("delete from tb_order_item where order_id in (select id from tb_order where buyer_id = ?)", buyerId);
            jdbc.update("delete from tb_order where buyer_id = ?", buyerId);
        }
        fixture.cleanUp();
    }

    @Test
    void shouldInitializeWithTheProductionUtcClockAndRequiredTransactionProxy() throws Exception {
        assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(context.getBean(OrderClockConfig.class)).isNotNull();
        assertThat(context.getBean("orderClock")).isSameAs(clock);
        assertThat(clock.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var method = OrderService.class.getMethod("register", OrderRequestDTO.class, AuthenticatedAccountPrincipal.class);
        var attribute = transactionAttributes.getTransactionAttribute(method, OrderService.class);
        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        assertThat(attribute.isReadOnly()).isFalse();
        assertThat(attribute.rollbackOn(new Exception("checked failure"))).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void shouldRegisterAndMapThroughTheProxyWithRealRepositoriesAndANewOrderPerCall() {
        buyerId = fixture.createAccount("t020-admin", "context-test-password", Set.of(UserRole.ADMIN));
        var first = fixture.createProduct("t020-first", new BigDecimal("10.00"));
        var second = fixture.createProduct("t020-second", new BigDecimal("7.50"));
        var request = new OrderRequestDTO(List.of(new OrderItemRequestDTO(second, 3), new OrderItemRequestDTO(first, 2)));
        observation.sql.clear();
        MethodInterceptor mappingObserver = invocation -> {
            Object result = invocation.proceed();
            // Appended inside the existing transaction advisor; it never opens a transaction.
            observation.mappings.add(result instanceof OrderResponseDTO && TransactionSynchronizationManager.isActualTransactionActive());
            return result;
        };
        var proxy = (Advised) service;
        proxy.addAdvice(mappingObserver);
        OrderResponseDTO firstResponse;
        OrderResponseDTO secondResponse;
        var earliest = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        try {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            firstResponse = service.register(request, new AuthenticatedAccountPrincipal(buyerId));
            secondResponse = service.register(request, new AuthenticatedAccountPrincipal(buyerId));
        } finally {
            proxy.removeAdvice(mappingObserver);
        }

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(observation.mappings).containsExactly(true, true);
        assertThat(observation.sql).isNotEmpty().allSatisfy(event -> {
            assertThat(event.active()).isTrue();
            assertThat(event.readOnly()).isFalse();
            assertThat(event.transactionName()).isEqualTo(OrderService.class.getName() + ".register");
        });
        assertThat(observation.sql.stream().filter(event -> event.statement().contains("left join tb_user_account_role"))).hasSize(2);
        assertThat(observation.sql.stream().filter(event -> event.statement().startsWith("insert into tb_order ("))).hasSize(2);
        assertThat(observation.sql.stream().filter(event -> event.statement().startsWith("insert into tb_order_item ("))).hasSize(4);
        assertThat(firstResponse.id()).isPositive();
        assertThat(secondResponse.id()).isPositive().isNotEqualTo(firstResponse.id());
        assertThat(firstResponse.accountId()).isEqualTo(buyerId);
        assertThat(firstResponse.createdAt()).isBetween(earliest, clock.instant());
        assertThat(firstResponse.createdAt().getNano() % 1_000_000).isZero();
        assertThat(firstResponse.status()).isEqualTo(OrderStatus.WAITING_PAYMENT);
        assertThat(firstResponse.items()).extracting(line -> line.productId()).containsExactly(second, first);
        assertThat(firstResponse.items()).extracting(line -> line.unitPrice()).containsExactly("7.50", "10.00");
        assertThat(firstResponse.items()).extracting(line -> line.subtotal()).containsExactly("22.50", "20.00");
        assertThat(firstResponse.total()).isEqualTo("42.50");
        assertThat(secondResponse.total()).isEqualTo("42.50");
        // JDBC reads happen after each proxy call completed, outside any test transaction.
        assertThat(jdbc.queryForObject("select count(*) from tb_order where buyer_id = ?", Integer.class, buyerId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from tb_order_item where order_id in (?, ?)",
                Integer.class, firstResponse.id(), secondResponse.id())).isEqualTo(4);
        assertThat(jdbc.queryForList("select product_id from tb_order_item where order_id = ? order by position", Long.class,
                firstResponse.id())).containsExactly(second, first);
    }

    @Test
    void shouldPersistAndMapTheDecimalCapacityExactly() {
        buyerId = fixture.createAccount("t020-capacity", "context-test-password", Set.of(UserRole.CLIENT));
        var product = fixture.createProduct("t020-capacity", new BigDecimal("99999999999999999.99"));
        var response = service.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(product, 1))),
                new AuthenticatedAccountPrincipal(buyerId));

        assertThat(response.total()).isEqualTo("99999999999999999.99");
        assertThat(response.items().get(0).unitPrice()).isEqualTo(response.total());
        assertThat(response.items().get(0).subtotal()).isEqualTo(response.total());
        assertThat(jdbc.queryForObject("select unit_price from tb_order_item where order_id = ?", BigDecimal.class,
                response.id())).isEqualTo(new BigDecimal("99999999999999999.99"));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void shouldStopBeforeWritingWhenSubtotalOrSumExceedsDecimalCapacity() {
        buyerId = fixture.createAccount("t020-overflow", "context-test-password", Set.of(UserRole.CLIENT));
        var maximum = fixture.createProduct("t020-maximum", new BigDecimal("99999999999999999.99"));
        var half1 = fixture.createProduct("t020-half1", new BigDecimal("50000000000000000.00"));
        var half2 = fixture.createProduct("t020-half2", new BigDecimal("50000000000000000.00"));
        observation.sql.clear();
        var principal = new AuthenticatedAccountPrincipal(buyerId);

        assertThatThrownBy(() -> service.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(maximum, 2))), principal))
                .isInstanceOfSatisfying(InvalidOrderException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(UNREPRESENTABLE_VALUE);
                    assertThat(exception).hasMessage("Order value cannot be represented");
                });
        assertThatThrownBy(() -> service.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(half1, 1),
                new OrderItemRequestDTO(half2, 1))), principal))
                .isInstanceOfSatisfying(InvalidOrderException.class, exception -> {
                    assertThat(exception.getReason()).isEqualTo(UNREPRESENTABLE_VALUE);
                    assertThat(exception).hasMessage("Order value cannot be represented");
                });

        assertThat(observation.sql).noneSatisfy(event -> assertThat(event.statement()).startsWith("insert"));
        assertThat(jdbc.queryForObject("select count(*) from tb_order where buyer_id = ?", Integer.class, buyerId)).isZero();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        // No order insert happened: this is pre-persistence capacity checking, not a rollback proof.
    }

    record SqlEvent(String statement, boolean active, boolean readOnly, String transactionName) { }

    static class Observation {
        final List<SqlEvent> sql = new CopyOnWriteArrayList<>();
        final List<Boolean> mappings = new CopyOnWriteArrayList<>();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean
        Observation orderServiceObservation() {
            return new Observation();
        }

        @Bean
        HibernatePropertiesCustomizer observeOrderServiceSql(Observation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                    (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> {
                        observation.sql.add(new SqlEvent(sql.toLowerCase(Locale.ROOT),
                                TransactionSynchronizationManager.isActualTransactionActive(),
                                TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                                TransactionSynchronizationManager.getCurrentTransactionName()));
                        return sql;
                    });
        }
    }
}
