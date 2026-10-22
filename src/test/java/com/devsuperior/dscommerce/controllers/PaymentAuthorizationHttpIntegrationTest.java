package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.Phase;
import com.devsuperior.dscommerce.config.PaymentRegistrationObservationConfig.SqlScope;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.AccessAccounts;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RootHttpContext
@Import(PaymentRegistrationObservationConfig.class)
class PaymentAuthorizationHttpIntegrationTest extends PaymentAuthorizationScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T014 authorship: real HTTP/service/JPA scenarios, awaiting the planned S2 execution. */
abstract class PaymentAuthorizationScenarios extends PaymentRegistrationHttpTestSupport {
    private static final Duration WAIT = Duration.ofSeconds(10);
    private static final DateTimeFormatter UTC_MILLISECONDS = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static final String PRIVATE_DETAIL = "T014-private-lookup-detail";
    @Autowired private Observation observation;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private UserAccountRepository accountRepository;
    private PaymentRegistrationTestFixture fixture;
    private PaymentRegistrationDatabaseProbe database;
    private Snapshot cleanupBaseline;
    private AccessAccounts accounts;
    private Account foreignBuyer;
    private ProductData product;
    private Instant reference, createdAt, occurredAt;
    private final Map<Long, String> tokens = new HashMap<>();

    private enum Boundary { BEFORE_SERVICE, SERVICE_GUARD, AFTER_LOCK }

    @BeforeEach
    void prepareOwnedCommittedPurchasesAndRealCredentials() {
        cleanupBaseline = null;
        fixture = new PaymentRegistrationTestFixture(context);
        database = new PaymentRegistrationDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        cleanupBaseline = database.snapshot(); // Strict eight-table observation; never tolerate missing Payment.
        accounts = fixture.prepareAccessAccounts();
        foreignBuyer = fixture.prepareBuyer();
        product = fixture.createProduct("T014-current-catalog", new BigDecimal("12.00"));
        for (var account : List.of(accounts.admin(), accounts.both(), accounts.client(), accounts.none())) {
            tokens.put(account.id(), realHttpLogin(account.id(), account.name(), account.password()));
        }
        reference = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        clock.setInstant(reference);
        createdAt = reference.minusSeconds(7200);
        occurredAt = reference.minusSeconds(3600);
        fixture.createOrder(accounts.client(), createdAt.minusSeconds(60),
                List.of(new ItemInput(product.id(), 1, "8.00", "8.00")), "8.00");
        requirePaymentServiceProxy(); // Original proxy and transaction advisor, no replacement service.
    }

    @AfterEach
    void restoreOnlyFixtureOwnedRowsAfterAllMarkedRequestsFinish() {
        try {
            if (fixture != null) {
                fixture.close();
                if (cleanupBaseline != null) {
                    PaymentRegistrationDatabaseProbe.assertUnchanged(cleanupBaseline, database.snapshot());
                }
            }
        } finally {
            fixture = null;
            cleanupBaseline = null;
            tokens.clear();
        }
    }

    @ParameterizedTest(name = "US2.1 credential={0}")
    @ValueSource(strings = {"ABSENT", "MALFORMED", "BAD_SIGNATURE", "EXPIRED", "REMOVED_ACCOUNT"})
    void missingInvalidExpiredAndRemovedAccountCredentialsAre401WithoutRestrictedReads(String kind) throws Exception {
        var order = order(foreignBuyer);
        String credential = switch (kind) {
            case "ABSENT" -> null;
            case "MALFORMED" -> "invalid-t014-bearer";
            case "BAD_SIGNATURE" -> invalidSignature(token(accounts.admin()));
            case "EXPIRED" -> signed(JwtClaimsSet.builder()
                    .subject(Long.toString(accounts.admin().id())).issuedAt(reference.minusSeconds(7200))
                    .expiresAt(reference.minusSeconds(3600)).build());
            case "REMOVED_ACCOUNT" -> token(accounts.admin());
            default -> throw new IllegalArgumentException("Unknown credential case");
        };
        if (kind.equals("REMOVED_ACCOUNT")) {
            assertThat(decoder.decode(credential).getSubject()).isEqualTo(Long.toString(accounts.admin().id()));
            removeAccount(accounts.admin()); // Commit before taking the rejection baseline.
        } else if (!kind.equals("ABSENT")) {
            assertThatThrownBy(() -> decoder.decode(credential)).isInstanceOf(JwtException.class);
        }
        assertRejected(credential, path(order.id()), body(), 401, Boundary.BEFORE_SERVICE);
    }

