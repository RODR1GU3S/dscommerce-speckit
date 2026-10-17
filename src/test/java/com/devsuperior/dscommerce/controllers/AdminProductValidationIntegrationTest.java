package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.CategoryData;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.security.Permission;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T026 business-rule HTTP RED only; no production input/diagnosis/maintenance implementation. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductValidationIntegrationTest.SqlConfig.class)
class AdminProductValidationIntegrationTest {
    private static final String ROUTE = "/admin/products";
    private static final String PAD = "\u00a0\u2003\t";
    private static final String DEFAULT_NAME = "Fone Azul";
    private static final String DEFAULT_DESCRIPTION = "Descricao completa";
    private static final String DEFAULT_IMAGE = "https://image-t026.invalid/product.jpg";
    private static final List<String> FIELDS = List.of("name", "description", "price", "image", "categoryIds");
    private static final NetworkProbe NETWORK = new NetworkProbe();
    private static SecurityManager previousManager;

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String token;
    private List<CategoryData> selected;
    private long absentCategory;
    private final Set<Long> requestOwnedProducts = new LinkedHashSet<>();

    @BeforeAll
    static void installTestOnlyNetworkObservation() {
        previousManager = System.getSecurityManager();
        if (previousManager != null) throw new IllegalStateException("T026 requires its isolated Java 21 test JVM");
        System.setSecurityManager(NETWORK);
    }

    @AfterAll
    static void restoreNetworkObservation() {
        NETWORK.stop();
        System.setSecurityManager(previousManager);
    }

