package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.config.OwnOrderQueryTestObservationConfig;
import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.OrderRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.ItemInput;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.OrderManifest;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.Scenario;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationDatabaseProbe.PaymentRow;
import com.devsuperior.dscommerce.fixtures.PaymentRegistrationTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture.FIRST_INSTANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false",
                "spring.h2.console.enabled=false", "feature007.own-order-list-test-context=root"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(OwnOrderQueryTestObservationConfig.class)
class OwnOrderQueryListHttpIntegrationTest extends OwnOrderQueryListHttpTestSupport {
    @Override protected String expectedContextPath() { return ""; }
    @Override protected String expectedContextIdentity() { return "root"; }
}

/** Shared US1 assertions only; does not inherit or rerun the authenticationGate tests of F0. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OwnOrderQueryListHttpTestSupport {

    @Test
    void listPost008PaidOldPurchaseKeepsTwoTwoOneCreationIdOrderAndOwnCount() {
        try (var journey = new PaidOwnOrderHttpJourney(context, mapper, port, expectedContextPath(), sql)) {
            journey.prepare("client");
            journey.assertPages();
            var oldest = journey.orders.getLast();
            var newest = journey.orders.getFirst();
            // The oldest purchase is paid later in occurrence time; neither payment changes creation order.
            journey.pay(oldest, FIRST_INSTANT.plusSeconds(7200));
            journey.pay(newest, FIRST_INSTANT.plusSeconds(120));
            journey.pay(journey.orders.get(2), FIRST_INSTANT.plusSeconds(180));
            journey.assertPages();
            journey.assertDetail(journey.ownerToken, journey.order(oldest.id()));
            journey.assertPage(journey.emptyToken, 0, 2, List.of(), "");
            journey.assertPage(journey.emptyToken, 3, 2, List.of(), "");
        }
    }

    private static final DateTimeFormatter INSTANT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final Set<String> PAGE_FIELDS = Set.of("content", "page", "size", "totalElements", "totalPages");
    private static final Set<String> SUMMARY_FIELDS = Set.of("id", "createdAt", "status", "total");
    private static final String SOURCE = "com.devsuperior.dscommerce.controllers.OwnOrderQueryListHttpTestSupport#";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtDecoder decoder;
    @Autowired private OwnOrderQueryDatabaseProbe database;
    @Autowired private SqlObservation sql;
    @LocalServerPort private int port;

    private final Map<Long, String> tokens = new HashMap<>();
    private OwnOrderQueryTestFixture fixture;
    private Scenario scenario;
    private HttpClient http;
    private int logins;
    private int listRequests;

    protected abstract String expectedContextPath();
    protected abstract String expectedContextIdentity();

    @BeforeAll
    void prepareCommittedManifestAndRealLoginOutsideReadWindows() {
        noCallerTransaction();
        var environment = context.getEnvironment();
        require(expectedContextIdentity().equals(environment.getRequiredProperty("feature007.own-order-list-test-context")),
                "Unexpected list context identity");
        require(expectedContextPath().equals(context.getServletContext().getContextPath()), "Unexpected context path");
        require("false".equals(environment.getRequiredProperty("spring.jpa.open-in-view")), "OSIV must be disabled");
        require("create-drop".equals(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")), "Keep create-drop");
        require("never".equals(environment.getRequiredProperty("spring.sql.init.mode")), "Use the seed-free context");
        var url = environment.getRequiredProperty("spring.datasource.url");
        var metadata = context.getBean(JdbcTemplate.class).execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getURL());
        require(url.startsWith("jdbc:h2:mem:admin-product-test-") && url.split(";", 2)[0].equals(metadata),
                "Use the stable isolated H2 database");
        require(url.equals(environment.getRequiredProperty("spring.datasource.url")), "Database URL must be stable");
        var empty = database.snapshot();
        require(empty.accountIds().isEmpty() && empty.roles().isEmpty() && empty.data().orders().isEmpty()
                        && empty.data().items().isEmpty() && empty.data().products().isEmpty()
                        && empty.data().categories().isEmpty() && empty.data().associations().isEmpty(),
                "List context must start without seeded data");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        fixture = new OwnOrderQueryTestFixture(context);
        scenario = fixture.preparePaginationScenario();
        var accounts = scenario.accounts();
        var snapshot = database.snapshot();
        for (var account : List.of(accounts.client(), accounts.admin(), accounts.both(), accounts.none(),
                scenario.otherBuyer(), scenario.emptyBuyer())) {
            var roles = snapshot.roles().stream().filter(role -> role.accountId() == account.id())
                    .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
            require(roles.equals(account.roles()), "Persisted setup profiles must match the manifest");
            if (!account.roles().isEmpty()) { tokens.put(account.id(), realHttpLogin(account)); }
        }
        System.out.println("T009_CONTEXT context=" + expectedContextIdentity() + " path="
                + (expectedContextPath().isEmpty() ? "ROOT" : expectedContextPath()) + " port=" + port
                + " database=" + metadata + " initialHttpLogins=" + logins + " mockMvcDispatches=0");
    }

    @AfterAll
    void cleanOnlyOwnedRowsAfterAllReadWindows() {
        try {
            if (fixture != null) { fixture.close(); }
        } finally {
            if (http != null) { http.close(); }
            tokens.clear();
            System.out.println("T009_COUNTS context=" + expectedContextIdentity() + " httpLogins=" + logins
                    + " listHttpRequests=" + listRequests + " mockMvcDispatches=0");
        }
    }

    @Test
    void listUs11ReturnsOnlyThreeOwnSummariesWithExactFieldsAndOrderCount() {
        // A separate pair owns exactly three/two orders; shared scenario rows are additional foreign controls.
        try (var local = new OwnOrderQueryTestFixture(context)) {
            var owner = local.prepareBuyer();
            var other = local.prepareBuyer();
            var a = local.createProduct("Resumo A", new BigDecimal("99.00"));
            var b = local.createProduct("Resumo B", new BigDecimal("12.00"));
            var first = local.createOrder(owner, FIRST_INSTANT,
                    List.of(new ItemInput(a.id(), 1, "5.00", "5.00")), "5.00");
            local.createOrder(other, FIRST_INSTANT.plusSeconds(1),
                    List.of(new ItemInput(a.id(), 1, "1.00", "1.00")), "1.00");
            var second = local.createOrder(owner, FIRST_INSTANT.plusSeconds(2),
                    List.of(new ItemInput(a.id(), 2, "10.00", "20.00")), "20.00");
            local.createOrder(other, FIRST_INSTANT.plusSeconds(3),
                    List.of(new ItemInput(b.id(), 1, "1.00", "1.00")), "1.00");
            var third = local.createOrder(owner, FIRST_INSTANT.plusSeconds(4),
                    List.of(new ItemInput(b.id(), 2, "10.00", "20.00"),
                            new ItemInput(a.id(), 3, "7.50", "22.50")), "42.50");
            assertPage(realHttpLogin(owner), "page=0&size=3", 0, 3, 3, 1, List.of(third, second, first));
        }
    }

    @Test
    void listUs12TraversesTwoTwoOneWithoutLossDuplicationOrForeignCount() {
        var visited = new ArrayList<Long>();
        var expected = scenario.expectedClientOrders();
        for (int page = 0; page < 3; page++) {
            var slice = expected.subList(page * 2, Math.min(page * 2 + 2, 5));
            var response = assertPage(clientToken(), "page=" + page + "&size=2", page, 2, 5, 3, slice);
            response.get("content").forEach(order -> visited.add(order.get("id").longValue()));
        }
        assertThat(visited).containsExactlyElementsOf(expected.stream().map(OrderManifest::id).toList());
        assertThat(new HashSet<>(visited)).hasSize(5);
    }

    @Test
    void listUs13KeepsDescendingIdTieAcrossPageBoundary() {
        var expected = scenario.expectedClientOrders();
        assertThat(expected.subList(0, 3)).extracting(OrderManifest::createdAt)
                .containsOnly(FIRST_INSTANT.plusSeconds(60));
        assertThat(expected.get(0).id()).isGreaterThan(expected.get(1).id());
        assertThat(expected.get(1).id()).isGreaterThan(expected.get(2).id());
        assertPage(clientToken(), "page=0&size=2", 0, 2, 5, 3, expected.subList(0, 2));
        assertPage(clientToken(), "page=1&size=2", 1, 2, 5, 3, expected.subList(2, 4));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void listUs14KeepsEmptyOwnerAndRequestedPosition(int page) {
        assertPage(tokens.get(scenario.emptyBuyer().id()), "page=" + page + "&size=2", page, 2, 0, 0, List.of());
    }

    @Test
    void listUs15KeepsTotalsAndPositionBeyondOwnLastPage() {
        assertPage(clientToken(), "page=3&size=2", 3, 2, 5, 3, List.of());
    }

    @ParameterizedTest(name = "invalid exact query [{index}] {0}")
    @MethodSource(SOURCE + "invalidExactQueries")
    void listUs16RejectsInvalidEmptyRepeatedOrOverflowingPagination(String query) {
        assertBadQuery(query);
    }

    @Test
    void listUs17DoesNotRetainOwnerBetweenSuccessiveIdentities() {
        var all = scenario.insertedOrders();
        assertPage(clientToken(), "", 0, 12, 5, 1, scenario.expectedClientOrders());
        assertPage(tokens.get(scenario.otherBuyer().id()), "", 0, 12, 2, 1, List.of(all.get(5), all.get(1)));
        assertPage(tokens.get(scenario.accounts().admin().id()), "", 0, 12, 1, 1, List.of(all.get(3)));
        assertPage(tokens.get(scenario.accounts().both().id()), "", 0, 12, 1, 1, List.of(all.get(7)));
        assertPage(clientToken(), "", 0, 12, 5, 1, scenario.expectedClientOrders());
    }

    @ParameterizedTest(name = "valid page [{index}] {0}")
    @MethodSource(SOURCE + "validPages")
    void listDefaultsAndLimitsPreserveExactMetadata(PageCase page) {
        assertPage(clientToken(), page.query(), page.page(), page.size(), 5, page.pages(),
                scenario.expectedClientOrders().subList(page.from(), page.to()));
    }

    @ParameterizedTest(name = "ignored aliases [{index}] {0}, explicit={1}")
    @MethodSource(SOURCE + "ignoredAliasCases")
    void listP2IgnoresAliasesBeforeBindingWithDefaultsAndExplicitValues(AliasCase aliases, boolean explicit) {
        var query = (explicit ? "page=1&size=2&" : "") + aliases.query();
        assertPage(clientToken(), query, explicit ? 1 : 0, explicit ? 2 : 12, 5, explicit ? 3 : 1,
                explicit ? scenario.expectedClientOrders().subList(2, 4) : scenario.expectedClientOrders());
    }

    @ParameterizedTest(name = "exact invalid data remains invalid [{index}] {0}, {1}")
    @MethodSource(SOURCE + "invalidQueriesWithAliases")
    void listP2NeverLetsIgnoredAliasesRepairInvalidExactParameters(String invalid, AliasCase aliases) {
        assertBadQuery(invalid + "&" + aliases.query());
    }

    @Test
    void listIgnoresOwnerSelectorsAndRequestedSort() {
        var selectors = "accountId=" + scenario.otherBuyer().id() + "&buyerId=" + scenario.accounts().admin().id()
                + "&sort=id,asc&sort=createdAt,asc";
        assertPage(clientToken(), "page=0&size=2&" + selectors, 0, 2, 5, 3,
                scenario.expectedClientOrders().subList(0, 2));
        assertPage(clientToken(), "accountId=not-an-id&accountId=&buyerId=&sort=invalid&Page=3&Size=1", 0, 12, 5, 1,
                scenario.expectedClientOrders());
    }

    private JsonNode assertPage(String token, String query, int page, int size, long total, int pages,
                                List<OrderManifest> expected) {
        var response = get(token, query, 200);
        var actual = json(response);
        assertFields(actual, PAGE_FIELDS);
        assertInteger(actual.get("page"), page);
        assertInteger(actual.get("size"), size);
        assertInteger(actual.get("totalElements"), total);
        assertInteger(actual.get("totalPages"), pages);
        var content = actual.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(expected.size());
        for (int index = 0; index < expected.size(); index++) {
            var summary = content.get(index);
            var order = expected.get(index);
            assertFields(summary, SUMMARY_FIELDS);
            assertInteger(summary.get("id"), order.id());
            assertThat(summary.get("createdAt").isTextual()).isTrue();
            assertThat(summary.get("createdAt").textValue()).isEqualTo(INSTANT.format(order.createdAt()));
            assertThat(summary.get("status").isTextual()).isTrue();
            assertThat(summary.get("status").textValue()).isEqualTo(order.status());
            assertThat(summary.get("total").isTextual()).isTrue();
            assertThat(summary.get("total").textValue()).isEqualTo(order.total());
        }
        return actual;
    }

    private void assertBadQuery(String query) {
        var response = get(clientToken(), query, 400);
        var expected = mapper.createObjectNode().put("status", 400).put("error", "Bad Request")
                .put("message", "Invalid order query data").put("path", expectedContextPath() + "/orders");
        require(expected.equals(json(response)), "Expected the exact safe four-field bad-query envelope");
    }

    private HttpResponse<byte[]> get(String token, String query, int expectedStatus) {
        noCallerTransaction();
        require(token != null, "Use a prepared real login token");
        var path = "/orders" + (query.isEmpty() ? "" : "?" + query);
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("Authorization", "Bearer " + token).GET().build();
        var observation = database.observeRead(sql, () -> send(request));
        var response = observation.result();
        listRequests++;
        System.out.println("T009_HTTP context=" + expectedContextIdentity() + " method=GET path=" + path
                + " expected=" + expectedStatus + " actual=" + response.statusCode() + " bodyBytes=" + response.body().length);
        observation.assertNoWrites();
        assertThat(response.statusCode()).as("own order list HTTP status").isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        return response;
    }

    private String realHttpLogin(Account account) {
        noCallerTransaction();
        try {
            var request = HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(
                            mapper.writeValueAsBytes(Map.of("name", account.name(), "password", account.password())))).build();
            var response = send(request);
            logins++;
            require(response.statusCode() == 200, "Real list fixture login must succeed");
            var token = mapper.readValue(response.body(), TokenResponseDTO.class);
            require("Bearer".equals(token.tokenType()) && token.accessToken() != null, "Expected a real bearer token");
            require(Long.toString(account.id()).equals(decoder.decode(token.accessToken()).getSubject()),
                    "Login identity must match the prepared account");
            return token.accessToken();
        } catch (IOException | JwtException failure) {
            throw new IllegalStateException("Real list fixture login could not be verified");
        }
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("List HTTP transport interrupted");
        } catch (IOException failure) {
            throw new IllegalStateException("List HTTP transport did not complete");
        }
    }

    private JsonNode json(HttpResponse<byte[]> response) {
        try {
            return mapper.readTree(response.body());
        } catch (IOException failure) {
            throw new AssertionError("List response must be valid JSON");
        }
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + expectedContextPath() + path); }
    private String clientToken() { return tokens.get(scenario.accounts().client().id()); }

    private static void assertFields(JsonNode node, Set<String> expected) {
        assertThat(node).isNotNull();
        assertThat(node.isObject()).isTrue();
        var fields = new HashSet<String>();
        node.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static void assertInteger(JsonNode node, long expected) {
        assertThat(node.isIntegralNumber()).isTrue();
        assertThat(node.canConvertToLong()).isTrue();
        assertThat(node.longValue()).isEqualTo(expected);
    }

    private static void noCallerTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "HTTP must not have a caller transaction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    static Stream<String> invalidExactQueries() {
        return Stream.of("page=-1", "page=not-an-integer", "page=1.5", "page=1e0", "page=",
                "page=2147483648", "page=-2147483649", "size=0", "size=-1", "size=51",
                "size=not-an-integer", "size=1.5", "size=1e0", "size=", "size=2147483648",
                "page=0&page=1", "page=0&page=0", "page=&page=0", "page=0&page=",
                "size=2&size=3", "size=2&size=2", "size=&size=2", "size=2&size=",
                "page=1073741824&size=2", "page=42949673&size=50", "page=2147483647",
                "page=%20", "size=%20");
    }

    static Stream<PageCase> validPages() {
        return Stream.of(new PageCase("both defaults", "", 0, 12, 0, 5, 1),
                new PageCase("default page", "size=2", 0, 2, 0, 2, 3),
                new PageCase("default size on later page", "page=1", 1, 12, 5, 5, 1),
                new PageCase("minimum size", "size=1", 0, 1, 0, 1, 5),
                new PageCase("maximum size", "size=50", 0, 50, 0, 5, 1),
                new PageCase("maximum page and offset", "page=2147483647&size=1", 2147483647, 1, 5, 5, 5),
                new PageCase("last valid offset for size two", "page=1073741823&size=2", 1073741823, 2, 5, 5, 3),
                new PageCase("last valid offset for size fifty", "page=42949672&size=50", 42949672, 50, 5, 5, 1),
                new PageCase("explicit defaults", "page=0&size=12", 0, 12, 0, 5, 1));
    }

    static Stream<Arguments> ignoredAliasCases() {
        return aliasCases().stream().flatMap(aliases -> Stream.of(false, true)
                .map(explicit -> Arguments.of(aliases, explicit)));
    }

    static Stream<Arguments> invalidQueriesWithAliases() {
        return Stream.of("page=-1&size=2", "page=0&page=1&size=2", "page=0&size=", "page=0&size=2&size=2")
                .flatMap(invalid -> aliasCases().stream().filter(aliases -> aliases.label().startsWith("combined/"))
                        .map(aliases -> Arguments.of(invalid, aliases)));
    }

    private static List<AliasCase> aliasCases() {
        var cases = new ArrayList<AliasCase>();
        var aliases = List.of("_page", "_size", "size[]", "!page");
        for (var variant : List.of("integer", "empty", "text", "fraction", "repeated")) {
            var combined = new ArrayList<String>();
            for (var alias : aliases) {
                var query = aliasQuery(alias, variant);
                cases.add(new AliasCase(alias + "/" + variant, query));
                combined.add(query);
            }
            cases.add(new AliasCase("combined/" + variant, String.join("&", combined)));
        }
        return List.copyOf(cases);
    }

    private static String aliasQuery(String alias, String variant) {
        var name = URLEncoder.encode(alias, StandardCharsets.UTF_8);
        return switch (variant) {
            case "integer" -> name + "=3";
            case "empty" -> name + "=";
            case "text" -> name + "=not-an-integer";
            case "fraction" -> name + "=1.5";
            case "repeated" -> name + "=&" + name + "=not-an-integer&" + name + "=3";
            default -> throw new IllegalArgumentException("Unknown alias fixture variant");
        };
    }

    record AliasCase(String label, String query) { @Override public String toString() { return label; } }
    record PageCase(String label, String query, int page, int size, int from, int to, int pages) {
        @Override public String toString() { return label; }
    }
}

/** T029 composition shared by list/detail/security and inherited ROOT/Api cases; no test discovery here. */
final class PaidOwnOrderHttpJourney implements AutoCloseable {

