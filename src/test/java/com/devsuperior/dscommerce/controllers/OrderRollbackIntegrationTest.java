package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemResponseDTO;
import com.devsuperior.dscommerce.dto.OrderResponseDTO;
import com.devsuperior.dscommerce.entities.Order;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.services.OrderService;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Separate real SQL, post-flush processing and beforeCommit failure proofs. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({OrderTestConfig.class, OrderRollbackIntegrationTest.SqlObservationConfig.class,
        OrderRollbackIntegrationTest.RepositorySpyConfig.class})
class OrderRollbackIntegrationTest extends OrderHttpTestSupport {

    @Autowired private OrderService service;
    @Autowired private SqlInsertProbe probe;
    @Autowired private OrderRepository repositorySpy;
    @Autowired private PostFlushProbe postFlush;
    private CompletionProbe completion;
    private JdbcTemplate independent;
    private String temporaryConstraint;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void prepareIndependentConnection() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(service).isInstanceOf(Advised.class);
        assertThat(jdbc.getDataSource()).isInstanceOf(ObservedDataSource.class);
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        independent = new JdbcTemplate(new DriverManagerDataSource(Objects.requireNonNull(url), "sa", ""));
        assertThat(url).startsWith("jdbc:h2:mem:order-test-");
        probe.clear();
    }

    @AfterEach
    void removeConstraintAfterAssertions() {
        // Subclass teardown runs before superclass fixture cleanup, including after assertion failures.
        probe.clear();
        if (temporaryConstraint != null) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            independent.execute("alter table tb_order_item drop constraint " + temporaryConstraint);
            assertThat(constraintExists()).isFalse();
            temporaryConstraint = null;
        }
    }

    @AfterEach
    void disarmPostFlushFaultAfterAssertions() {
        postFlush.disarm();
    }

    @AfterEach
    void removeCompletionObserversAfterAssertions() {
        if (completion != null) completion.uninstall();
    }

    @Test
    void shouldRollbackWhenBeforeCommitFailsAfterServiceProducesDto() throws Exception {
        assertThat(temporaryConstraint).isNull();
        assertThat(postFlush.buyer).as("T030 processing fault must remain disarmed").isNull();
        assertThat(independent.queryForObject("select count(*) from information_schema.table_constraints "
                + "where constraint_name like 'CK_T029_SECOND_%'", Long.class)).isZero();
        var account = login(Set.of(UserRole.CLIENT));
        var first = fixture.createProduct("before-commit-first", new BigDecimal("10.00"));
        var second = fixture.createProduct("before-commit-second", new BigDecimal("7.50"));
        assertCommittedFixture(account.id(), first, second);
        var baseline = snapshot();
        probe.arm(account.id(), null);
        completion = new CompletionProbe((Advised) service, jdbc, account.id());
        completion.install();

        Outcome outcome;
        DatabaseState after;
        try {
            outcome = perform(account.token(), body(first, 2, second, 3));
        } finally {
            // Check after proxy exit even on an unexpected MVC escape, before any observer/fixture cleanup.
            after = snapshot();
            System.out.printf("T031_INDEPENDENT baselineOrders=%d baselineItems=%d afterOrders=%d afterItems=%d baselineRestored=%s%n",
                    baseline.orders().size(), baseline.items().size(), after.orders().size(), after.items().size(),
                    after.equals(baseline));
        }
        var steps = probe.steps();
        System.out.printf("T031_BODY produced=%s tx=%s total=%s callbackBeforeBody=%s proxyReturned=%s%n",
                completion.dto != null, completion.bodyTransactionActive,
                completion.dto == null ? "NONE" : completion.dto.total(), completion.callbackBeforeBody, completion.proxyReturned);
        System.out.printf("T031_BEFORE_COMMIT reached=%s hadDto=%s interceptorCompletion=%s tx=%s boundConnection=%s autoCommit=%s readOnly=%s visibleOrders=%d visibleItems=%d completedInserts=%d failedInserts=%d%n",
                completion.beforeCommitReached, completion.callbackDto != null, completion.interceptorCompletion,
                completion.callbackTransactionActive, completion.boundConnection, completion.autoCommit, completion.readOnly,
                completion.view.orders().size(), completion.view.items().size(),
                steps.stream().filter(SqlStep::completed).count(), steps.stream().filter(step -> !step.completed()).count());
        System.out.printf("T031_FAULT thrown=%s sameException=%s events=%s%n",
                completion.exceptionThrown, outcome.applicationFailure() == completion.failure, completion.events);
        System.out.printf("T031_HTTP status=%s escaped=%s%n",
                outcome.result() == null ? "NONE" : outcome.result().getResponse().getStatus(),
                outcome.result() == null && outcome.applicationFailure() != null
                        ? outcome.applicationFailure().getClass().getSimpleName() : "NONE");
        assertAll(
                () -> {
                    assertThat(completion.dto).as("real body returned its mapped DTO inside transaction advice").isNotNull();
                    assertThat(completion.dto.id()).isPositive();
                    assertThat(completion.dto).isEqualTo(new OrderResponseDTO(completion.dto.id(), account.id(),
                            clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), OrderStatus.WAITING_PAYMENT,
                            List.of(new OrderItemResponseDTO(first, 2, "10.00", "20.00"),
                                    new OrderItemResponseDTO(second, 3, "7.50", "22.50")), "42.50"));
                    assertThat(completion.bodyTransactionActive).isTrue();
                    assertThat(completion.callbackBeforeBody).isFalse();
                    assertThat(completion.proxyEntered).isTrue();
                    assertThat(completion.proxyReturned).as("internal DTO production is not an external proxy return").isFalse();
                },
                () -> {
                    assertThat(completion.beforeCommitReached).isTrue();
                    assertThat(completion.callbackDto).isSameAs(completion.dto);
                    assertThat(completion.interceptorCompletion).isTrue();
                    assertThat(completion.callbackTransactionActive).isTrue();
                    assertThat(completion.boundConnection).isTrue();
                    assertThat(completion.autoCommit).isFalse();
                    assertThat(completion.readOnly).isFalse();
                    assertThat(steps).hasSize(3).allMatch(SqlStep::completed)
                            .allMatch(step -> step.affected() == 1 && step.transactionActive() && !step.autoCommit());
                    assertThat(steps).extracting(SqlStep::table)
                            .containsExactly("tb_order", "tb_order_item", "tb_order_item");
                    assertThat(completion.view.orders()).containsExactly(new OrderRow(completion.dto.id(), account.id(),
                            completion.dto.createdAt(), "WAITING_PAYMENT"));
                    assertThat(completion.view.items()).hasSize(2);
                    assertThat(completion.view.items()).extracting(ItemRow::orderId)
                            .containsExactly(completion.dto.id(), completion.dto.id());
                    assertThat(completion.view.items()).extracting(ItemRow::productId).containsExactly(first, second);
                    assertThat(completion.view.items()).extracting(ItemRow::position).containsExactly(0, 1);
                    assertThat(completion.view.items()).extracting(ItemRow::quantity).containsExactly(2, 3);
                    assertThat(completion.view.items()).extracting(ItemRow::unitPrice)
                            .containsExactly(new BigDecimal("10.00"), new BigDecimal("7.50"));
                    assertThat(completion.view.items()).extracting(ItemRow::subtotal)
                            .containsExactly(new BigDecimal("20.00"), new BigDecimal("22.50"));
                    assertThat(completion.view.items().stream().map(ItemRow::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                            .isEqualTo(new BigDecimal("42.50"));
                },
                () -> {
                    assertThat(completion.events).containsExactly("SYNC_REGISTERED", "DTO_PRODUCED", "BEFORE_COMMIT",
                            "ROWS_OBSERVED", "CALLBACK_EXCEPTION");
                    assertThat(completion.exceptionThrown).isTrue();
                    assertThat(outcome.applicationFailure()).isSameAs(completion.failure)
                            .isInstanceOf(BeforeCommitTestFailure.class);
                },
                () -> assertThat(after).as("complete baseline after proxy exit, before cleanup").isEqualTo(baseline),
                () -> assertError(outcome, 500, "Order registration could not be completed", account.token()));
    }

    @Test
    void shouldRollbackAfterRealSaveAndFlushReturnsBeforeServiceCompletes() throws Exception {
        assertThat(mockingDetails(repositorySpy).isSpy()).isTrue();
        OrderService serviceTarget = AopTestUtils.getUltimateTargetObject(service);
        assertThat(ReflectionTestUtils.getField(serviceTarget, "orders"))
                .as("Spring service constructor received this repository spy").isSameAs(repositorySpy);
        assertThat(AopUtils.isAopProxy(postFlush.realRepository)).isTrue();
        assertThat(mockingDetails(postFlush.realRepository).isMock()).isFalse();
        assertThat(temporaryConstraint).isNull();
        assertThat(independent.queryForObject("select count(*) from information_schema.table_constraints "
                + "where constraint_name like 'CK_T029_SECOND_%'", Long.class)).isZero();
        var account = login(Set.of(UserRole.CLIENT));
        var first = fixture.createProduct("post-flush-first", new BigDecimal("10.00"));
        var second = fixture.createProduct("post-flush-second", new BigDecimal("7.50"));
        assertCommittedFixture(account.id(), first, second);
        var baseline = snapshot();
        clearInvocations(repositorySpy);
        probe.arm(account.id(), null);
        postFlush.arm(account.id(), jdbc);

        Outcome outcome;
        DatabaseState after;
        try {
            outcome = perform(account.token(), body(first, 2, second, 3));
        } finally {
            // Also read after an unexpected escaped MVC exception, before any teardown or cleanup.
            after = snapshot();
            System.out.printf("T030_INDEPENDENT baselineOrders=%d baselineItems=%d afterOrders=%d afterItems=%d baselineRestored=%s%n",
                    baseline.orders().size(), baseline.items().size(), after.orders().size(), after.items().size(),
                    after.equals(baseline));
        }
        var steps = probe.steps();
        System.out.printf("T030_FLUSH returned=%s orderId=%s tx=%s boundConnection=%s autoCommit=%s visibleOrders=%d visibleItems=%d completedInserts=%d failedInserts=%d%n",
                postFlush.flushReturned, postFlush.savedId, postFlush.transactionActive, postFlush.boundConnection,
                postFlush.autoCommit, postFlush.view.orders().size(), postFlush.view.items().size(),
                steps.stream().filter(SqlStep::completed).count(), steps.stream().filter(step -> !step.completed()).count());
        System.out.printf("T030_FAULT thrown=%s sameException=%s http=%s escaped=%s%n",
                postFlush.exceptionThrown, outcome.applicationFailure() == postFlush.failure,
                outcome.result() == null ? "NONE" : outcome.result().getResponse().getStatus(),
                outcome.result() == null && outcome.applicationFailure() != null
                        ? outcome.applicationFailure().getClass().getSimpleName() : "NONE");
        assertAll(
                () -> {
                    verify(repositorySpy, times(1)).saveAndFlush(any(Order.class));
                    assertThat(postFlush.flushReturned).isTrue();
                    assertThat(postFlush.savedId).isPositive();
                    assertThat(postFlush.transactionActive).isTrue();
                    assertThat(postFlush.boundConnection).isTrue();
                    assertThat(postFlush.autoCommit).isFalse();
                    assertThat(steps).hasSize(3).allMatch(SqlStep::completed)
                            .allMatch(step -> step.affected() == 1 && step.transactionActive() && !step.autoCommit());
                    assertThat(steps).extracting(SqlStep::table)
                            .containsExactly("tb_order", "tb_order_item", "tb_order_item");
                    assertThat(steps.get(1).productId()).isEqualTo(first);
                    assertThat(steps.get(2).productId()).isEqualTo(second);
                    assertThat(postFlush.view.orders()).containsExactly(new OrderRow(postFlush.savedId, account.id(),
                            clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS), "WAITING_PAYMENT"));
                    assertThat(postFlush.view.items()).hasSize(2);
                    assertThat(postFlush.view.items()).extracting(ItemRow::orderId)
                            .containsExactly(postFlush.savedId, postFlush.savedId);
                    assertThat(postFlush.view.items()).extracting(ItemRow::productId).containsExactly(first, second);
                    assertThat(postFlush.view.items()).extracting(ItemRow::position).containsExactly(0, 1);
                    assertThat(postFlush.view.items()).extracting(ItemRow::quantity).containsExactly(2, 3);
                    assertThat(postFlush.view.items()).extracting(ItemRow::unitPrice)
                            .containsExactly(new BigDecimal("10.00"), new BigDecimal("7.50"));
                    assertThat(postFlush.view.items()).extracting(ItemRow::subtotal)
                            .containsExactly(new BigDecimal("20.00"), new BigDecimal("22.50"));
                    assertThat(postFlush.view.items().stream().map(ItemRow::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                            .isEqualTo(new BigDecimal("42.50"));
                },
                () -> {
                    assertThat(postFlush.exceptionThrown).isTrue();
                    assertThat(outcome.applicationFailure()).isSameAs(postFlush.failure)
                            .isInstanceOf(PostFlushTestFailure.class);
                },
                () -> assertThat(after).as("complete baseline after proxy exit, before cleanup").isEqualTo(baseline),
                () -> assertError(outcome, 500, "Order registration could not be completed", account.token()));
    }

    @Test
    void shouldRollbackCompletedOrderAndFirstItemWhenSecondProductFailsInSql() throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var first = fixture.createProduct("late-sql-first", new BigDecimal("10.00"));
        var second = fixture.createProduct("late-sql-second", new BigDecimal("7.50"));
        assertCommittedFixture(account.id(), first, second);
        var baseline = snapshot();
        temporaryConstraint = "ck_t029_second_" + UUID.randomUUID().toString().replace("-", "");
        // Separate autocommit JDBC connection: installed before the order transaction starts.
        independent.execute("alter table tb_order_item add constraint " + temporaryConstraint
                + " check (product_id <> " + second + ")");
        assertThat(constraintExists()).isTrue();
        assertThat(snapshot()).isEqualTo(baseline);
        probe.arm(account.id(), temporaryConstraint);

        var outcome = perform(account.token(), body(first, 2, second, 3));
        var after = snapshot(); // No cleanup/DDL between the request/proxy exit and this read.
        var steps = probe.steps();
        printObservation("LATE_SQL", outcome, baseline, after, steps);

        assertAll(
                () -> {
                    assertThat(steps).hasSize(3);
                    var order = steps.get(0);
                    assertThat(order.table()).isEqualTo("tb_order");
                    assertThat(order.completed()).isTrue();
                    assertThat(order.affected()).isEqualTo(1);
                    assertThat(order.view().orders()).hasSize(1);
                    assertThat(order.view().items()).isEmpty();
                    var item = steps.get(1);
                    assertThat(item.table()).isEqualTo("tb_order_item");
                    assertThat(item.productId()).isEqualTo(first);
                    assertThat(item.completed()).isTrue();
                    assertThat(item.affected()).isEqualTo(1);
                    assertThat(item.view().orders()).hasSize(1);
                    assertThat(item.view().items()).containsExactly(new ItemRow(
                            item.view().items().get(0).id(), order.view().orders().get(0).id(), first, 0, 2,
                            new BigDecimal("10.00")));
                    var failure = steps.get(2);
                    assertThat(failure.productId()).isEqualTo(second);
                    assertThat(failure.completed()).isFalse();
                    assertThat(failure.sqlState()).isEqualTo("23513");
                    assertThat(failure.expectedConstraint()).isTrue();
                    assertThat(failure.view()).isEqualTo(item.view());
                    assertThat(steps).allMatch(step -> step.transactionActive() && !step.autoCommit());
                },
                () -> assertThat(after).as("independent read after proxy exit: complete baseline").isEqualTo(baseline),
                () -> assertThat(outcome.applicationFailure()).isInstanceOf(DataIntegrityViolationException.class),
                () -> assertThat(constraintExists()).as("constraint still installed during all checks").isTrue(),
                () -> assertError(outcome, 500, "Order registration could not be completed", account.token()));
    }

    @Test
    void shouldCommitCompleteOrderAndItemsVisibleAfterHttp201() throws Exception {
        var account = login(Set.of(UserRole.ADMIN));
        var first = fixture.createProduct("rollback-control-first", new BigDecimal("10.00"));
        var second = fixture.createProduct("rollback-control-second", new BigDecimal("7.50"));
        assertCommittedFixture(account.id(), first, second);
        var baseline = snapshot();
        probe.arm(account.id(), null);

        var response = assertConfirmation(account.token(), body(first, 2, second, 3), account.id(),
                List.of(new ExpectedLine(first, 2, "10.00", "20.00"),
                        new ExpectedLine(second, 3, "7.50", "22.50")), "42.50");
        var after = snapshot();
        var steps = probe.steps();
        System.out.printf("T029_CONTROL status=201 ordersDelta=%d itemsDelta=%d completedInserts=%d%n",
                after.orders().size() - baseline.orders().size(), after.items().size() - baseline.items().size(),
                steps.stream().filter(SqlStep::completed).count());
        assertThat(after.orders()).hasSize(baseline.orders().size() + 1);
        assertThat(after.items()).hasSize(baseline.items().size() + 2);
        assertThat(after.orders()).containsAll(baseline.orders());
        assertThat(after.items()).containsAll(baseline.items());
        var id = response.path("id").longValue();
        assertThat(after.orders()).contains(new OrderRow(id, account.id(),
                Instant.parse(response.path("createdAt").textValue()), "WAITING_PAYMENT"));
        var purchased = after.items().stream().filter(item -> item.orderId() == id).toList();
        assertThat(purchased).hasSize(2);
        assertThat(purchased).extracting(ItemRow::productId).containsExactly(first, second);
        assertThat(purchased).extracting(ItemRow::position).containsExactly(0, 1);
        assertThat(purchased).extracting(ItemRow::quantity).containsExactly(2, 3);
        assertThat(purchased).extracting(ItemRow::unitPrice)
                .containsExactly(new BigDecimal("10.00"), new BigDecimal("7.50"));
        assertThat(purchased).extracting(ItemRow::subtotal)
                .containsExactly(new BigDecimal("20.00"), new BigDecimal("22.50"));
        assertThat(purchased.stream().map(ItemRow::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualTo(new BigDecimal("42.50"));
        assertThat(steps).hasSize(3).allMatch(SqlStep::completed)
                .allMatch(step -> step.transactionActive() && !step.autoCommit());
        assertThat(steps.get(2).view().items()).hasSize(2);
    }

    @ParameterizedTest(name = "whole input rejection {0}")
    @EnumSource(Rejection.class)
    void shouldRejectTheWholeInputBeforeAnyInsert(Rejection rejection) throws Exception {
        var account = login(Set.of(UserRole.CLIENT));
        var first = fixture.createProduct("rejection-first", new BigDecimal("10.00"));
        var middle = fixture.createProduct("rejection-middle", new BigDecimal("7.50"));
        var last = fixture.createProduct("rejection-last", new BigDecimal("2.00"));
        if (rejection == Rejection.ZERO_PRICE || rejection == Rejection.NEGATIVE_PRICE) {
            fixture.setProductPrice(middle, new BigDecimal(rejection == Rejection.ZERO_PRICE ? "0.00" : "-0.01"));
        }
        assertCommittedFixture(account.id(), first, middle, last);
        if (rejection == Rejection.ZERO_PRICE || rejection == Rejection.NEGATIVE_PRICE) {
            assertThat(independent.queryForObject("select price from tb_product where id = ?", BigDecimal.class, middle))
                    .isEqualTo(new BigDecimal(rejection == Rejection.ZERO_PRICE ? "0.00" : "-0.01"));
        }
        var middleId = rejection == Rejection.MISSING_PRODUCT ? Long.MAX_VALUE : middle;
        if (rejection == Rejection.MISSING_PRODUCT) {
            assertThat(independent.queryForObject("select count(*) from tb_product where id = ?", Long.class, middleId)).isZero();
        }
        var lines = rejection == Rejection.DUPLICATE_PRODUCT
                ? "{\"items\":[" + line(first, 2) + "," + line(middle, 3) + "," + line(first, 4)
                    + "," + line(last, 1) + "]}"
                : "{\"items\":[" + line(first, 2) + "," + line(middleId, rejection == Rejection.ZERO_QUANTITY ? 0 : 1)
                    + "," + line(last, 3) + "]}";
        var baseline = snapshot();
        probe.arm(account.id(), null);

        var outcome = perform(account.token(), lines);
        var after = snapshot();
        var steps = probe.steps();
        printObservation(rejection.name(), outcome, baseline, after, steps);
        assertAll(
                () -> assertThat(steps).as("business/input rejection before any order SQL").isEmpty(),
                () -> assertThat(after).as("no new rows by independent connection before cleanup").isEqualTo(baseline),
                () -> assertError(outcome, rejection.status, rejection.message, account.token()));
    }

    private Outcome perform(String token, String input) throws Exception {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try {
            var result = mvc.perform(post("/orders").header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).content(input)).andReturn();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            // Preserve the real exception even after the future MVC advice resolves it to HTTP 500.
            return new Outcome(result, result.getResolvedException());
        } catch (jakarta.servlet.ServletException failure) {
            // Known missing MVC adapters are behavioral RED, not fixture failures; never fabricate a response.
            Throwable cause = failure.getCause();
            if (!(cause instanceof DataIntegrityViolationException)
                    && !(cause instanceof IllegalArgumentException)
                    && !(cause instanceof PostFlushTestFailure)
                    && !(cause instanceof BeforeCommitTestFailure)) throw failure;
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new Outcome(null, cause);
        }
    }

    private void assertError(Outcome outcome, int status, String message, String token) throws Exception {
        assertThat(outcome.result()).as("failure must reach MVC as an HTTP error response").isNotNull();
        var response = outcome.result().getResponse();
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", HttpStatus.valueOf(status).getReasonPhrase()).put("message", message).put("path", "/orders");
        assertAll(
                () -> assertThat(response.getStatus()).isEqualTo(status).isNotEqualTo(201),
                () -> {
                    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
                    assertThat(mapper.readTree(response.getContentAsString())).isEqualTo(expected);
                },
                () -> assertThat(response.getHeader("Location")).isNull(),
                () -> assertThat(response.getContentAsString().contains(token)).as("token must not be exposed").isFalse());
        // Exact four-field envelope and fixed message exclude input/hash/SQL/stack trace/internal diagnostics.
    }

    private void assertCommittedFixture(long account, long... products) {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(independent.queryForObject("select count(*) from tb_user_account where id = ?", Long.class, account)).isEqualTo(1);
        assertThat(independent.queryForObject("select count(*) from tb_user_account_role where user_account_id = ?",
                Long.class, account)).isPositive();
        for (long product : products) {
            assertThat(independent.queryForObject("select count(*) from tb_product where id = ?", Long.class, product)).isEqualTo(1);
        }
    }

    private DatabaseState snapshot() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        return independent.execute((ConnectionCallback<DatabaseState>) connection -> readState(connection, null));
    }

    private boolean constraintExists() {
        return independent.queryForObject("select count(*) from information_schema.table_constraints "
                        + "where constraint_schema = 'PUBLIC' and constraint_name = ?", Long.class,
                temporaryConstraint.toUpperCase(Locale.ROOT)) == 1;
    }

    private static void printObservation(String label, Outcome outcome, DatabaseState baseline,
                                         DatabaseState after, List<SqlStep> steps) {
        System.out.printf("T029_RESULT case=%s http=%s escaped=%s baselineOrders=%d baselineItems=%d afterOrders=%d afterItems=%d baselineRestored=%s completedInserts=%d failedInserts=%d%n",
                label, outcome.result() == null ? "NONE" : outcome.result().getResponse().getStatus(),
                outcome.result() != null || outcome.applicationFailure() == null
                        ? "NONE" : outcome.applicationFailure().getClass().getSimpleName(),
                baseline.orders().size(), baseline.items().size(), after.orders().size(), after.items().size(),
                after.equals(baseline), steps.stream().filter(SqlStep::completed).count(),
                steps.stream().filter(step -> !step.completed()).count());
        for (var step : steps) {
            System.out.printf("T029_SQL case=%s table=%s productId=%s completed=%s affected=%d tx=%s autoCommit=%s visibleOrders=%d visibleItems=%d sqlState=%s expectedConstraint=%s%n",
                    label, step.table(), step.productId(), step.completed(), step.affected(), step.transactionActive(),
                    step.autoCommit(), step.view().orders().size(), step.view().items().size(), step.sqlState(), step.expectedConstraint());
        }
    }

    private static String body(long first, int firstQuantity, long second, int secondQuantity) {
        return "{\"items\":[" + line(first, firstQuantity) + "," + line(second, secondQuantity) + "]}";
    }

    private static String line(long product, int quantity) {
        return "{\"productId\":" + product + ",\"quantity\":" + quantity + "}";
    }

    private static DatabaseState readState(Connection connection, Long buyer) throws SQLException {
        var orders = new ArrayList<OrderRow>();
        var items = new ArrayList<ItemRow>();
        var filter = buyer == null ? "" : " where buyer_id = ?";
        try (var statement = connection.prepareStatement("select id, buyer_id, created_at, status from tb_order" + filter + " order by id")) {
            if (buyer != null) statement.setLong(1, buyer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) orders.add(new OrderRow(rows.getLong("id"), rows.getLong("buyer_id"),
                        rows.getObject("created_at", OffsetDateTime.class).toInstant(), rows.getString("status")));
            }
        }
        var itemFilter = buyer == null ? "" : " where order_id in (select id from tb_order where buyer_id = ?)";
        try (var statement = connection.prepareStatement("select id, order_id, product_id, position, quantity, unit_price "
                + "from tb_order_item" + itemFilter + " order by order_id, position")) {
            if (buyer != null) statement.setLong(1, buyer);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) items.add(new ItemRow(rows.getLong("id"), rows.getLong("order_id"),
                        rows.getLong("product_id"), rows.getInt("position"), rows.getInt("quantity"), rows.getBigDecimal("unit_price")));
            }
        }
        return new DatabaseState(List.copyOf(orders), List.copyOf(items));
    }

    record Outcome(MvcResult result, Throwable applicationFailure) { }
    record OrderRow(long id, long buyerId, Instant createdAt, String status) { }
    record ItemRow(long id, long orderId, long productId, int position, int quantity, BigDecimal unitPrice) {
        BigDecimal subtotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
    }
    record DatabaseState(List<OrderRow> orders, List<ItemRow> items) { }
    record SqlStep(String table, Long productId, boolean completed, long affected, boolean transactionActive,
                   boolean autoCommit, DatabaseState view, String sqlState, boolean expectedConstraint) { }

    enum Rejection {
        MISSING_PRODUCT(404, "Product not found"),
        DUPLICATE_PRODUCT(422, "Duplicate product in order"),
        ZERO_PRICE(422, "Product price is not valid for order"),
        NEGATIVE_PRICE(422, "Product price is not valid for order"),
        ZERO_QUANTITY(400, "Invalid order data");
        final int status;
        final String message;
        Rejection(int status, String message) { this.status = status; this.message = message; }
    }

    /** T031 only: observe body return inside transaction advice and inject failure during its completion. */
    static class CompletionProbe {
        private final Advised serviceProxy;
        private final JdbcTemplate transactionJdbc;
        private final long buyer;
        private final List<String> events = new ArrayList<>();
        private final BeforeCommitTestFailure failure = new BeforeCommitTestFailure();
        private OrderResponseDTO dto;
        private OrderResponseDTO callbackDto;
        private boolean bodyTransactionActive;
        private boolean callbackBeforeBody;
        private boolean beforeCommitReached;
        private boolean callbackTransactionActive;
        private boolean interceptorCompletion;
        private boolean boundConnection;
        private boolean autoCommit;
        private boolean readOnly;
        private boolean exceptionThrown;
        private boolean proxyEntered;
        private boolean proxyReturned;
        private boolean innerInstalled;
        private boolean outerInstalled;
        private DatabaseState view = new DatabaseState(List.of(), List.of());

        private final MethodInterceptor bodyObserver = invocation -> {
            if (!invocation.getMethod().getName().equals("register")) return invocation.proceed();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { observeBeforeCommit(readOnly); }
            });
            events.add("SYNC_REGISTERED");
            // This proceed reaches the actual service method, without completing the outer transaction advice.
            dto = (OrderResponseDTO) invocation.proceed();
            bodyTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            callbackBeforeBody = beforeCommitReached;
            events.add("DTO_PRODUCED");
            return dto;
        };

        private final MethodInterceptor proxyReturnObserver = invocation -> {
            if (!invocation.getMethod().getName().equals("register")) return invocation.proceed();
            proxyEntered = true;
            // Includes the TransactionInterceptor: its successful return is required for caller-visible DTO return.
            var response = invocation.proceed();
            proxyReturned = true;
            events.add("PROXY_RETURNED");
            return response;
        };

        CompletionProbe(Advised serviceProxy, JdbcTemplate transactionJdbc, long buyer) {
            this.serviceProxy = serviceProxy;
            this.transactionJdbc = transactionJdbc;
            this.buyer = buyer;
        }

        void install() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(serviceProxy.isFrozen()).isFalse();
            var advisors = serviceProxy.getAdvisors();
            var positions = IntStream.range(0, advisors.length)
                    .filter(index -> advisors[index].getAdvice() instanceof TransactionInterceptor).toArray();
            assertThat(positions).as("one actual Spring transaction interceptor").hasSize(1);
            int transactionIndex = positions[0];
            serviceProxy.addAdvice(transactionIndex + 1, bodyObserver);
            innerInstalled = true;
            serviceProxy.addAdvice(transactionIndex, proxyReturnObserver);
            outerInstalled = true;
            assertThat(serviceProxy.getAdvisors()).extracting(advisor -> advisor.getAdvice())
                    .containsSubsequence(proxyReturnObserver, advisors[transactionIndex].getAdvice(), bodyObserver);
        }

        void observeBeforeCommit(boolean readOnly) {
            beforeCommitReached = true;
            this.readOnly = readOnly;
            callbackDto = dto;
            callbackTransactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            interceptorCompletion = StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals("org.springframework.transaction.interceptor.TransactionAspectSupport")
                            && frame.getMethodName().equals("commitTransactionAfterReturning")));
            events.add("BEFORE_COMMIT");
            view = transactionJdbc.execute((ConnectionCallback<DatabaseState>) connection -> {
                var transactionConnection = DataSourceUtils.getTargetConnection(connection);
                boundConnection = DataSourceUtils.isConnectionTransactional(transactionConnection, transactionJdbc.getDataSource());
                autoCommit = transactionConnection.getAutoCommit();
                return readState(transactionConnection, buyer);
            });
            // Failed observation is a fixture/instrumentation error, not the intended callback exception.
            assertThat(dto).isNotNull();
            assertThat(bodyTransactionActive).isTrue();
            assertThat(proxyReturned).isFalse();
            assertThat(interceptorCompletion).isTrue();
            assertThat(callbackTransactionActive).isTrue();
            assertThat(boundConnection).isTrue();
            assertThat(autoCommit).isFalse();
            assertThat(view.orders()).hasSize(1);
            assertThat(view.orders().getFirst().id()).isEqualTo(dto.id());
            assertThat(view.items()).hasSize(2);
            events.add("ROWS_OBSERVED");
            exceptionThrown = true;
            events.add("CALLBACK_EXCEPTION");
            throw failure;
        }

        void uninstall() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (innerInstalled) assertThat(serviceProxy.removeAdvice(bodyObserver)).isTrue();
            if (outerInstalled) assertThat(serviceProxy.removeAdvice(proxyReturnObserver)).isTrue();
            innerInstalled = false;
            outerInstalled = false;
        }
    }

    static class BeforeCommitTestFailure extends RuntimeException {
        BeforeCommitTestFailure() { super("T031 test-only beforeCommit failure after body DTO production"); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RepositorySpyConfig {
        @Bean static PostFlushProbe postFlushProbe() { return new PostFlushProbe(); }

        @Bean
        static BeanPostProcessor spyRealOrderRepository(PostFlushProbe observation) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof OrderRepository realRepository)) return bean;
                    observation.realRepository = realRepository;
                    // A forwarding view without Advised avoids Mockito's Spring proxy unwrapping.
                    // Every method still invokes the original Spring Data proxy and real JPA implementation.
                    var forwardingRepository = (OrderRepository) Proxy.newProxyInstance(
                            OrderRepository.class.getClassLoader(), new Class<?>[]{OrderRepository.class},
                            (proxy, method, args) -> {
                                var result = ObservedDataSource.invoke(realRepository, method, args);
                                if (method.getName().equals("saveAndFlush")) observation.afterRealFlush((Order) result);
                                return result;
                            });
                    // No save method is stubbed; the spy calls the forwarding implementation above.
                    return spy(forwardingRepository);
                }
            };
        }
    }

    /** Armed only by T030; the real flush returns before this processing fault is raised. */
    static class PostFlushProbe {
        private OrderRepository realRepository;
        private Long buyer;
        private JdbcTemplate transactionJdbc;
        private Long savedId;
        private boolean flushReturned;
        private boolean transactionActive;
        private boolean boundConnection;
        private boolean autoCommit;
        private boolean exceptionThrown;
        private DatabaseState view = new DatabaseState(List.of(), List.of());
        private final PostFlushTestFailure failure = new PostFlushTestFailure();

        void arm(long buyer, JdbcTemplate transactionJdbc) {
            this.buyer = buyer;
            this.transactionJdbc = transactionJdbc;
            flushReturned = false;
            exceptionThrown = false;
        }
        void disarm() { buyer = null; transactionJdbc = null; }

        void afterRealFlush(Order saved) {
            if (buyer == null) return;
            flushReturned = true;
            savedId = saved.getId();
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            view = transactionJdbc.execute((ConnectionCallback<DatabaseState>) connection -> {
                // JdbcTemplate supplies a close-suppressing ConnectionProxy; inspect its bound target.
                var transactionConnection = DataSourceUtils.getTargetConnection(connection);
                boundConnection = DataSourceUtils.isConnectionTransactional(transactionConnection, transactionJdbc.getDataSource());
                autoCommit = transactionConnection.getAutoCommit();
                return readState(transactionConnection, buyer);
            });
            // Instrumentation failures remain errors; never mislabel them as the intended processing fault.
            assertThat(transactionActive).isTrue();
            assertThat(boundConnection).isTrue();
            assertThat(autoCommit).isFalse();
            assertThat(view.orders()).hasSize(1);
            assertThat(view.orders().getFirst().id()).isEqualTo(savedId);
            assertThat(view.items()).hasSize(2);
            exceptionThrown = true;
            // OrderService.register is still waiting for its repository call: no DTO return or commit yet.
            throw failure;
        }
    }

    static class PostFlushTestFailure extends RuntimeException {
        PostFlushTestFailure() { super("T030 test-only processing failure after real flush"); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlObservationConfig {
        @Bean static SqlInsertProbe sqlInsertProbe() { return new SqlInsertProbe(); }

        @Bean
        static BeanPostProcessor observeRealDataSource(SqlInsertProbe probe) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    return bean instanceof DataSource source && !(source instanceof ObservedDataSource)
                            ? new ObservedDataSource(source, probe) : bean;
                }
            };
        }
    }

    /** Records only completed real JDBC calls or the actual database exception, and never injects errors. */
    static class SqlInsertProbe {
        private Long buyer;
        private String constraint;
        private final List<SqlStep> steps = new ArrayList<>();
        void arm(long buyer, String constraint) { this.buyer = buyer; this.constraint = constraint; steps.clear(); }
        void clear() { buyer = null; constraint = null; steps.clear(); }
        List<SqlStep> steps() { return List.copyOf(steps); }

        void record(Connection connection, String table, Long product, long affected, SQLException failure) throws SQLException {
            if (buyer == null) return;
            var view = readState(connection, buyer);
            steps.add(new SqlStep(table, product, failure == null, affected,
                    TransactionSynchronizationManager.isActualTransactionActive(), connection.getAutoCommit(), view,
                    failure == null ? "NONE" : failure.getSQLState(), failure != null && constraint != null
                    && failure.getMessage().toLowerCase(Locale.ROOT).contains(constraint.toLowerCase(Locale.ROOT))));
        }
    }

    static class ObservedDataSource extends DelegatingDataSource {
        private final SqlInsertProbe probe;
        ObservedDataSource(DataSource delegate, SqlInsertProbe probe) { super(delegate); this.probe = probe; }
        @Override public Connection getConnection() throws SQLException { return observed(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return observed(super.getConnection(user, password));
        }

        private Connection observed(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("unwrap") && args[0] == Connection.class) return proxy;
                        var result = invoke(target, method, args);
                        if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && result instanceof PreparedStatement statement) {
                            var normalized = sql.toLowerCase(Locale.ROOT).stripLeading();
                            var table = normalized.startsWith("insert into tb_order_item ") ? "tb_order_item"
                                    : normalized.startsWith("insert into tb_order ") ? "tb_order" : null;
                            if (table != null) return observedStatement(target, statement, table, normalized);
                        }
                        return result;
                    });
        }

        private PreparedStatement observedStatement(Connection connection, PreparedStatement target, String table, String sql) {
            var bindings = new HashMap<Integer, Object>();
            var columns = sql.substring(sql.indexOf('(') + 1, sql.indexOf(')')).split(",");
            int found = 0;
            for (int i = 0; i < columns.length; i++) if (columns[i].strip().equals("product_id")) found = i + 1;
            final int productIndex = found;
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        if (method.getName().startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                            bindings.put(index, args[1]);
                        }
                        var execution = Set.of("executeUpdate", "executeLargeUpdate", "execute").contains(method.getName());
                        Long product = bindings.get(productIndex) instanceof Number value ? value.longValue() : null;
                        Object result;
                        try {
                            result = invoke(target, method, args);
                        } catch (SQLException failure) {
                            if (execution) probe.record(connection, table, product, 0, failure);
                            throw failure;
                        }
                        // The driver has returned success: this is not a before-execute callback or printed SQL.
                        if (execution) probe.record(connection, table, product,
                                result instanceof Number value ? value.longValue() : target.getUpdateCount(), null);
                        return result;
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
    }
}
