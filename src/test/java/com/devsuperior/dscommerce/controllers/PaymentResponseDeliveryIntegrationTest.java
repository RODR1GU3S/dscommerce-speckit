package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.FailureProbe;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.Point;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.Run;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.State;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.dto.PaymentConfirmationDTO;
import com.devsuperior.dscommerce.entities.OrderStatus;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T025 authorship: application/filter simulation only, with real service, transaction and independent JDBC. */
@RootHttpContext
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@Import({PaymentFailureInjectionTestConfig.class, PaymentResponseDeliveryIntegrationTest.BufferConfig.class})
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class PaymentResponseDeliveryIntegrationTest extends PaymentResponseRecoverySupport {
    @Autowired private MockMvc mvc;
    @Autowired private DeliveryBuffer buffer;

    @ParameterizedTest(name = "MockMvc buffered response {0}")
    @EnumSource(DeliveryMode.class)
    void bufferedConfirmationIsDeliveredOrPreventedOnlyAfterIndependentCommitProof(DeliveryMode mode) throws Exception {
        var before = database.snapshot();
        try (var run = unarmed(); var scope = buffer.open(run, mode, (body, state) -> {
            assertConfirmation(body);
            assertCommittedSettlement(before, state);
        })) {
            var request = post(paymentPath()).header("Authorization", "Bearer " + payerToken)
                    .header(PaymentFailureInjectionTestConfig.HEADER, run.headerValue())
                    .contentType("application/json").accept("application/json").content(paymentBody());
            if (mode == DeliveryMode.WITHHOLD) {
                Throwable escaped = null;
                try { mvc.perform(request).andReturn(); }
                catch (Exception failure) { escaped = failure; }
                assertThat(causes(escaped)).contains(scope.failure);
            } else {
                var result = mvc.perform(request).andReturn();
                assertThat(result.getResponse().getStatus()).isEqualTo(201);
                assertConfirmation(result.getResponse().getContentAsByteArray());
            }
            run.requireOutsideTransaction();
            scope.verify();
            assertThat(scope.generatedBytes).isPositive();
            assertThat(scope.downstream).isNotNull();
            var delivered = scope.downstream.getContentAsByteArray();
            assertThat(delivered.length).isEqualTo(mode == DeliveryMode.WITHHOLD ? 0 : scope.generatedBytes);
            if (mode == DeliveryMode.FORWARD) { assertConfirmation(delivered); }
            assertThat(scope.events).containsExactly("APPLICATION_RETURNED", "JDBC_COMMIT_CONFIRMED",
                    mode == DeliveryMode.WITHHOLD ? "BUFFER_DELIVERY_FAILED" : "BUFFER_COPIED");
            assertThat(scope.applicationReturnedAt).isLessThanOrEqualTo(scope.commitObservedAt);
            assertThat(scope.commitObservedAt).isLessThanOrEqualTo(scope.deliveryFinishedAt);
            assertCommittedSettlement(before, run.state());
            assertRecoveryWithoutReusingSuccess();
            System.out.println("T025_BUFFER context=ROOT mode=" + mode + " generatedBytes=" + scope.generatedBytes
                    + " deliveredMockBytes=" + delivered.length + " events=" + scope.events
                    + " applicationReturnedAtNanos=" + scope.applicationReturnedAt + " commitObservedAtNanos=" + scope.commitObservedAt
                    + " deliveryFinishedAtNanos=" + scope.deliveryFinishedAt
                    + " elapsedNanos=" + scope.elapsedNanos + " commit=1 repeatedPost=409 ownerGet=PAID");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BufferConfig {
        @Bean DeliveryBuffer paymentDeliveryBuffer() { return new DeliveryBuffer(); }
        @Bean FilterRegistrationBean<OncePerRequestFilter> paymentDeliveryFilter(DeliveryBuffer buffer) {
            var filter = new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                          FilterChain chain) throws ServletException, IOException {
                    var id = request.getHeader(PaymentFailureInjectionTestConfig.HEADER);
                    var scope = id == null ? null : buffer.scopes.get(id);
                    if (scope == null) { chain.doFilter(request, response); return; }
                    var started = System.nanoTime();
                    var cached = new ContentCachingResponseWrapper(response);
                    try {
                        assertThat(++scope.hits).isEqualTo(1);
                        assertThat(request.getMethod()).isEqualTo("POST");
                        chain.doFilter(request, cached);
                        scope.applicationReturnedAt = System.nanoTime();
                        scope.events.add("APPLICATION_RETURNED");
                        scope.downstream = WebUtils.getNativeResponse(response, MockHttpServletResponse.class);
                        assertThat(scope.downstream).as("Marked delivery scope is MockMvc only").isNotNull();
                        assertThat(response.isCommitted()).isFalse();
                        assertThat(scope.downstream.getContentAsByteArray()).isEmpty();
                        assertThat(cached.getStatus()).isEqualTo(201);
                        assertThat(cached.getHeader("Location")).isNull();
                        assertThat(cached.getHeader("WWW-Authenticate")).isNull();
                        assertThat(cached.getContentType()).contains("application/json");
                        var body = cached.getContentAsByteArray();
                        scope.generatedBytes = body.length;
                        assertThat(body).isNotEmpty();
                        var state = scope.run.stateAfterProxy();
                        scope.proof.verify(body, state); // New physical JDBC connection, after original proxy exit.
                        scope.commitObservedAt = System.nanoTime();
                        scope.events.add("JDBC_COMMIT_CONFIRMED");
                        if (scope.mode == DeliveryMode.FORWARD) {
                            cached.copyBodyToResponse();
                            scope.deliveryFinishedAt = System.nanoTime();
                            scope.events.add("BUFFER_COPIED");
                        }
                    } catch (Throwable failure) {
                        scope.instrumentationFailure = failure;
                        throw new ServletException("T025 buffered-delivery preparation/oracle failed", failure);
                    } finally { scope.elapsedNanos = System.nanoTime() - started; }
                    if (scope.mode == DeliveryMode.WITHHOLD) {
                        // No copyBodyToResponse: the generated confirmation never reaches the MockMvc response body.
                        scope.events.add("BUFFER_DELIVERY_FAILED");
                        scope.deliveryFinishedAt = System.nanoTime();
                        scope.deliveryFailed = true;
                        throw scope.failure;
                    }
                }
            };
            var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
            // Inside T023's request scope, outside Security/MVC. No request without an owned scope is altered.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
            return registration;
        }
    }

    @FunctionalInterface interface CommitProof { void verify(byte[] body, State state) throws Exception; }

    static final class DeliveryBuffer implements DisposableBean {
        final ConcurrentHashMap<String, BufferScope> scopes = new ConcurrentHashMap<>();
        BufferScope open(Run run, DeliveryMode mode, CommitProof proof) {
            var scope = new BufferScope(this, run, mode, proof);
            assertThat(scopes.putIfAbsent(run.headerValue(), scope)).isNull();
            return scope;
        }
        @Override public void destroy() { assertThat(scopes).isEmpty(); }
    }

    static final class BufferScope implements AutoCloseable {
        final DeliveryBuffer owner;
        final Run run;
        final DeliveryMode mode;
        final CommitProof proof;
        final IOException failure = new IOException("T025 test-only delivery prevented after confirmed commit");
        final List<String> events = new ArrayList<>();
        int hits, generatedBytes;
        long elapsedNanos;
        long applicationReturnedAt, commitObservedAt, deliveryFinishedAt;
        boolean deliveryFailed;
        Throwable instrumentationFailure;
        MockHttpServletResponse downstream;
        BufferScope(DeliveryBuffer owner, Run run, DeliveryMode mode, CommitProof proof) {
            this.owner = owner; this.run = run; this.mode = mode; this.proof = proof;
        }
        void verify() {
            run.awaitFinished();
            assertThat(instrumentationFailure).as("500/escaped exception cannot replace a reached delivery point").isNull();
            assertThat(hits).isEqualTo(1);
            assertThat(events).contains("JDBC_COMMIT_CONFIRMED");
            assertThat(deliveryFailed).isEqualTo(mode == DeliveryMode.WITHHOLD);
        }
        @Override public void close() {
            try { verify(); }
            finally { owner.scopes.remove(run.headerValue(), this); }
        }
    }
}