    @ParameterizedTest(name = "US2.2 profile={0}, own={1}")
    @CsvSource({"CLIENT,true", "CLIENT,false", "NONE,true", "NONE,false"})
    void clientAndNoProfileCannotSettleEvenTheirOwnPurchase(String profile, boolean own) throws Exception {
        var actor = account(profile);
        var order = order(own ? actor : foreignBuyer);
        assertRejected(token(actor), path(order.id()), body(), 403, Boundary.BEFORE_SERVICE);
    }

    @ParameterizedTest(name = "ADMIN suffices profile={0}, own={1}")
    @CsvSource({"ADMIN,true", "ADMIN,false", "BOTH,true", "BOTH,false"})
    void currentAdminAloneOrWithClientSettlesOwnAndForeignOrders(String profile, boolean own) throws Exception {
        var actor = account(profile);
        assertAccepted(token(actor), order(own ? actor : foreignBuyer));
    }

    @ParameterizedTest(name = "signed misleading claims, current profile={0}")
    @ValueSource(strings = {"ADMIN", "BOTH", "CLIENT", "NONE"})
    void signedRoleNameAndAuthorityClaimsCannotGrantOrRemoveDatabasePermission(String profile) throws Exception {
        var actor = account(profile);
        var claims = profile.equals("ADMIN") || profile.equals("BOTH")
                ? List.of("ROLE_AUDITOR") : List.of("ADMIN", "CLIENT", "ROLE_ADMIN");
        var credential = withClaims(actor, claims);
        var decoded = decoder.decode(credential);
        assertThat(decoded.getSubject()).isEqualTo(Long.toString(actor.id()));
        assertThat(decoded.getClaimAsStringList("roles")).isEqualTo(claims);
        assertThat(decoded.getClaimAsString("name")).isEqualTo(foreignBuyer.name());
        var order = order(foreignBuyer);
        if (profile.equals("ADMIN") || profile.equals("BOTH")) {
            assertAccepted(credential, order);
        } else {
            assertRejected(credential, path(order.id()), body(), 403, Boundary.BEFORE_SERVICE);
        }
    }

    @ParameterizedTest(name = "US2.3 issued={0}, current={1}")
    @CsvSource({"ADMIN,CLIENT", "ADMIN,NONE", "BOTH,CLIENT", "BOTH,NONE"})
    void revocationAfterIssuanceDefeatsTheSameSignedAdminClaims(String initial, String current) throws Exception {
        var actor = account(initial);
        var credential = withClaims(actor, List.of("ADMIN", "CLIENT", "ROLE_ADMIN"));
        var order = order(actor);
        replaceRoles(actor, roles(current));
        assertThat(decoder.decode(credential).getClaimAsStringList("roles")).contains("ADMIN");
        assertRejected(credential, path(order.id()), body(), 403, Boundary.BEFORE_SERVICE);
    }

    @ParameterizedTest(name = "US2.3 issued={0}, granted={1}")
    @CsvSource({"CLIENT,ADMIN", "CLIENT,BOTH", "NONE,ADMIN", "NONE,BOTH"})
    void currentGrantAllowsTheSamePreviouslyDeniedCredentialWithoutReissuingIt(String initial, String granted)
            throws Exception {
        var actor = account(initial);
        var credential = withClaims(actor, initial.equals("CLIENT") ? List.of("CLIENT") : List.of());
        var order = order(actor);
        assertRejected(credential, path(order.id()), body(), 403, Boundary.BEFORE_SERVICE);
        replaceRoles(actor, roles(granted));
        assertThat(decoder.decode(credential).getClaimAsStringList("roles")).doesNotContain("ADMIN");
        assertAccepted(credential, order);
    }

    @ParameterizedTest(name = "after real filter: {0} to {1}")
    @CsvSource({"ADMIN,CLIENT", "ADMIN,NONE", "BOTH,CLIENT", "BOTH,NONE"})
    void revocationBetweenFilterAndServiceIsRereadBeforeAnyOrderLookup(String initial, String current) throws Exception {
        var actor = account(initial);
        var order = order(foreignBuyer);
        assertChangedAfterFilter(actor, order, () -> replaceRoles(actor, roles(current)), 403);
    }

    @Test
    void accountRemovedBetweenFilterAndServiceBecomes401WithoutExposingThePurchase() throws Exception {
        assertChangedAfterFilter(accounts.admin(), order(foreignBuyer), () -> removeAccount(accounts.admin()), 401);
    }