    @BeforeEach
    void prepareRealAuthenticationWithoutCallerTransactionOrSeeds() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        requestOwnedProducts.clear();
        fixture = new AdminProductTestFixture(context);
        var admin = fixture.createAccount("T026 Admin", "Disposable-T026", Set.of(UserRole.ADMIN));
        token = fixture.login(admin);
        assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo(Long.toString(admin.id()));
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        sql.clear();
    }

    @AfterEach
    void cleanOnlyRequestAndFixtureOwnedRowsAfterAssertions() throws Exception {
        NETWORK.stop();
        assertNoCallerTransaction();
        if (!requestOwnedProducts.isEmpty()) {
            try (var connection = database.openConnection()) {
                connection.setAutoCommit(false);
                try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?");
                     var products = connection.prepareStatement("delete from tb_product where id = ?")) {
                    for (long id : requestOwnedProducts) {
                        pairs.setLong(1, id); pairs.executeUpdate();
                        products.setLong(1, id); products.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            }
            requestOwnedProducts.clear();
        }
        if (fixture != null) { fixture.close(); assertEmptyDatabase(); }
        sql.clear();
    }

    @Test
    void shouldCalibrateDnsAndHttpObservationWithoutSendingNetworkTraffic() {
        NETWORK.start();
        try {
            assertThatThrownBy(() -> InetAddress.getByName("t026-" + UUID.randomUUID() + ".invalid"))
                    .isInstanceOf(SecurityException.class).hasMessage(NetworkProbe.MESSAGE);
            assertThat(NETWORK.events()).contains("DNS");
        } finally { NETWORK.stop(); }
        NETWORK.start();
        try {
            assertThatThrownBy(() -> new URL("http://127.0.0.1:9/t026-calibration").openConnection().getInputStream())
                    .isInstanceOf(SecurityException.class).hasMessage(NetworkProbe.MESSAGE);
            assertThat(NETWORK.events()).contains("CONNECT");
        } finally { NETWORK.stop(); }
        System.out.println("T026_NETWORK_CALIBRATION dnsObserved=true httpConnectObserved=true blockedBeforeTraffic=true");
    }

    @ParameterizedTest(name = "T026 valid context={0}, case={1}")
    @MethodSource("validCases")
    void shouldAcceptValidLimitsAndPersistExactlyNormalizedValuesWithoutNetwork(String externalContext, RuleCase input)
            throws Exception {
        var observed = perform(externalContext, input, token, 201);
        assertThat(observed.result().getResponse().getStatus()).as("valid business limits require HTTP 201").isEqualTo(201);
        var actual = json(observed.result());
        assertThat(actual.path("id").isIntegralNumber()).isTrue();
        assertThat(actual.path("id").canConvertToLong()).isTrue();
        long id = actual.get("id").asLong();
        assertThat(id).isPositive();
        assertThat(observed.before().products()).noneMatch(row -> row.id() == id);
        var expected = input.output();
        var detail = mapper.createObjectNode().put("id", id).put("name", expected.name())
                .put("description", expected.description()).put("price", expected.price()).put("image", expected.image());
        var options = detail.putArray("categories");
        selected.stream().sorted(Comparator.comparing(CategoryData::name).thenComparingLong(CategoryData::id))
                .forEach(category -> options.addObject().put("id", category.id()).put("name", category.name()));
        assertThat(actual).isEqualTo(mapper.readTree(detail.toString()));
        String locationHeader = observed.result().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(locationHeader).isNotBlank();
        var location = URI.create(locationHeader);
        assertThat(location.getPath()).isEqualTo(externalContext + ROUTE + "/" + id);
        assertThat(location.getRawQuery()).isNull(); assertThat(location.getRawFragment()).isNull();
        var rows = new ArrayList<>(observed.before().products());
        rows.add(new ProductRow(id, expected.name(), expected.description(), expected.image(), new BigDecimal(expected.price())));
        rows.sort(Comparator.comparingLong(ProductRow::id));
        assertThat(observed.after().products()).containsExactlyElementsOf(rows);
        var pairs = new LinkedHashSet<>(observed.before().associations());
        selected.forEach(category -> pairs.add(new ProductCategory(id, category.id())));
        assertThat(observed.after().associations()).isEqualTo(pairs);
        assertThat(sql.events()).anyMatch(event -> isDml(event.sql()));
        System.out.printf("T026_VALIDATED case=%s context=%s commitVisible=true networkAttempts=0%n", input.label(), externalContext);
    }

    @ParameterizedTest(name = "T026 invalid context={0}, case={1}")
    @MethodSource("invalidCases")
    void shouldRejectInvalidBusinessDataWithSafeFieldErrorsAndNoDatabaseOrNetworkChanges(String externalContext, RuleCase input)
            throws Exception {
        var observed = perform(externalContext, input, token, 400);
        assertThat(observed.after()).isEqualTo(observed.before());
        assertThat(sql.events()).noneMatch(event -> isDml(event.sql()));
        assertThat(observed.result().getResponse().getStatus()).as("invalid business data requires HTTP 400").isEqualTo(400);
        var actual = json(observed.result());
        assertEnvelope(actual, 400, "Bad Request", "Invalid product maintenance data", externalContext);
        var errors = new ArrayList<Diagnosis>();
        var triples = new HashSet<List<String>>();
        assertThat(actual.get("errors").isArray()).isTrue();
        actual.get("errors").forEach(error -> {
            var fields = new HashSet<String>(); error.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("field", "code", "message");
            assertThat(error.get("field").isTextual()).isTrue(); assertThat(error.get("code").isTextual()).isTrue();
            assertThat(error.get("message").isTextual()).isTrue(); assertThat(error.get("message").asText()).isNotBlank();
            assertThat(triples.add(List.of(error.get("field").asText(), error.get("code").asText(), error.get("message").asText()))).isTrue();
            errors.add(new Diagnosis(error.get("field").asText(), error.get("code").asText()));
        });
        assertThat(errors).containsExactlyElementsOf(input.errors());
        assertThat(observed.result().getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(observed.result().getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(observed.result().getResponse().getContentAsString()).doesNotContain(DEFAULT_IMAGE, "SQLException", "stackTrace", "password");
        System.out.printf("T026_DIAGNOSED case=%s context=%s fields=%d networkAttempts=0%n", input.label(), externalContext, errors.size());
    }

    static Stream<Arguments> validCases() { return contexts(validInputs()); }
    static Stream<Arguments> invalidCases() { return contexts(invalidInputs()); }
    private static Stream<Arguments> contexts(List<RuleCase> inputs) {
        return Stream.of("", "/api").flatMap(context -> inputs.stream().map(input -> Arguments.of(context, input)));
    }

    private static List<RuleCase> validInputs() {
        var cases = new ArrayList<RuleCase>();
        for (var value : List.of("abc", "é".repeat(80), "\ud83e\udde9".repeat(3), "\ud83e\udde9".repeat(80)))
            cases.add(valid("name-codepoints-" + value.codePointCount(0, value.length()), Map.of("name", PAD + value + PAD), new Output(value, DEFAULT_DESCRIPTION, "10.00", DEFAULT_IMAGE)));
        for (var value : List.of("0123456789", "\ud83e\udde9".repeat(10)))
            cases.add(valid("description-ten-" + value.length(), Map.of("description", PAD + value + PAD), new Output(DEFAULT_NAME, value, "10.00", DEFAULT_IMAGE)));
        cases.add(valid("unicode-trim-preserves-internals", Map.of("name", PAD + " Fone  Azul " + PAD,
                "description", PAD + " Descricao  completa " + PAD), new Output("Fone  Azul", "Descricao  completa", "10.00", DEFAULT_IMAGE)));
        cases.add(valid("description-over-255", Map.of("description", "d".repeat(600)), new Output(DEFAULT_NAME, "d".repeat(600), "10.00", DEFAULT_IMAGE)));
        String longImage = "https://image-t026.invalid/" + "a".repeat(300) + "?x=1#part";
        cases.add(valid("image-over-255", Map.of("image", longImage), new Output(DEFAULT_NAME, DEFAULT_DESCRIPTION, "10.00", longImage)));
        for (var value : List.of("0.01", "99999999999999999.99", "1.000"))
            cases.add(valid("price-" + value, Map.of("price", value), new Output(DEFAULT_NAME, DEFAULT_DESCRIPTION, value.equals("1.000") ? "1.00" : value, DEFAULT_IMAGE)));
        for (var image : List.of("http://image-t026.invalid/a", "https://image-t026.invalid/a", "http://127.0.0.1:9/unavailable",
                "HTTPS://image-t026.invalid/a?x=1#part"))
            cases.add(valid("uri-" + cases.size(), Map.of("image", image), new Output(DEFAULT_NAME, DEFAULT_DESCRIPTION, "10.00", image)));
        return List.copyOf(cases);
    }

    private static List<RuleCase> invalidInputs() {
        var cases = new ArrayList<RuleCase>();
        for (var value : List.of("ab", "n".repeat(81), "\ud83e\udde9".repeat(2), "\ud83e\udde9".repeat(81)))
            cases.add(invalid("name-length-" + cases.size(), "name", PAD + value + PAD, "INVALID_LENGTH"));
        for (var value : List.of("123456789", "\ud83e\udde9".repeat(9)))
            cases.add(invalid("description-nine-" + value.length(), "description", PAD + value + PAD, "INVALID_LENGTH"));
        cases.add(invalid("name-whitespace", "name", PAD, "REQUIRED"));
        cases.add(invalid("description-whitespace", "description", PAD, "REQUIRED"));
        for (var value : List.of("0", "-0.01")) cases.add(invalid("price-" + value, "price", value, "NOT_POSITIVE"));
        for (var value : List.of("100000000000000000.00", "1.001")) cases.add(invalid("price-" + value, "price", value, "UNREPRESENTABLE_MONEY"));
        for (var value : List.of("1e2", "1,00", "+1.00", " 1.00 ", "NaN")) cases.add(invalid("price-format-" + cases.size(), "price", value, "INVALID_FORMAT"));
        for (var image : List.of("/image.jpg", "ftp://image-t026.invalid/a", "http://", "https://image-t026.invalid/a b",
                "https://[broken/path", "https:opaque", "http:///missing-host", " \t"))
            cases.add(invalid("invalid-uri-" + cases.size(), "image", image, "INVALID_IMAGE_URL"));
        cases.add(invalid("empty-image", "image", "", "REQUIRED"));
        for (var field : FIELDS) cases.add(new RuleCase("missing-" + field, Map.of(), Set.of(field), Categories.EXISTING,
                null, List.of(new Diagnosis(field, "REQUIRED"))));
        cases.add(categoryCase("empty-selection", Categories.EMPTY, "categoryIds", "EMPTY_CATEGORIES"));
        cases.add(categoryCase("mixed-existing-and-absent", Categories.MIXED, "categoryIds[1]", "CATEGORY_NOT_FOUND"));
        cases.add(categoryCase("only-absent", Categories.ABSENT, "categoryIds[0]", "CATEGORY_NOT_FOUND"));
        cases.add(categoryCase("no-categories-empty-selection", Categories.NO_CATEGORIES_EMPTY, "categoryIds", "EMPTY_CATEGORIES"));
        cases.add(categoryCase("no-categories-positive-reference", Categories.NO_CATEGORIES_REFERENCE, "categoryIds[0]", "CATEGORY_NOT_FOUND"));
        return List.copyOf(cases);
    }

    private static RuleCase valid(String label, Map<String, String> overrides, Output output) {
        return new RuleCase(label, overrides, Set.of(), Categories.EXISTING, output, List.of());
    }
    private static RuleCase invalid(String label, String field, String value, String code) {
        return new RuleCase(label, Map.of(field, value), Set.of(), Categories.EXISTING, null, List.of(new Diagnosis(field, code)));
    }
    private static RuleCase categoryCase(String label, Categories categories, String field, String code) {
        return new RuleCase(label, Map.of(), Set.of(), categories, null, List.of(new Diagnosis(field, code)));
    }

    @ParameterizedTest(name = "T026 security context={0}, client={1}")
    @MethodSource("securityCases")
    void shouldPreserveSecurityBeforeInvalidBusinessData(String externalContext, boolean client) throws Exception {
        String accessToken = null;
        if (client) {
            var account = fixture.createAccount("T026 Client", "Disposable-T026", Set.of(UserRole.CLIENT));
            assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
            accessToken = fixture.login(account);
        }
        int status = client ? 403 : 401;
        var observed = perform(externalContext, invalid("security-invalid-name", "name", "ab", "INVALID_LENGTH"), accessToken, status);
        assertThat(observed.result().getResponse().getStatus()).isEqualTo(status);
        var actual = json(observed.result());
        assertEnvelope(actual, status, client ? "Forbidden" : "Unauthorized",
                client ? "Product maintenance not permitted" : "Authentication required or invalid", externalContext);
        assertThat(actual.get("errors").isArray()).isTrue(); assertThat(actual.get("errors")).isEmpty();
        assertThat(observed.after()).isEqualTo(observed.before());
        assertThat(observed.result().getHandler()).isNull();
        assertThat(observed.result().getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(client ? null : "Bearer");
        assertThat(observed.result().getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(sql.events()).noneMatch(event -> isDml(event.sql()));
    }

    static Stream<Arguments> securityCases() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(false, true).map(client -> Arguments.of(context, client)));
    }

    private Observation perform(String externalContext, RuleCase input, String accessToken, int expectedStatus) throws Exception {
        if (input.categories() == Categories.NO_CATEGORIES_EMPTY || input.categories() == Categories.NO_CATEGORIES_REFERENCE) {
            fixture.prepareWithoutCategories(); selected = List.of(); absentCategory = 1;
            assertThat(database.snapshot().categories()).isEmpty();
        } else {
            var options = fixture.prepareCategorySelection(); selected = options.sameName();
            absentCategory = Math.addExact(options.unused().id(), 1L);
            var legacy = fixture.createProduct("Produto preexistente", "Legado", "imagem-legada", new BigDecimal("0.00"));
            fixture.associate(legacy.id(), options.unused().id());
            assertThat(database.snapshot().categories()).hasSize(3);
        }
        assertThat(database.snapshot().categories()).noneMatch(category -> category.id() == absentCategory);
        var body = mapper.createObjectNode().put("name", DEFAULT_NAME).put("description", DEFAULT_DESCRIPTION)
                .put("price", "10.00").put("image", DEFAULT_IMAGE);
        input.overrides().forEach(body::put);
        var ids = body.putArray("categoryIds");
        switch (input.categories()) {
            case EXISTING -> selected.forEach(category -> ids.add(category.id()));
            case MIXED -> { ids.add(selected.getFirst().id()); ids.add(absentCategory); }
            case ABSENT, NO_CATEGORIES_REFERENCE -> ids.add(absentCategory);
            case EMPTY, NO_CATEGORIES_EMPTY -> { }
        }
        input.omitted().forEach(body::remove);
        var request = post(externalContext + ROUTE).contextPath(externalContext).servletPath(ROUTE)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        if (accessToken != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        assertNoCallerTransaction();
        var before = database.snapshot(); var accounts = database.accountIds(); sql.clear();
        MvcResult result; AdminProductDatabaseProbe.Snapshot after;
        NETWORK.start();
        try { result = mvc.perform(request).andReturn(); }
        finally {
            NETWORK.stop(); after = database.snapshot();
            after.products().stream().filter(product -> before.products().stream().noneMatch(old -> old.id() == product.id()))
                    .forEach(product -> requestOwnedProducts.add(product.id()));
        }
        assertNoCallerTransaction();
        assertThat(after.categories()).isEqualTo(before.categories()); assertThat(after.orders()).isEqualTo(before.orders());
        assertThat(after.items()).isEqualTo(before.items()); assertThat(database.accountIds()).isEqualTo(accounts);
        if (accessToken == null) assertThat(sql.events()).isEmpty(); else assertThat(sql.events()).isNotEmpty();
        assertThat(NETWORK.events()).as("no JDK DNS/connect attempt during the real request").isEmpty();
        System.out.printf("T026_HTTP case=%s context=%s expected=%d actual=%d cause=%s snapshotEqual=%s hibernateDml=%d networkAttempts=0%n",
                input.label(), externalContext, expectedStatus,
                result.getResponse().getStatus(), result.getResolvedException() == null ? "NONE" : result.getResolvedException().getClass().getSimpleName(),
                after.equals(before), sql.events().stream().filter(event -> isDml(event.sql())).count());
        return new Observation(result, before, after);
    }

    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
    private void assertEnvelope(JsonNode actual, int status, String error, String message, String externalContext) {
        var fields = new HashSet<String>(); actual.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(actual.get("status").asInt()).isEqualTo(status); assertThat(actual.get("error").asText()).isEqualTo(error);
        assertThat(actual.get("message").asText()).isEqualTo(message); assertThat(actual.get("path").asText()).isEqualTo(externalContext + ROUTE);
    }
    private static boolean isDml(String statement) {
        var normalized = statement.stripLeading().toLowerCase(Locale.ROOT);
        return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(normalized::startsWith);
    }
    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmptyDatabase() {
        var snapshot = database.snapshot(); assertThat(snapshot.products()).isEmpty(); assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty(); assertThat(snapshot.orders()).isEmpty(); assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    enum Categories { EXISTING, EMPTY, MIXED, ABSENT, NO_CATEGORIES_EMPTY, NO_CATEGORIES_REFERENCE }
    private record Output(String name, String description, String price, String image) { }
    private record Diagnosis(String field, String code) { }
    private record RuleCase(String label, Map<String, String> overrides, Set<String> omitted, Categories categories,
                            Output output, List<Diagnosis> errors) {
        private RuleCase { overrides = Map.copyOf(overrides); omitted = Set.copyOf(omitted); errors = List.copyOf(errors); }
        @Override public String toString() { return label; }
    }
    private record Observation(MvcResult result, AdminProductDatabaseProbe.Snapshot before, AdminProductDatabaseProbe.Snapshot after) { }

    /** Scoped to the request interval; counters cover JDK permission hooks, not native libraries or later async work. */
    private static final class NetworkProbe extends SecurityManager {
        static final String MESSAGE = "T026 network access is forbidden during observation";
        private volatile boolean observing;
        private final List<String> events = new CopyOnWriteArrayList<>();
        void start() { events.clear(); observing = true; }
        void stop() { observing = false; }
        List<String> events() { return List.copyOf(events); }
        @Override public void checkPermission(Permission permission) { }
        @Override public void checkPermission(Permission permission, Object context) { }
        @Override public void checkConnect(String host, int port) {
            if (observing) { events.add(port == -1 ? "DNS" : "CONNECT"); throw new SecurityException(MESSAGE); }
        }
        @Override public void checkConnect(String host, int port, Object context) { checkConnect(host, port); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation validationSqlObservation() { return new AdminProductDatabaseProbe.SqlObservation(); }
        @Bean HibernatePropertiesCustomizer validationSqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
