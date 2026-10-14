package com.devsuperior.dscommerce.dto;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T025 HTTP input contract: raw JSON, original filters/application, no future production input classes. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductInputContractTest.SqlConfig.class)
class AdminProductInputContractTest {
    private static final String ROUTE = "/admin/products";
    private static final String PRIVATE_FIELD = "T025_PRIVATE_FIELD";
    private static final String PRIVATE_VALUE = "T025_PRIVATE_VALUE";
    private static final List<String> FIELDS = List.of("name", "description", "price", "image", "categoryIds");
    private static final Map<String, String> VALID_TOKENS = Map.of("name", "\"Fone Azul\"",
            "description", "\"Descricao completa\"", "price", "\"10.00\"",
            "image", "\"https://example.invalid/t025.jpg\"", "categoryIds", "[$A]");

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String adminToken;
    private long categoryId;
    private final Set<Long> requestOwnedProducts = new LinkedHashSet<>();

    @BeforeEach
    void prepareCommittedIsolatedDataAndRealAdminAuthentication() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        requestOwnedProducts.clear();
        fixture = new AdminProductTestFixture(context);
        var admin = fixture.createAccount("T025 Admin", "Disposable-T025", Set.of(UserRole.ADMIN));
        adminToken = fixture.login(admin);
        assertThat(context.getBean(JwtDecoder.class).decode(adminToken).getSubject()).isEqualTo(Long.toString(admin.id()));
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        var category = fixture.createCategory("Categoria existente T025");
        categoryId = category.id();
        var legacy = fixture.createProduct("Produto preexistente", "Legado", "imagem-legada", new BigDecimal("0.00"));
        fixture.associate(legacy.id(), categoryId);
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).hasSize(1);
        assertThat(snapshot.categories()).containsExactly(new AdminProductDatabaseProbe.CategoryRow(categoryId, category.name()));
        assertThat(snapshot.associations()).containsExactly(new AdminProductTestFixture.ProductCategory(legacy.id(), categoryId));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        sql.clear();
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        assertNoCallerTransaction();
        // An erroneous insertion must not leak into the next case; capture only this request's new IDs.
        if (!requestOwnedProducts.isEmpty()) {
            try (var connection = database.openConnection()) {
                connection.setAutoCommit(false);
                try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?");
                     var products = connection.prepareStatement("delete from tb_product where id = ?")) {
                    for (long id : requestOwnedProducts) {
                        pairs.setLong(1, id);
                        pairs.executeUpdate();
                        products.setLong(1, id);
                        products.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
            requestOwnedProducts.clear();
        }
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
        sql.clear();
    }

    @ParameterizedTest(name = "T025 raw context={0}, case={1}")
    @MethodSource("rawInputs")
    void shouldRejectRawInvalidInputWithCompleteSafeFieldDiagnostics(String externalContext, InputCase input) throws Exception {
        var result = observe(request(externalContext, input.body())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken), input.label(), externalContext);
        assertThat(result.getResponse().getStatus()).as("invalid administrative JSON must return HTTP 400").isEqualTo(400);
        var actual = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEnvelope(result, actual, 400, "Bad Request", "Invalid product maintenance data", externalContext);
        var diagnoses = new ArrayList<Diagnosis>();
        var triples = new HashSet<List<String>>();
        assertThat(actual.get("errors").isArray()).isTrue();
        assertThat(actual.get("errors")).isNotEmpty();
        actual.get("errors").forEach(error -> {
            var fields = new HashSet<String>();
            error.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("field", "code", "message");
            assertThat(error.get("field").isTextual()).isTrue();
            assertThat(error.get("code").isTextual()).isTrue();
            assertThat(error.get("message").isTextual()).isTrue();
            assertThat(error.get("message").asText()).isNotBlank();
            assertThat(triples.add(List.of(error.get("field").asText(), error.get("code").asText(),
                    error.get("message").asText()))).as("deduplicate the complete field/code/message triple").isTrue();
            diagnoses.add(new Diagnosis(error.get("field").asText(), error.get("code").asText()));
        });
        assertThat(diagnoses).as("all independent structural defects, in contracted field/index order")
                .containsExactlyElementsOf(input.expected());
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T025_DIAGNOSED case=%s context=%s expectedFields=%d actualFields=%d%n",
                input.label(), externalContext, input.expected().size(), diagnoses.size());
    }

    static Stream<Arguments> rawInputs() {
        return Stream.of("", "/api").flatMap(context -> invalidInputs().stream().map(input -> Arguments.of(context, input)));
    }

    private static List<InputCase> invalidInputs() {
        var inputs = new ArrayList<InputCase>();
        inputs.add(new InputCase("all-five-wrong-types", raw(Map.of("name", "23", "description", "false", "price", "10.00",
                "image", "[]", "categoryIds", "{}"), Set.of()), diagnoses(FIELDS, "INVALID_TYPE")));
        for (var field : List.of("name", "description", "image", "categoryIds")) {
            inputs.add(one(field + "-wrong-type", field, field.equals("categoryIds") ? "$A" : "true", "INVALID_TYPE"));
        }
        for (var token : List.of("10", "10.00", "1e1", "true", "[]", "{}")) {
            inputs.add(one("price-token-" + token, "price", token, "INVALID_TYPE"));
        }
        inputs.add(new InputCase("extra-property", append(raw(Map.of(), Set.of()), PRIVATE_FIELD, "\"" + PRIVATE_VALUE + "\""),
                List.of(new Diagnosis("body", "UNKNOWN_FIELD"))));
        inputs.add(new InputCase("extra-keeps-independent-types", append(raw(Map.of("name", "23", "image", "false"), Set.of()),
                PRIVATE_FIELD, "\"" + PRIVATE_VALUE + "\""), List.of(new Diagnosis("body", "UNKNOWN_FIELD"),
                new Diagnosis("name", "INVALID_TYPE"), new Diagnosis("image", "INVALID_TYPE"))));
        for (var field : FIELDS) {
            inputs.add(new InputCase("duplicate-" + field, append(raw(Map.of(), Set.of()), field, "null"),
                    List.of(new Diagnosis(field, "DUPLICATE_FIELD"))));
        }
        inputs.add(new InputCase("duplicate-keeps-independent-types", append(raw(Map.of("description", "false", "price", "[]",
                "categoryIds", "[$A,true]"), Set.of()), "name", "null"), List.of(new Diagnosis("name", "DUPLICATE_FIELD"),
                new Diagnosis("description", "INVALID_TYPE"), new Diagnosis("price", "INVALID_TYPE"),
                new Diagnosis("categoryIds[1]", "INVALID_TYPE"))));
        inputs.add(new InputCase("partial-array-all-uncoerced-types", raw(Map.of("categoryIds", "[$A,\"$A\",true,$A.0,{},[],$A]"), Set.of()),
                diagnoses(List.of("categoryIds[1]", "categoryIds[2]", "categoryIds[3]", "categoryIds[4]", "categoryIds[5]"), "INVALID_TYPE")));
        inputs.add(one("integer-id-exponent-not-coerced", "categoryIds", "[$A,$Ae0]", "categoryIds[1]", "INVALID_TYPE"));
        inputs.add(one("integer-id-overflows-long", "categoryIds", "[$A,9223372036854775808]", "categoryIds[1]", "INVALID_FORMAT"));
        for (var field : FIELDS) {
            inputs.add(one("null-" + field, field, "null", "REQUIRED"));
            inputs.add(new InputCase("absent-" + field, raw(Map.of(), Set.of(field)), List.of(new Diagnosis(field, "REQUIRED"))));
        }
        inputs.add(new InputCase("examinable-empty-object", "{}", diagnoses(FIELDS, "REQUIRED")));
        inputs.add(new InputCase("examinable-all-null", raw(FIELDS.stream().collect(Collectors.toMap(field -> field, field -> "null")),
                Set.of()), diagnoses(FIELDS, "REQUIRED")));
        for (var body : List.of("[]", "\"" + PRIVATE_VALUE + "\"", "23", "true", "null")) {
            inputs.add(bodyOnly("non-object-root-" + inputs.size(), body));
        }
        inputs.add(bodyOnly("absent-body", null));
        inputs.add(bodyOnly("empty-body", ""));
        inputs.add(bodyOnly("whitespace-body", " \t\r\n "));
        inputs.add(bodyOnly("unclosed-object", "{\"name\":\"Fone Azul\""));
        inputs.add(bodyOnly("trailing-comma", "{\"name\":\"Fone Azul\",}"));
        inputs.add(bodyOnly("broken-syntax", "{\"name\" \"Fone Azul\"}"));
        inputs.add(bodyOnly("trailing-second-object", raw(Map.of(), Set.of()) + " {}"));
        inputs.add(bodyOnly("trailing-scalar", raw(Map.of(), Set.of()) + " true"));
        inputs.add(bodyOnly("trailing-garbage", raw(Map.of(), Set.of()) + " " + PRIVATE_VALUE));
        return List.copyOf(inputs);
    }

    private static InputCase one(String label, String field, String token, String code) {
        return one(label, field, token, field, code);
    }

    private static InputCase one(String label, String field, String token, String diagnosticField, String code) {
        return new InputCase(label, raw(Map.of(field, token), Set.of()), List.of(new Diagnosis(diagnosticField, code)));
    }

    private static InputCase bodyOnly(String label, String body) {
        return new InputCase(label, body, List.of(new Diagnosis("body", "INVALID_BODY")));
    }

    private static List<Diagnosis> diagnoses(List<String> fields, String code) {
        return fields.stream().map(field -> new Diagnosis(field, code)).toList();
    }

    private static String raw(Map<String, String> overrides, Set<String> absent) {
        return FIELDS.stream().filter(field -> !absent.contains(field))
                .map(field -> "\"" + field + "\":" + overrides.getOrDefault(field, VALID_TOKENS.get(field)))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String append(String body, String field, String token) {
        return body.substring(0, body.length() - 1) + ",\"" + field + "\":" + token + "}";
    }

    @ParameterizedTest(name = "T025 malformed security context={0}, client={1}")
    @MethodSource("securityCases")
    void shouldPreserveSecurityBeforeMalformedBodyAndMvc(String externalContext, boolean client) throws Exception {
        var request = request(externalContext, "{\"" + PRIVATE_FIELD + "\":");
        if (client) {
            var account = fixture.createAccount("T025 Client", "Disposable-T025", Set.of(UserRole.CLIENT));
            assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(account));
        }
        var result = observe(request, client ? "security-client" : "security-anonymous", externalContext);
        int status = client ? 403 : 401;
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        var actual = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertEnvelope(result, actual, status, client ? "Forbidden" : "Unauthorized",
                client ? "Product maintenance not permitted" : "Authentication required or invalid", externalContext);
        assertThat(actual.get("errors").isArray()).isTrue();
        assertThat(actual.get("errors")).isEmpty();
        assertThat(result.getHandler()).isNull();
        assertThat(result.getResolvedException()).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(client ? null : "Bearer");
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
    }

    static Stream<Arguments> securityCases() {
        return Stream.of("", "/api").flatMap(context -> Stream.of(false, true).map(client -> Arguments.of(context, client)));
    }

    private MockHttpServletRequestBuilder request(String externalContext, String body) {
        var request = post(externalContext + ROUTE).contextPath(externalContext).servletPath(ROUTE)
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
        // Do not parse/reserialize: duplicate keys, malformed tokens and residual bytes must reach HTTP intact.
        if (body != null) request.content(body.replace("$A", Long.toString(categoryId)));
        return request;
    }

    private MvcResult observe(MockHttpServletRequestBuilder request, String label, String externalContext) throws Exception {
        assertNoCallerTransaction();
        var before = database.snapshot();
        var accounts = database.accountIds();
        sql.clear();
        MvcResult result;
        AdminProductDatabaseProbe.Snapshot after;
        try {
            result = mvc.perform(request).andReturn();
        } finally {
            after = database.snapshot();
            after.products().stream().filter(product -> before.products().stream().noneMatch(old -> old.id() == product.id()))
                    .forEach(product -> requestOwnedProducts.add(product.id()));
        }
        assertNoCallerTransaction();
        assertThat(after).as("independent five-table snapshot after response and before cleanup").isEqualTo(before);
        assertThat(database.accountIds()).isEqualTo(accounts);
        if (result.getRequest().getHeader(HttpHeaders.AUTHORIZATION) != null) assertThat(sql.events()).isNotEmpty();
        else assertThat(sql.events()).isEmpty();
        assertThat(sql.events()).noneMatch(event -> isDml(event.sql()));
        System.out.printf("T025_HTTP case=%s context=%s actual=%d cause=%s snapshotEqual=true hibernateDml=0%n", label,
                externalContext, result.getResponse().getStatus(),
                result.getResolvedException() == null ? "NONE" : result.getResolvedException().getClass().getSimpleName());
        return result;
    }

    private void assertEnvelope(MvcResult result, com.fasterxml.jackson.databind.JsonNode actual, int status,
                                String error, String message, String externalContext) throws java.io.UnsupportedEncodingException {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var fields = new HashSet<String>();
        actual.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(actual.get("status").asInt()).isEqualTo(status);
        assertThat(actual.get("error").asText()).isEqualTo(error);
        assertThat(actual.get("message").asText()).isEqualTo(message);
        assertThat(actual.get("path").asText()).isEqualTo(externalContext + ROUTE);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PRIVATE_FIELD, PRIVATE_VALUE,
                "Fone Azul", "Descricao completa", "https://example.invalid/t025.jpg", "stackTrace", "password", "SQLException");
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
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).isEmpty();
        assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty();
        assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    private record Diagnosis(String field, String code) { }
    private record InputCase(String label, String body, List<Diagnosis> expected) {
        private InputCase { expected = List.copyOf(expected); }
        @Override public String toString() { return label; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation inputContractSqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }
        @Bean HibernatePropertiesCustomizer inputContractSqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