    @ParameterizedTest(name = "internal lookup point={0}")
    @ValueSource(strings = {"FILTER_ENTITY", "FILTER_SCALAR", "SERVICE_SCALAR", "POST_LOCK_SCALAR"})
    void controlledRepositoryLookupFailuresStay500RatherThan401OrBusinessConflicts(String point) throws Exception {
        var order = order(foreignBuyer);
        var scalarCalls = new AtomicInteger();
        var hits = new AtomicInteger();
        int failingScalar = switch (point) {
            case "FILTER_SCALAR" -> 1;
            case "SERVICE_SCALAR" -> 2;
            case "POST_LOCK_SCALAR" -> 3;
            default -> 0;
        };
        // Scope only this fixture account and one synchronous request. Delegate every other call unchanged.
        MethodInterceptor injection = invocation -> {
            if (invocation.getArguments().length == 1
                    && Long.valueOf(accounts.admin().id()).equals(invocation.getArguments()[0])) {
                String method = invocation.getMethod().getName();
                boolean fail = method.equals("findByIdWithRoles") && point.equals("FILTER_ENTITY");
                if (method.equals("findEffectiveAccountRolesById")) {
                    fail = scalarCalls.incrementAndGet() == failingScalar;
                }
                if (fail) {
                    hits.incrementAndGet();
                    throw new DataAccessResourceFailureException(PRIVATE_DETAIL);
                }
            }
            return invocation.proceed();
        };
        assertThat(accountRepository).isInstanceOf(Advised.class);
        var proxy = (Advised) accountRepository;
        proxy.addAdvice(0, injection);
        try {
            var boundary = point.equals("POST_LOCK_SCALAR") ? Boundary.AFTER_LOCK
                    : point.equals("SERVICE_SCALAR") ? Boundary.SERVICE_GUARD : Boundary.BEFORE_SERVICE;
            assertRejected(token(accounts.admin()), path(order.id()), body(), 500, boundary);
            assertThat(hits.get()).as("The intended repository failure must actually be reached once").isEqualTo(1);
            assertThat(scalarCalls.get()).isEqualTo(failingScalar);
        } finally {
            assertThat(proxy.removeAdvice(injection)).as("Remove only this scenario's local interceptor").isTrue();
        }
    }

    @ParameterizedTest(name = "security precedes MVC for {0}")
    @ValueSource(strings = {"MALFORMED_JSON", "MISSING_BODY", "EXTRA_FIELD", "QUERY", "NONPOSITIVE_ID", "OVERFLOW_ID"})
    void invalidInputsStillAuthenticateAndAuthorizeBeforeTheirLocal400(String kind) throws Exception {
        var order = order(foreignBuyer);
        var target = switch (kind) {
            case "NONPOSITIVE_ID" -> path(0);
            case "OVERFLOW_ID" -> "/admin/orders/9223372036854775808/payments";
            case "QUERY" -> path(order.id()) + "?private=T014-must-not-appear";
            default -> path(order.id());
        };
        var input = switch (kind) {
            case "MALFORMED_JSON" -> "{";
            case "MISSING_BODY" -> "";
            case "EXTRA_FIELD" -> body().replace("}", ",\"amount\":\"0.01\"}");
            default -> body();
        };
        // Positive control: a selected operation, genuinely invalid after ADMIN access; never a route 404.
        assertRejected(token(accounts.admin()), target, input, 400, Boundary.BEFORE_SERVICE);
        assertRejected(null, target, input, 401, Boundary.BEFORE_SERVICE);
        assertRejected(token(accounts.client()), target, input, 403, Boundary.BEFORE_SERVICE);
        assertRejected(token(accounts.none()), target, input, 403, Boundary.BEFORE_SERVICE);
    }

