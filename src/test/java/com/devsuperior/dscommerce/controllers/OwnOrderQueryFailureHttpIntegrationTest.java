package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryFailureInjectionTestConfig.Attempt;
import com.devsuperior.dscommerce.config.OwnOrderQueryFailureInjectionTestConfig.FailureProbe;
import com.devsuperior.dscommerce.config.OwnOrderQueryFailureInjectionTestConfig.Point;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.Scenario;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false", "spring.h2.console.enabled=false",
                "feature007.own-order-failure-test-context=root"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import({OwnOrderQueryTestObservationConfig.class, OwnOrderQueryFailureInjectionTestConfig.class})
class OwnOrderQueryFailureHttpIntegrationTest extends OwnOrderQueryFailureHttpTestSupport {
    @Override protected String contextPath() { return ""; }
    @Override protected String identity() { return "root"; }
}

/** Real HTTP only. Does not inherit or repeat previous checkpoint cases in these new failure contexts. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OwnOrderQueryFailureHttpTestSupport {
    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;
    @Autowired private JwtDecoder decoder;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @Autowired private FailureProbe probe;
    @LocalServerPort private int port;
    private HttpClient http;
    private OwnOrderQueryTestFixture fixture;
    private Scenario scenario;
    private String token;
    private String stableUrl;
    private int logins, requests;

    protected abstract String contextPath();
    protected abstract String identity();

    @BeforeAll
    void verifyDedicatedFailureContextAndOpenTransport() {
        outsideTransaction();
        var env = context.getEnvironment();
        require(contextPath().equals(context.getServletContext().getContextPath()), "Expected failure context path");
        require(identity().equals(env.getRequiredProperty("feature007.own-order-failure-test-context")), "Expected failure identity");
        require("false".equals(env.getRequiredProperty("spring.jpa.open-in-view")), "Disable OSIV");
        require("create-drop".equals(env.getRequiredProperty("spring.jpa.hibernate.ddl-auto")), "Keep create-drop");
        require("never".equals(env.getRequiredProperty("spring.sql.init.mode")), "Disable SQL seed locally");
        require("".equals(env.getRequiredProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")), "Disable Hibernate seed locally");
        stableUrl = env.getRequiredProperty("spring.datasource.url");
        var metadata = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        require(stableUrl.startsWith("jdbc:h2:mem:admin-product-test-") && stableUrl.split(";", 2)[0].equals(metadata),
                "Expected isolated stable failure database");
        assertEmpty();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        System.out.println("T024_CONTEXT context=" + identity() + " port=" + port + " database=" + metadata + " osiv=false");
    }

    @BeforeEach
    void prepareFullIdPageAndRealLoginOutsideFailureScope() {
        outsideTransaction();
        assertEmpty();
        fixture = new OwnOrderQueryTestFixture(context);
        scenario = fixture.preparePaginationScenario();
        // Five own roots plus interleaved foreign roots. The first size-2 page is full, requiring real count.
        require(scenario.expectedClientOrders().size() == 5, "Use the full-page count control fixture");
        try {
            var account = scenario.accounts().client();
            var response = send(HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(
                            mapper.writeValueAsBytes(Map.of("name", account.name(), "password", account.password())))).build());
            logins++;
            require(response.statusCode() == 200, "Real failure-fixture login must return 200");
            var credential = mapper.readValue(response.body(), TokenResponseDTO.class);
            require("Bearer".equals(credential.tokenType()) && credential.accessToken() != null, "Use the real bearer credential");
            token = credential.accessToken();
            require(Long.toString(account.id()).equals(decoder.decode(token).getSubject()), "Login must identify the fixture owner");
        } catch (IOException | JwtException failure) { throw new IllegalStateException("Failure-fixture login could not be verified"); }
    }

    @AfterEach
    void disarmAndCleanOnlyOwnedRows() {
        probe.disarm();
        try { if (fixture != null) { fixture.close(); } }
        finally { fixture = null; scenario = null; token = null; }
        outsideTransaction();
        assertEmpty();
        require(stableUrl.equals(context.getEnvironment().getRequiredProperty("spring.datasource.url")), "Keep the same context database");
        sql.clear();
    }

    @AfterAll
    void closeTransportAndReportActualCounts() {
        if (http != null) { http.close(); }
        System.out.println("T024_COUNTS context=" + identity() + " httpLogins=" + logins + " queryHttpRequests=" + requests
                + " mockMvcDispatches=0");
    }

    @ParameterizedTest(name = "[{index}] {0} {1} {2}")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryFailureHttpTestSupport#failureCases")
    void failureUs37IsSafe500AtTheReachedRealStage(Point point, String method, boolean list) {
        var a = probe.arm(point);
        var path = path(list) + "&private=must-not-appear";
        assertError(request(a, method, path, "application/json", 500), method, path, 500,
                "Internal Server Error", "Order query could not be completed");
        assertThat(a.hits).isEqualTo(1);
        assertThat(a.transactionAtHit).isTrue();
        assertThat(a.proxyReturns).isZero();
        if (point == Point.INITIAL_LOOKUP || point == Point.FILTER_SCALAR) {
            assertThat(a.serviceEntries).isZero();
            assertThat(a.bodyEntries).isZero();
            assertThat(a.initialLookups).isEqualTo(1);
            assertThat(a.filterScalars).isEqualTo(point == Point.FILTER_SCALAR ? 1 : 0);
        } else {
            assertThat(a.serviceEntries).isEqualTo(1);
            assertThat(a.bodyEntries).isEqualTo(1);
            assertThat(a.bodyReadOnly && a.outsideAtEntry && a.outsideAtExit).isTrue();
            assertThat(a.guards).isEqualTo(1);
            assertThat(a.lateScalars).isEqualTo(1);
            assertThat(a.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        assertThat(a.bodyReturns).isEqualTo(point == Point.COMPLETION ? 1 : 0);
        if (point == Point.COMPLETION) { assertThat(a.beforeCommits).isEqualTo(1); }
        assertSqlReach(a, list, point);
        report(a);
    }

    @ParameterizedTest
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryFailureHttpTestSupport#routes")
    void failureDisarmedStageControlUsesRealQueriesAndCompleteSuccessfulDto(String method, boolean list) {
        var a = probe.arm(Point.NONE);
        var response = request(a, method, path(list), "application/json", 200);
        assertThat(a.hits).isZero();
        assertThat(a.bodyEntries).isEqualTo(1);
        assertThat(a.bodyReturns).isEqualTo(1);
        assertThat(a.proxyReturns).isEqualTo(1);
        assertThat(a.beforeCommits).isEqualTo(1);
        assertThat(a.completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(a.bodyReadOnly && a.outsideAtEntry && a.outsideAtExit).isTrue();
        assertSqlReach(a, list, Point.NONE);
        if (method.equals("HEAD")) { assertThat(response.body()).isEmpty(); }
        else { assertSuccessJson(response, list); }
        report(a);
    }

    @ParameterizedTest(name = "[{index}] late {0} {1} {2}")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryFailureHttpTestSupport#lateCases")
    void failureLateCurrentAccountOrProfileLossRetains401Or403InsteadOf500(Point point, String method, boolean list) {
        var a = probe.arm(point);
        var before = database.snapshot();
        var owner = scenario.accounts().client().id();
        a.lateMutation = () -> {
            // Explicit independent committed preparation after filter conversion, before the real late scalar guard.
            try (var connection = database.openConnection()) {
                connection.setAutoCommit(false);
                try {
                    if (point == Point.LATE_REMOVED) {
                        update(connection, "delete from tb_order_item where order_id in (select id from tb_order where buyer_id = ?)", owner);
                        require(update(connection, "delete from tb_order where buyer_id = ?", owner) == 5, "Remove only this fixture owner's roots");
                    }
                    require(update(connection, "delete from tb_user_account_role where user_account_id = ?", owner) == 1,
                            "Remove only this fixture owner's CLIENT profile");
                    if (point == Point.LATE_REMOVED) {
                        require(update(connection, "delete from tb_user_account where id = ?", owner) == 1, "Remove only the fixture owner");
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException | Error failure) { connection.rollback(); throw failure; }
            } catch (SQLException failure) { throw new IllegalStateException("T024 independent late preparation failed"); }
            a.afterMutation = database.snapshot();
        };
        var status = point == Point.LATE_REMOVED ? 401 : 403;
        var path = path(list);
        assertError(request(a, method, path, "application/json", status), method, path, status,
                status == 401 ? "Unauthorized" : "Forbidden",
                status == 401 ? "Authentication required or invalid" : "Order query not permitted");
        assertThat(a.hits).isEqualTo(1);
        assertThat(a.initialLookups).isEqualTo(1);
        assertThat(a.filterScalars).isEqualTo(1);
        assertThat(a.guards).isEqualTo(1);
        assertThat(a.lateScalars).isEqualTo(1);
        assertThat(a.bodyReadOnly && a.transactionAtHit && a.outsideAtEntry && a.outsideAtExit).isTrue();
        assertThat(a.bodyReturns).isZero();
        assertThat(a.proxyReturns).isZero();
        assertThat(a.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(a.idSqlExecuted + a.countSqlExecuted + a.listFetchSqlExecuted + a.detailFetchSqlExecuted).isZero();
        var after = database.snapshot();
        require(!after.roles().stream().anyMatch(row -> row.accountId() == owner), "Role removal must remain committed");
        require(after.accountIds().contains(owner) == (point == Point.LATE_NO_ROLE), "Keep absence distinct from no permission");
        require(before.data().products().equals(after.data().products()) && before.data().categories().equals(after.data().categories())
                && before.data().associations().equals(after.data().associations()), "Late preparation cannot change the catalog");
        require(before.data().orders().stream().filter(row -> row.buyerId() != owner).toList()
                .equals(after.data().orders().stream().filter(row -> row.buyerId() != owner).toList()), "Keep all foreign roots intact");
        var ownIds = before.data().orders().stream().filter(row -> row.buyerId() == owner).map(row -> row.id()).toList();
        require(before.data().items().stream().filter(row -> !ownIds.contains(row.orderId())).toList()
                .equals(after.data().items().stream().filter(row -> !ownIds.contains(row.orderId())).toList()), "Keep foreign items intact");
        require(before.accountIds().stream().filter(id -> id != owner).toList()
                .equals(after.accountIds().stream().filter(id -> id != owner).toList()), "Keep other account identities intact");
        require(before.roles().stream().filter(row -> row.accountId() != owner).toList()
                .equals(after.roles().stream().filter(row -> row.accountId() != owner).toList()), "Keep other account profiles intact");
        if (point == Point.LATE_NO_ROLE) {
            require(before.data().equals(after.data()), "Revocation changes profiles only, never purchase data");
        }
        report(a);
    }

    @ParameterizedTest
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryFailureHttpTestSupport#routes")
    void failureInvalidInputIs400BeforeAnyServiceOrCompletionInjection(String method, boolean list) {
        var a = probe.arm(Point.COMPLETION);
        var path = list ? "/orders?page=-1&size=2&private=must-not-appear" : "/orders/0?private=must-not-appear";
        assertError(request(a, method, path, "application/json", 400), method, path, 400, "Bad Request", "Invalid order query data");
        assertThat(a.initialLookups).isEqualTo(1);
        assertThat(a.filterScalars).isEqualTo(1);
        assertThat(a.serviceEntries).isZero();
        assertThat(a.guards).isZero();
        assertThat(a.hits).isZero();
        assertThat(a.idSqlExecuted + a.countSqlExecuted + a.listFetchSqlExecuted + a.detailFetchSqlExecuted).isZero();
        report(a);
    }

    @ParameterizedTest
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryFailureHttpTestSupport#nativeCases")
    void failureNativeMethodAndNegotiationKeepTheirSemanticsBeforeTheService(String method, boolean list, String accept) {
        var a = probe.arm(Point.COMPLETION);
        var response = request(a, method, path(list), accept, method.equals("PUT") ? 405 : 406);
        assertThat(a.hits).isZero();
        assertThat(a.serviceEntries).isZero();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        if (method.equals("PUT")) {
            var allow = new HashSet<String>();
            response.headers().allValues("Allow").forEach(header -> Arrays.stream(header.split(","))
                    .map(String::trim).forEach(allow::add));
            var nativeFailure = new HttpRequestMethodNotSupportedException("PUT", list ? List.of("GET", "POST") : List.of("GET"));
            assertThat(allow).containsExactlyInAnyOrderElementsOf(nativeFailure.getHeaders().getAllow().stream()
                    .map(httpMethod -> httpMethod.name()).toList());
        }
        if (method.equals("HEAD")) { assertThat(response.body()).isEmpty(); }
        report(a);
    }

    private HttpResponse<byte[]> request(Attempt a, String method, String path, String accept, int expected) {
        outsideTransaction();
        var before = database.snapshot();
        sql.clear();
        var response = send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30)).header("Accept", accept)
                .header("Authorization", "Bearer " + token).header(OwnOrderQueryFailureInjectionTestConfig.HEADER, a.id)
                .method(method, HttpRequest.BodyPublishers.noBody()).build());
        requests++;
        try { require(a.finished.await(10, TimeUnit.SECONDS), "The marked HTTP request must complete before inspecting the probe"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("T024 observation interrupted"); }
        assertThat(a.requests).isEqualTo(1);
        System.out.println("T024_HTTP context=" + identity() + " point=" + a.point + " method=" + method + " path=" + path
                + " expected=" + expected + " actual=" + response.statusCode() + " bodyBytes=" + response.body().length);
        var after = database.snapshot();
        if (a.point == Point.LATE_REMOVED || a.point == Point.LATE_NO_ROLE) {
            require(a.afterMutation != null, "The deliberate late preparation must be observed independently");
            OwnOrderQueryDatabaseProbe.assertUnchanged(a.afterMutation, after);
        } else { OwnOrderQueryDatabaseProbe.assertUnchanged(before, after); }
        assertThat(sql.events()).allSatisfy(event -> assertThat(event.sql()).startsWith("select"));
        assertThat(response.statusCode()).isEqualTo(expected);
        return response;
    }

    private void assertError(HttpResponse<byte[]> response, String method, String path, int status, String error, String message) {
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .containsExactlyElementsOf(status == 401 ? List.of("Bearer") : List.of());
        if (method.equals("HEAD")) { assertThat(response.body()).isEmpty(); return; }
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", contextPath() + path.split("\\?", 2)[0]);
        require(expected.equals(json(response.body())), "Only the exact safe four-field envelope may be returned");
    }

    private void assertSuccessJson(HttpResponse<byte[]> response, boolean list) {
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        var expected = list ? mapper.createObjectNode().put("page", 0).put("size", 2).put("totalElements", 5).put("totalPages", 3)
                : header(scenario.expectedClientOrders().getLast());
        if (list) { scenario.expectedClientOrders().subList(0, 2).forEach(order -> expected.withArray("content").add(header(order))); }
        else {
            var items = expected.putArray("items");
            for (var item : scenario.expectedClientOrders().getLast().items()) {
                items.addObject().put("productId", item.productId()).put("productName", item.productName()).put("quantity", item.quantity())
                        .put("unitPrice", item.unitPrice()).put("subtotal", item.subtotal());
            }
        }
        try { require(json(response.body()).equals(json(mapper.writeValueAsBytes(expected))), "Expected exact complete own-order success JSON"); }
        catch (IOException failure) { throw new AssertionError("T024 explicit JSON oracle must be serializable"); }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode header(OrderManifest order) {
        return mapper.createObjectNode().put("id", order.id()).put("createdAt", order.createdAt().toString())
                .put("status", order.status()).put("total", order.total());
    }

    private void assertSqlReach(Attempt a, boolean list, Point point) {
        var early = point == Point.INITIAL_LOOKUP || point == Point.FILTER_SCALAR || point == Point.LATE_SCALAR;
        assertThat(a.idSqlExecuted).isEqualTo(list && !early ? 1 : 0);
        assertThat(a.countSqlExecuted).isEqualTo(list && !early && point != Point.ID_PAGE ? 1 : 0);
        assertThat(a.listFetchSqlExecuted).isEqualTo(list && !early && point != Point.ID_PAGE && point != Point.COUNT ? 1 : 0);
        assertThat(a.detailFetchSqlExecuted).isEqualTo(!list && !early ? 1 : 0);
    }

    private void report(Attempt a) {
        System.out.println("T024_REACHED point=" + a.point + " hits=" + a.hits + " bodyReturns=" + a.bodyReturns
                + " proxyReturns=" + a.proxyReturns + " beforeCommits=" + a.beforeCommits + " completion=" + a.completion
                + " ids=" + a.idSqlExecuted + " count=" + a.countSqlExecuted + " listFetch=" + a.listFetchSqlExecuted
                + " detailFetch=" + a.detailFetchSqlExecuted);
    }

    private String path(boolean list) { return list ? "/orders?page=0&size=2" : "/orders/" + scenario.expectedClientOrders().getLast().id() + "?query=ignored"; }
    private URI uri(String path) { return URI.create("http://localhost:" + port + contextPath() + path); }
    private JsonNode json(byte[] bytes) {
        try { return mapper.readTree(bytes); }
        catch (IOException failure) { throw new AssertionError("T024 response must be valid JSON"); }
    }
    private HttpResponse<byte[]> send(HttpRequest request) {
        try { return http.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("T024 HTTP interrupted"); }
        catch (IOException failure) { throw new IllegalStateException("T024 HTTP transport failed"); }
    }
    private static int update(Connection connection, String sql, long owner) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) { statement.setLong(1, owner); return statement.executeUpdate(); }
    }
    private void assertEmpty() {
        var s = database.snapshot();
        require(s.accountIds().isEmpty() && s.roles().isEmpty() && s.data().orders().isEmpty() && s.data().items().isEmpty()
                && s.data().products().isEmpty() && s.data().categories().isEmpty() && s.data().associations().isEmpty(),
                "Use only this fixture's seed-free rows and clean them after each case");
    }
    private static void outsideTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "T024 HTTP must have no caller transaction");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError(message); } }

    static Stream<Arguments> routes() {
        return Stream.of("GET", "HEAD").flatMap(method -> Stream.of(true, false).map(list -> Arguments.of(method, list)));
    }
    static Stream<Arguments> failureCases() {
        return Stream.of("GET", "HEAD").flatMap(method -> Stream.concat(
                Stream.of(Point.INITIAL_LOOKUP, Point.FILTER_SCALAR, Point.LATE_SCALAR, Point.CALCULATION, Point.COMPLETION)
                        .flatMap(point -> Stream.of(true, false).map(list -> Arguments.of(point, method, list))),
                Stream.of(Arguments.of(Point.ID_PAGE, method, true), Arguments.of(Point.COUNT, method, true),
                        Arguments.of(Point.LIST_FETCH, method, true), Arguments.of(Point.DETAIL_FETCH, method, false),
                        Arguments.of(Point.MAPPING, method, false))));
    }
    static Stream<Arguments> lateCases() {
        return Stream.of(Point.LATE_REMOVED, Point.LATE_NO_ROLE).flatMap(point -> Stream.of("GET", "HEAD")
                .flatMap(method -> Stream.of(true, false).map(list -> Arguments.of(point, method, list))));
    }
    static Stream<Arguments> nativeCases() {
        return Stream.of(true, false).flatMap(list -> Stream.of(Arguments.of("PUT", list, "application/json"),
                Arguments.of("GET", list, "text/plain"), Arguments.of("HEAD", list, "text/plain")));
    }
}