    private static final DateTimeFormatter INSTANT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final WebApplicationContext context;
    private final ObjectMapper mapper;
    private final int port;
    private final String contextPath;
    private final String databaseUrl;
    private final SqlObservation sql;
    private final HttpClient http;
    private final PaymentRegistrationDatabaseProbe database;
    private final PaymentRegistrationDatabaseProbe.Snapshot received;
    private final PaymentRegistrationTestFixture fixture;
    private final Map<Long, String> currentNames = new HashMap<>();
    private ProductData a;
    private ProductData b;
    Account owner;
    private Account payer;
    String ownerToken;
    String payerToken;
    String deniedToken;
    String emptyToken;
    List<OrderManifest> orders;
    OrderManifest payerOrder;

    PaidOwnOrderHttpJourney(WebApplicationContext context, ObjectMapper mapper, int port,
                            String contextPath, SqlObservation sql) {
        outsideTransaction();
        this.context = context;
        this.mapper = mapper;
        this.port = port;
        this.contextPath = contextPath;
        this.sql = sql;
        databaseUrl = context.getEnvironment().getRequiredProperty("spring.datasource.url");
        assertThat(databaseUrl).startsWith("jdbc:h2:mem:admin-product-test-");
        assertThat(context.getServletContext().getContextPath()).isEqualTo(contextPath);
        assertThat(port).isPositive();
        database = new PaymentRegistrationDatabaseProbe(databaseUrl);
        received = database.snapshot();
        fixture = new PaymentRegistrationTestFixture(context);
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        // No insertion here: preparation below is always inside the caller's try-with-resources.
    }