    private void assertChangedAfterFilter(Account actor, OrderManifest order, Runnable externalChange, int status)
            throws Exception {
        var beforeChange = database.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            try (var gate = attempt.pauseAt(Phase.PROXY_ENTRY)) {
                var request = request(attempt, token(actor), path(order.id()), body());
                var future = window.submit(() -> send(request));
                gate.awaitReached(); // The real security chain and MVC have already passed; no service tx yet.
                assertThat(attempt.clockCalls()).isZero();
                assertNegativeSql(attempt, Boundary.BEFORE_SERVICE);
                externalChange.run(); // Own committed transaction on the test thread, outside application tx.
                var afterExternalChange = database.snapshot();
                assertThat(afterExternalChange).isNotEqualTo(beforeChange);
                assertThat(afterExternalChange.payments()).isEqualTo(beforeChange.payments());
                assertThat(afterExternalChange.previous().data()).isEqualTo(beforeChange.previous().data());
                gate.release();
                var response = window.awaitResult(future);
                attempt.awaitFinished();
                assertError(response, path(order.id()), status);
                assertNegativeSql(attempt, Boundary.SERVICE_GUARD);
                // The deliberate external role/account change is baseline, never attributed to the POST.
                PaymentRegistrationDatabaseProbe.assertUnchanged(afterExternalChange, database.snapshot());
            }
        }
    }

    private void assertRejected(String credential, String target, String input, int status, Boundary boundary)
            throws Exception {
        var before = database.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(request(attempt, credential, target, input));
            attempt.awaitFinished();
            assertError(response, target, status);
            assertNegativeSql(attempt, boundary);
            PaymentRegistrationDatabaseProbe.assertUnchanged(before, database.snapshot());
        }
    }

    private void assertNegativeSql(Attempt attempt, Boundary boundary) {
        var events = attempt.sql();
        var statements = events.stream().map(event -> event.sql().strip().toLowerCase(Locale.ROOT)).toList();
        assertThat(statements).allSatisfy(sql -> assertThat(sql).startsWith("select "));
        String restricted = boundary == Boundary.AFTER_LOCK
                ? "\\b(?:tb_order_item|tb_payment|tb_product|tb_category|tb_product_category)\\b"
                : "\\b(?:tb_order|tb_order_item|tb_payment|tb_product|tb_category|tb_product_category)\\b";
        assertThat(statements).noneSatisfy(sql -> assertThat(sql).containsPattern(restricted));
        assertThat(attempt.proxyReturned()).isFalse();
        if (boundary == Boundary.BEFORE_SERVICE) {
            assertThat(attempt.clockCalls()).isZero();
            assertThat(events).allSatisfy(event -> assertThat(event.scope()).isEqualTo(SqlScope.OUTSIDE_SERVICE));
        } else {
            attempt.assertOwnedTransactionObserved();
            assertThat(attempt.proxyFailed()).isTrue();
            assertThat(attempt.reference()).isEqualTo(reference);
        }
        if (boundary == Boundary.AFTER_LOCK) {
            assertThat(statements.stream().filter(sql -> sql.contains("for update"))).hasSize(2);
        }
        // Hibernate preparation only: independent eight-table snapshots complement these SQL assertions.
    }

    private void assertAccepted(String credential, OrderManifest order) throws Exception {
        var before = database.snapshot();
        try (var window = observation.openWindow(WAIT)) {
            var attempt = window.newRequest();
            var response = send(request(attempt, credential, path(order.id()), body()));
            attempt.awaitFinished();
            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(response.headers().allValues("Location")).isEmpty();
            assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
            var expected = mapper.createObjectNode().put("orderId", order.id())
                    .put("occurredAt", UTC_MILLISECONDS.format(occurredAt)).put("status", "PAID");
            var json = mapper.readTree(response.body());
            // Parse both trees alike: integer node width must not change exact JSON equality.
            assertThat(json).isEqualTo(mapper.readTree(expected.toString()));
            assertThat(json.get("orderId").isIntegralNumber()).isTrue();
            attempt.assertOwnedTransactionObserved();
            assertThat(attempt.proxyReturned()).isTrue();
            assertThat(attempt.proxyFailed()).isFalse();
            assertThat(attempt.reference()).isEqualTo(reference);
            var after = database.snapshot(); // New physical JDBC reads after real proxy/request completion.
            assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(before.payments().stream(),
                    Stream.of(new PaymentRow(order.id(), occurredAt))).toList());
            var expectedOrders = before.previous().data().orders().stream().map(row -> row.id() == order.id()
                    ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList();
            assertThat(after.previous().data().orders()).isEqualTo(expectedOrders);
            assertThat(after.previous().data().items()).isEqualTo(before.previous().data().items());
            assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                    .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
            var historicalTotal = after.previous().data().items().stream().filter(row -> row.orderId() == order.id())
                    .map(row -> row.unitPrice().multiply(BigDecimal.valueOf(row.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(historicalTotal).isEqualByComparingTo("20.00");
        }
    }

    private void assertError(HttpResponse<byte[]> response, String target, int status) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .isEqualTo(status == 401 ? List.of("Bearer") : List.of());
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        String message = switch (status) {
            case 400 -> "Invalid payment data";
            case 401 -> "Authentication required or invalid";
            case 403 -> "Payment registration not permitted";
            case 500 -> "Payment registration could not be completed";
            default -> throw new IllegalArgumentException("Unexpected authorization result");
        };
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", HttpStatus.valueOf(status).getReasonPhrase()).put("message", message)
                .put("path", expectedContextPath() + target.split("\\?", 2)[0]);
        assertThat(mapper.readTree(response.body())).isEqualTo(expected); // Closed four-field envelope.
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).doesNotContain(PRIVATE_DETAIL);
    }

    private HttpRequest request(Attempt attempt, String credential, String target, String input) {
        var builder = jsonRequest(target).header(PaymentRegistrationObservationConfig.HEADER, attempt.id());
        if (credential != null) { builder.header("Authorization", "Bearer " + credential); }
        return builder.POST(HttpRequest.BodyPublishers.ofString(input)).build();
    }

    private void replaceRoles(Account actor, Set<UserRole> roles) {
        assertOwnedActor(actor);
        committed().executeWithoutResult(status -> {
            assertThat(status.isNewTransaction()).isTrue();
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", actor.id());
            for (var role : roles) {
                assertThat(jdbc.update("insert into tb_user_account_role (user_account_id, role) values (?, ?)",
                        actor.id(), role.name())).isEqualTo(1);
            }
        });
        assertThat(database.snapshot().previous().roles().stream().filter(row -> row.accountId() == actor.id())
                .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(roles);
    }

    private void removeAccount(Account actor) {
        assertOwnedActor(actor);
        assertThat(jdbc.queryForObject("select count(*) from tb_order where buyer_id = ?", Long.class, actor.id()))
                .isZero(); // Delete only a fixture account with no purchase dependencies.
        committed().executeWithoutResult(status -> {
            assertThat(status.isNewTransaction()).isTrue();
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", actor.id());
            assertThat(jdbc.update("delete from tb_user_account where id = ?", actor.id())).isEqualTo(1);
        });
        assertThat(database.snapshot().previous().accountIds()).doesNotContain(actor.id());
    }

    private TransactionTemplate committed() {
        assertNoExternalTransaction();
        var transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    private void assertOwnedActor(Account actor) {
        assertNoExternalTransaction();
        assertThat(List.of(accounts.admin().id(), accounts.both().id(), accounts.client().id(), accounts.none().id()))
                .contains(actor.id());
    }

    private Account account(String profile) {
        return switch (profile) {
            case "ADMIN" -> accounts.admin();
            case "BOTH" -> accounts.both();
            case "CLIENT" -> accounts.client();
            case "NONE" -> accounts.none();
            default -> throw new IllegalArgumentException("Unknown profile case");
        };
    }

    private Set<UserRole> roles(String profile) {
        return switch (profile) {
            case "ADMIN" -> Set.of(UserRole.ADMIN);
            case "BOTH" -> Set.of(UserRole.ADMIN, UserRole.CLIENT);
            case "CLIENT" -> Set.of(UserRole.CLIENT);
            case "NONE" -> Set.of();
            default -> throw new IllegalArgumentException("Unknown profile case");
        };
    }

    private String withClaims(Account actor, List<String> advertisedRoles) {
        var original = decoder.decode(token(actor));
        return signed(JwtClaimsSet.builder().claims(claims -> claims.putAll(original.getClaims()))
                .claim("name", foreignBuyer.name()).claim("roles", advertisedRoles)
                .claim("authorities", advertisedRoles).claim("scope", String.join(" ", advertisedRoles)).build());
    }

    private String signed(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String invalidSignature(String valid) {
        var pieces = valid.split("\\.");
        assertThat(pieces).hasSize(3);
        pieces[2] = (pieces[2].startsWith("A") ? "B" : "A") + pieces[2].substring(1);
        return String.join(".", pieces);
    }

    private OrderManifest order(Account buyer) {
        return fixture.createOrder(buyer, createdAt, List.of(new ItemInput(product.id(), 2, "10.00", "20.00")), "20.00");
    }

    private String token(Account actor) { return tokens.get(actor.id()); }
    private String path(long orderId) { return "/admin/orders/" + orderId + "/payments"; }
    private String body() { return mapper.createObjectNode().put("occurredAt", UTC_MILLISECONDS.format(occurredAt)).toString(); }
}