/** No JUnit cases here. Only the two explicit T025 classes use this owned fixture and recovery oracle. */
abstract class PaymentResponseRecoverySupport extends PaymentRegistrationHttpTestSupport {
    enum DeliveryMode { FORWARD, WITHHOLD }
    protected static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    @Autowired protected FailureProbe failures;
    protected PaymentRegistrationDatabaseProbe database;
    protected OrderManifest target, canary;
    protected Instant occurrence;
    protected String payerToken;
    private String ownerToken, noRoleToken;
    private PaymentRegistrationTestFixture fixture;
    private Snapshot cleanupBaseline;

    @Override protected final String expectedContextPath() { return ""; }

    @BeforeEach
    void prepareOwnedRowsAndRealCredentialsBeforeAnyDeliveryScope() {
        cleanupBaseline = null;
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot();
        var accounts = fixture.prepareAccessAccounts();
        var owner = fixture.prepareBuyer();
        var a = fixture.createProduct("T025 current A", new BigDecimal("12.00"));
        var b = fixture.createProduct("T025 current B", new BigDecimal("99.00"));
        payerToken = realHttpLogin(accounts.admin().id(), accounts.admin().name(), accounts.admin().password());
        ownerToken = realHttpLogin(owner.id(), owner.name(), owner.password());
        noRoleToken = realHttpLogin(accounts.none().id(), accounts.none().name(), accounts.none().password());
        var reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        occurrence = reference.minusSeconds(3600);
        target = fixture.createOrder(owner, reference.minusSeconds(7200), List.of(
                new ItemInput(a.id(), 2, "10.00", "20.00"), new ItemInput(b.id(), 3, "7.50", "22.50")), "42.50");
        canary = fixture.createOrder(accounts.client(), reference.minusSeconds(10800),
                List.of(new ItemInput(a.id(), 2, "10.00", "20.00")), "20.00");
        requirePaymentServiceProxy();
    }