    void prepare(String identity) {
        outsideTransaction();
        var accounts = fixture.prepareAccessAccounts();
        owner = switch (identity) {
            case "client" -> accounts.client();
            case "admin" -> accounts.admin();
            case "both" -> accounts.both();
            default -> throw new AssertionError("Use an authorized own-order identity");
        };
        payer = owner.id() == accounts.admin().id()
                ? fixture.createAccount("us5-payer-" + java.util.UUID.randomUUID(), "password123", Set.of(UserRole.ADMIN))
                : accounts.admin();
        assertThat(payer.id()).isNotEqualTo(owner.id());
        assertThat(payer.roles()).containsExactly(UserRole.ADMIN);
        var otherBuyer = fixture.prepareBuyer();
        var emptyBuyer = fixture.prepareBuyer();
        a = fixture.createProduct("Produto A anterior", new BigDecimal("99.00"));
        b = fixture.createProduct("Produto B anterior", new BigDecimal("12.00"));
        assertThat(b.id()).isGreaterThan(a.id());
        currentNames.put(a.id(), a.name());
        currentNames.put(b.id(), b.name());
        var oldest = fixture.createOrder(owner, FIRST_INSTANT,
                List.of(new ItemInput(b.id(), 2, "10.00", "20.00"),
                        new ItemInput(a.id(), 3, "7.50", "22.50")), "42.50");
        var middle = fixture.createOrder(owner, FIRST_INSTANT.plusSeconds(10),
                List.of(new ItemInput(b.id(), 2, "10.00", "20.00")), "20.00");
        var tiedFirst = fixture.createOrder(owner, FIRST_INSTANT.plusSeconds(60),
                List.of(new ItemInput(a.id(), 1, "0.01", "0.01")), "0.01");
        var tiedSecond = fixture.createOrder(owner, FIRST_INSTANT.plusSeconds(60),
                List.of(new ItemInput(b.id(), 1, "9007199254740993.01", "9007199254740993.01")), "9007199254740993.01");
        var tiedLast = fixture.createOrder(owner, FIRST_INSTANT.plusSeconds(60),
                List.of(new ItemInput(a.id(), 1, "15.25", "15.25")), "15.25");
        assertThat(tiedLast.id()).isGreaterThan(tiedSecond.id());
        assertThat(tiedSecond.id()).isGreaterThan(tiedFirst.id());
        orders = List.of(tiedLast, tiedSecond, tiedFirst, middle, oldest);
        payerOrder = fixture.createOrder(payer, FIRST_INSTANT.plusSeconds(60),
                List.of(new ItemInput(a.id(), 1, "3.33", "3.33")), "3.33");
        fixture.createOrder(otherBuyer, FIRST_INSTANT.plusSeconds(5),
                List.of(new ItemInput(b.id(), 1, "4.00", "4.00")), "4.00");
        ownerToken = login(owner);
        payerToken = login(payer);
        deniedToken = login(accounts.none());
        emptyToken = login(emptyBuyer);
        System.out.println("T029_CONTEXT path=" + (contextPath.isEmpty() ? "ROOT" : contextPath)
                + " port=" + port + " database=" + databaseUrl.split(";", 2)[0]
                + " owner=" + identity + " scenarioOwnOrders=5 scenarioForeignOrders=2 mockMvcDispatches=0");
    }