    @AfterEach
    void removeOnlyOwnedRowsAfterClosedDeliveryResources() {
        try { if (fixture != null) { fixture.close(); } }
        finally {
            fixture = null; payerToken = null; ownerToken = null; noRoleToken = null;
            if (cleanupBaseline != null) { PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot()); }
        }
    }

    protected final Run unarmed() {
        return failures.open(target.id(), Point.NONE, (point, connection, state) -> {
            throw new AssertionError("A post-commit delivery scenario must not inject a transactional fault");
        });
    }
    protected final String paymentPath() { return "/admin/orders/" + target.id() + "/payments"; }
    protected final byte[] paymentBody() throws IOException {
        return mapper.writeValueAsBytes(mapper.createObjectNode().put("occurredAt", UTC.format(occurrence)));
    }

    protected final void assertConfirmation(byte[] body) throws IOException {
        var expected = mapper.createObjectNode().put("orderId", target.id())
                .put("occurredAt", UTC.format(occurrence)).put("status", "PAID");
        var actual = mapper.readTree(body);
        assertThat(actual).isEqualTo(mapper.readTree(expected.toString())); // Exact integers; normalize node representation only.
        assertThat(actual.get("orderId").isIntegralNumber()).isTrue();
        assertThat(actual.get("orderId").canConvertToLong()).isTrue();
        assertThat(actual.get("orderId").longValue()).isEqualTo(target.id());
    }

    protected final void assertCommittedSettlement(Snapshot before, State state) throws SQLException {
        assertNoExternalTransaction();
        assertThat(state.instrumentationFailure()).isNull();
        assertThat(state.point()).isEqualTo(Point.NONE);
        assertThat(state.hits()).isZero();
        assertThat(state.injected()).isFalse();
        assertThat(state.proxyEntries()).isEqualTo(1);
        assertThat(state.proxyReturns()).isEqualTo(1);
        assertThat(state.proxyFailures()).isZero();
        assertThat(state.proxyFailure()).isNull();
        assertThat(state.outsideAtEntry()).isTrue();
        assertThat(state.outsideAtExit()).isTrue();
        assertThat(state.newTransaction()).isTrue();
        assertThat(state.boundConnection()).isTrue();
        assertThat(state.autoCommitOff()).isTrue();
        assertThat(state.isolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        assertThat(state.flushReturns()).isEqualTo(1);
        assertThat(state.bodyReturns()).isEqualTo(1);
        assertThat(state.bodyDto()).isEqualTo(new PaymentConfirmationDTO(target.id(), occurrence, OrderStatus.PAID));
        assertThat(state.beforeCommits()).isEqualTo(1);
        assertThat(state.commitAttempts()).isEqualTo(1);
        assertThat(state.delegatedCommits()).isEqualTo(1);
        assertThat(state.successfulCommits()).isEqualTo(1);
        assertThat(state.afterCommits()).isEqualTo(1);
        assertThat(state.rollbackAttempts()).isZero();
        assertThat(state.completion()).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(state.failedDml()).isEmpty();
        assertThat(state.dml()).hasSize(2).allSatisfy(dml -> assertThat(dml.affectedRows()).isEqualTo(1));
        assertThat(state.dml().stream().filter(dml -> dml.sql().startsWith("insert into tb_payment "))).hasSize(1);
        assertThat(state.dml().stream().filter(dml -> dml.sql().startsWith("update tb_order "))).hasSize(1);
        assertThat(state.events()).containsSubsequence("JDBC_COMMIT_SUCCEEDED", "AFTER_COMMIT",
                "AFTER_COMPLETION:" + TransactionSynchronization.STATUS_COMMITTED, "PROXY_RETURNED", "PROXY_EXITED");
        try (var connection = openIndependentConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(state.databaseUrl());
            try (var query = connection.prepareStatement("select session_id()")) {
                query.setQueryTimeout(2);
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getInt(1)).isNotEqualTo(state.sessionId());
                    assertThat(rows.next()).isFalse();
                }
            }
            try (var query = connection.prepareStatement("select o.status, p.occurred_at from tb_order o "
                    + "join tb_payment p on p.order_id=o.id where o.id=?")) {
                query.setQueryTimeout(2); query.setLong(1, target.id());
                try (var rows = query.executeQuery()) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("PAID");
                    assertThat(rows.getObject(2, OffsetDateTime.class).toInstant()).isEqualTo(occurrence);
                    assertThat(rows.next()).isFalse();
                }
            }
        }
        var after = database.snapshot();
        assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(before.payments().stream(),
                Stream.of(new PaymentRow(target.id(), occurrence))).toList());
        assertThat(after.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                .map(row -> row.id() == target.id() ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
    }

    /** Narrow US4 recovery observation, not the US5 pagination/regression checkpoint. HEAD has no business body. */
    protected final void assertRecoveryWithoutReusingSuccess() throws Exception {
        var committed = database.snapshot();
        try (var repeat = unarmed()) {
            var response = send(jsonRequest(paymentPath()).header("Authorization", "Bearer " + payerToken)
                    .header(PaymentFailureInjectionTestConfig.HEADER, repeat.headerValue())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(paymentBody())).build());
            repeat.requireOutsideTransaction();
            assertThat(response.statusCode()).isEqualTo(409);
            var expected = mapper.createObjectNode().put("status", 409).put("error", "Conflict")
                    .put("message", "Payment already registered").put("path", paymentPath());
            assertThat(mapper.readTree(response.body())).isEqualTo(expected);
            assertThat(response.headers().allValues("Location")).isEmpty();
            assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
            assertThat(repeat.state().dml()).isEmpty();
            assertThat(repeat.state().failedDml()).isEmpty();
            assertThat(repeat.state().flushReturns()).isZero();
            assertThat(repeat.state().bodyReturns()).isZero();
            assertThat(repeat.state().successfulCommits()).isZero();
            assertThat(repeat.state().completion()).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        PaymentRegistrationDatabaseProbe.assertUnchanged(committed, database.snapshot());
        var path = "/orders/" + target.id();
        var response = send(jsonRequest(path).header("Authorization", "Bearer " + ownerToken).GET().build());
        assertThat(response.statusCode()).isEqualTo(200);
        var expected = expectedPaidDetail();
        assertThat(mapper.readTree(response.body())).isEqualTo(mapper.readTree(expected.toString()));
        assertHead(path, ownerToken, 200);
        assertHead(path, null, 401);
        assertHead(path, noRoleToken, 403);
        assertHead(path, payerToken, 404); // ADMIN who paid a foreign purchase still cannot read it.
        var foreign = send(jsonRequest(path).header("Authorization", "Bearer " + payerToken).GET().build());
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(foreign.body())).isEqualTo(mapper.createObjectNode().put("status", 404)
                .put("error", "Not Found").put("message", "Order not found").put("path", path));
        PaymentRegistrationDatabaseProbe.assertUnchanged(committed, database.snapshot());
    }

    private ObjectNode expectedPaidDetail() {
        var expected = mapper.createObjectNode().put("id", target.id()).put("createdAt", UTC.format(target.createdAt()))
                .put("status", "PAID").put("total", target.total());
        var items = expected.putArray("items");
        for (var item : target.items()) {
            items.addObject().put("productId", item.productId()).put("productName", item.productName())
                    .put("quantity", item.quantity()).put("unitPrice", item.unitPrice()).put("subtotal", item.subtotal());
        }
        return expected;
    }
    private void assertHead(String path, String token, int status) {
        var request = jsonRequest(path).method("HEAD", HttpRequest.BodyPublishers.noBody());
        if (token != null) { request.header("Authorization", "Bearer " + token); }
        var response = send(request.build());
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.body()).isEmpty();
        if (status == 401) { assertThat(response.headers().allValues("WWW-Authenticate")).containsExactly("Bearer"); }
        else { assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty(); }
    }
    protected static List<Throwable> causes(Throwable failure) {
        var chain = new ArrayList<Throwable>();
        while (failure != null && chain.size() < 30 && !chain.contains(failure)) { chain.add(failure); failure = failure.getCause(); }
        return chain;
    }
}