    void pay(OrderManifest target, Instant occurrence) {
        outsideTransaction();
        assertThat(fixture.ownedOrderIds()).contains(target.id());
        assertThat(target.buyerId()).isEqualTo(owner.id());
        assertThat(target.status()).isEqualTo("WAITING_PAYMENT");
        var before = database.snapshot();
        var path = "/admin/orders/" + target.id() + "/payments";
        var response = send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + payerToken).header("Content-Type", "application/json")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(
                        bytes(Map.of("occurredAt", INSTANT.format(occurrence))))).build());
        assertThat(response.statusCode()).as("T029 real payment HTTP status").isEqualTo(201);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().allValues("Location")).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
        assertJson(json(response), mapper.createObjectNode().put("orderId", target.id())
                .put("occurredAt", INSTANT.format(occurrence)).put("status", "PAID"));
        // New driver connections after the actual HTTP response, without a caller transaction.
        var after = database.snapshot();
        assertThat(after.payments()).containsExactlyInAnyOrderElementsOf(Stream.concat(before.payments().stream(),
                Stream.of(new PaymentRow(target.id(), occurrence))).toList());
        assertThat(after.previous().data().orders()).isEqualTo(before.previous().data().orders().stream()
                .map(row -> row.id() == target.id()
                        ? new OrderRow(row.id(), row.buyerId(), row.createdAt(), "PAID") : row).toList());
        // This probe reports every changed table, including opaque account columns and profiles.
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_order");
        orders = orders.stream().map(order -> order.id() == target.id()
                ? new OrderManifest(order.id(), order.buyerId(), order.createdAt(), "PAID", order.total(), order.items())
                : order).toList();
        System.out.println("T029_PAYMENT path=" + contextPath + path
                + " status=201 independentPaymentAndPaid=true allowedChangedTables=tb_order,tb_payment");
    }

    OrderManifest order(long id) {
        return orders.stream().filter(order -> order.id() == id).findFirst().orElseThrow();
    }

    void assertPages() {
        var visited = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            var result = assertPage(ownerToken, page, 2, orders, "&sort=occurredAt,desc&buyerId=" + payer.id());
            result.get("content").forEach(row -> visited.add(row.get("id").longValue()));
        }
        assertThat(visited).containsExactlyElementsOf(orders.stream().map(OrderManifest::id).toList());
        assertThat(new HashSet<>(visited)).hasSize(5);
        assertPage(ownerToken, 3, 2, orders, "");
    }

    JsonNode assertPage(String token, int page, int size, List<OrderManifest> expectedOrders, String extra) {
        var actual = json(read("GET", "/orders?page=" + page + "&size=" + size + extra, token, 200));
        var expected = mapper.createObjectNode().put("page", page).put("size", size)
                .put("totalElements", expectedOrders.size()).put("totalPages", (expectedOrders.size() + size - 1) / size);
        var content = expected.putArray("content");
        expectedOrders.stream().skip((long) page * size).limit(size).forEach(order -> content.add(header(order)));
        assertJson(actual, expected);
        return actual;
    }

    void assertDetail(String token, OrderManifest order) {
        var expected = header(order);
        var items = expected.putArray("items");
        for (var item : order.items()) {
            items.addObject().put("productId", item.productId()).put("productName", currentNames.get(item.productId()))
                    .put("quantity", item.quantity()).put("unitPrice", item.unitPrice()).put("subtotal", item.subtotal());
        }
        assertJson(json(read("GET", "/orders/" + order.id(), token, 200)), expected);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode header(OrderManifest order) {
        // Equality of the complete wire object rejects Payment, occurrence, buyer and any other added key.
        return mapper.createObjectNode().put("id", order.id()).put("createdAt", INSTANT.format(order.createdAt()))
                .put("status", order.status()).put("total", order.total());
    }

    void renameCurrentCatalog() {
        outsideTransaction();
        var before = database.snapshot();
        var committed = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        committed.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var jdbc = context.getBean(JdbcTemplate.class);
        committed.executeWithoutResult(status -> {
            assertThat(jdbc.update("update tb_product set name = ?, price = ? where id = ?",
                    "Á", new BigDecimal("0.00"), a.id())).isEqualTo(1);
            assertThat(jdbc.update("update tb_product set name = ?, price = ? where id = ?",
                    " Nome atual B ", new BigDecimal("37.00"), b.id())).isEqualTo(1);
        });
        var after = database.snapshot();
        assertThat(after.payments()).isEqualTo(before.payments());
        assertThat(after.previous().data().products()).isEqualTo(before.previous().data().products().stream().map(row -> {
            if (row.id() == a.id()) {
                return new ProductRow(row.id(), "Á", row.description(), row.image(), new BigDecimal("0.00"));
            }
            if (row.id() == b.id()) {
                return new ProductRow(row.id(), " Nome atual B ", row.description(), row.image(), new BigDecimal("37.00"));
            }
            return row;
        }).toList());
        assertThatThrownBy(() -> OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous()))
                .isInstanceOf(AssertionError.class).hasMessage("Observed database state changed: tb_product");
        currentNames.put(a.id(), "Á");
        currentNames.put(b.id(), " Nome atual B ");
    }

    void assertForeignEqualsAbsent(long foreignId) {
        assertThat(database.snapshot().previous().data().orders()).noneMatch(row -> row.id() == Long.MAX_VALUE);
        JsonNode previous = null;
        for (var id : List.of(foreignId, Long.MAX_VALUE)) {
            var path = "/orders/" + id + "?private=must-not-appear&buyerId=" + owner.id();
            var error = assertError("GET", path, payerToken, 404, "Not Found", "Order not found");
            var withoutPath = (com.fasterxml.jackson.databind.node.ObjectNode) error.deepCopy();
            withoutPath.remove("path");
            if (previous != null) { assertThat(withoutPath).isEqualTo(previous); }
            previous = withoutPath;
            assertError("HEAD", path, payerToken, 404, "Not Found", "Order not found");
        }
    }

    JsonNode assertError(String method, String path, String token, int status, String error, String message) {
        var response = read(method, path, token, status);
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .containsExactlyElementsOf(status == 401 ? List.of("Bearer") : List.of());
        if (method.equals("HEAD")) { assertThat(response.body()).isEmpty(); return null; }
        var actual = json(response);
        assertJson(actual, mapper.createObjectNode().put("status", status).put("error", error)
                .put("message", message).put("path", contextPath + path.split("\\?", 2)[0]));
        return actual;
    }

    void assertHead(String path, String token, int expectedStatus) {
        var response = read("HEAD", path, token, expectedStatus);
        assertThat(response.body()).isEmpty();
        assertThat(response.headers().allValues("WWW-Authenticate")).isEmpty();
    }

    private HttpResponse<byte[]> read(String method, String path, String token, int expectedStatus) {
        outsideTransaction();
        assertThat(method).isIn("GET", "HEAD");
        var builder = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) { builder.header("Authorization", "Bearer " + token); }
        var before = database.snapshot();
        sql.clear();
        var response = send(builder.build());
        var events = sql.events();
        var after = database.snapshot();
        new OwnOrderQueryDatabaseProbe.Observation<>(response, before.previous(), after.previous(), events).assertNoWrites();
        PaymentRegistrationDatabaseProbe.assertUnchanged(before, after);
        for (var event : events) {
            if (event.sql() != null && event.sql().matches("(?is).*\\btb_payment\\b.*")) {
                throw new AssertionError("Own-order HTTP query must not read the payment table");
            }
        }
        assertThat(response.statusCode()).as("T029 own-order HTTP status").isEqualTo(expectedStatus);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().allValues("WWW-Authenticate"))
                .containsExactlyElementsOf(expectedStatus == 401 ? List.of("Bearer") : List.of());
        System.out.println("T029_READ method=" + method + " path=" + contextPath + path.split("\\?", 2)[0]
                + " expected=" + expectedStatus + " actual=" + response.statusCode()
                + " bodyBytes=" + response.body().length + " snapshotTables=8 mockMvcDispatches=0");
        return response;
    }

    private String login(Account account) {
        outsideTransaction();
        var roles = database.snapshot().previous().roles().stream().filter(row -> row.accountId() == account.id())
                .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
        assertThat(roles).containsExactlyInAnyOrderElementsOf(account.roles());
        var response = send(HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(
                        bytes(Map.of("name", account.name(), "password", account.password())))).build());
        assertThat(response.statusCode()).as("T029 real HTTP login status").isEqualTo(200);
        try {
            var credential = mapper.readValue(response.body(), TokenResponseDTO.class);
            assertThat(credential.tokenType()).isEqualTo("Bearer");
            assertThat(credential.accessToken()).isNotBlank();
            var jwt = context.getBean(JwtDecoder.class).decode(credential.accessToken());
            assertThat(jwt.getSubject()).isEqualTo(Long.toString(account.id()));
            return credential.accessToken();
        } catch (IOException | JwtException failure) { throw new AssertionError("T029 requires a valid real login credential"); }
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try { return http.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("T029 real HTTP transport interrupted");
        } catch (IOException failure) { throw new IllegalStateException("T029 real HTTP transport could not complete"); }
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + contextPath + path); }

    private JsonNode json(HttpResponse<byte[]> response) {
        try { return mapper.readTree(response.body()); }
        catch (IOException failure) { throw new AssertionError("T029 requires valid wire JSON"); }
    }

    private byte[] bytes(Object value) {
        try { return mapper.writeValueAsBytes(value); }
        catch (IOException failure) { throw new AssertionError("T029 fixture JSON must be serializable"); }
    }

    private void assertJson(JsonNode actual, JsonNode expected) {
        try {
            // Normalize numeric nodes through wire JSON, preserving exact string money and all closed keys.
            if (actual == null || !actual.equals(mapper.readTree(mapper.writeValueAsBytes(expected)))) {
                throw new AssertionError("T029 expected exact own-order fields, owner, order, values and metadata");
            }
        } catch (IOException failure) { throw new AssertionError("T029 JSON oracle must be serializable"); }
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new AssertionError("T029 real HTTP setup/read/cleanup requires no caller transaction");
        }
    }

    @Override public void close() {
        outsideTransaction();
        try {
            fixture.close(); // Owned Payment first, then owned items/orders/accounts/catalog; never global DELETE.
            PaymentRegistrationDatabaseProbe.assertUnchanged(received, database.snapshot());
            assertThat(context.getEnvironment().getRequiredProperty("spring.datasource.url")).isEqualTo(databaseUrl);
        } finally { http.close(); }
    }
}
