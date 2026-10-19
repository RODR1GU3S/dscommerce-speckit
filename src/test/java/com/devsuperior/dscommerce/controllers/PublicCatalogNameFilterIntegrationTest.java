package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/** T003 preparation and T004 pre-change HTTP characterization in isolated real servlet contexts. */
class PublicCatalogNameFilterIntegrationTest {

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false",
                    "feature006.catalog-test-context=root"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = AdminProductTestConfig.class)
    @Import(SqlProbeConfig.class)
    class RootContext extends ContextControl {
        @Override String expectedContextPath() { return ""; }
        @Override String expectedIdentity() { return "root"; }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(classes = DscommerceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.servlet.context-path=/api", "spring.jpa.open-in-view=false",
                    "feature006.catalog-test-context=api"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = AdminProductTestConfig.class)
    @Import(SqlProbeConfig.class)
    class ApiContext extends ContextControl {
        @Override String expectedContextPath() { return "/api"; }
        @Override String expectedIdentity() { return "api"; }
    }

    abstract static class ContextControl {
        @Autowired WebApplicationContext context;
        @Autowired ObjectMapper mapper;
        @Autowired TestRestTemplate http;
        @Autowired AdminProductDatabaseProbe.SqlObservation sql;
        @Autowired NameRequestObservation nameRequests;
        @LocalServerPort int port;

        abstract String expectedContextPath();
        abstract String expectedIdentity();

        @Test
        void preparesAnIsolatedCommittedCatalogWithoutNameFilter() throws Exception {
            assertNoCallerTransaction();
            var environment = context.getEnvironment();
            assertThat(environment.getActiveProfiles()).contains("test");
            assertThat(environment.getProperty("feature006.catalog-test-context")).isEqualTo(expectedIdentity());
            assertThat(context.getServletContext()).isNotNull();
            assertThat(context.getServletContext().getContextPath()).isEqualTo(expectedContextPath());
            assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
            assertThat(context.containsBean("openEntityManagerInViewInterceptor")).isFalse();
            assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.import_files")).isEmpty();
            assertThat(context.getBean(FilterChainProxy.class).getFilterChains()).isNotEmpty();
            assertThat(context.getBeansOfType(AdminProductDatabaseProbe.SqlObservation.class))
                    .containsOnlyKeys("publicCatalogNameSqlObservation");

            var databaseUrl = environment.getRequiredProperty("spring.datasource.url");
            var database = new AdminProductDatabaseProbe(databaseUrl);
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context);
                 var otherOwner = new AdminProductTestFixture(context)) {
                var alpha = fixture.createProduct("T003 Alpha", "Fixture Alpha", "alpha.jpg", new BigDecimal("10.00"));
                var beta = fixture.createProduct("T003 Beta", "Fixture Beta", "beta.jpg", new BigDecimal("20.00"));
                var category = fixture.createCategory("T003 Category");
                fixture.associate(alpha.id(), category.id());
                var other = otherOwner.createProduct("T003 Other owner", "Other fixture", "other.jpg",
                        new BigDecimal("30.00"));

                // Independent connections and real HTTP must see the setup already committed.
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactly(alpha.id(), beta.id(), other.id());
                assertThat(before.categories()).extracting(AdminProductDatabaseProbe.CategoryRow::id)
                        .containsExactly(category.id());
                assertThat(before.associations()).containsExactly(
                        new AdminProductTestFixture.ProductCategory(alpha.id(), category.id()));
                assertNoCallerTransaction();
                sql.clear();
                var headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?page=0&size=12");
                var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                var content = mapper.readTree(response.getBody()).get("content");
                var ids = new ArrayList<Long>();
                var names = new ArrayList<String>();
                content.forEach(item -> { ids.add(item.get("id").asLong()); names.add(item.get("name").asText()); });
                assertThat(ids).containsExactly(alpha.id(), beta.id(), other.id());
                assertThat(names).containsExactly(alpha.name(), beta.name(), other.name());
                assertThat(database.snapshot()).isEqualTo(before);
                assertNoCallerTransaction();
                assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                    assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                    assertThat(event.transactionActive()).isTrue();
                });

                // Cleaning one owner cannot erase rows belonging to the other fixture.
                fixture.cleanUp();
                var remaining = database.snapshot();
                assertThat(remaining.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactly(other.id());
                assertThat(remaining.categories()).isEmpty();
                assertThat(remaining.associations()).isEmpty();
            } finally {
                sql.clear();
            }
            assertEmptyDatabase(database);
            assertNoCallerTransaction();
            System.out.printf("T003_CONTEXT identity=%s path=%s database=%s committed=true ownershipCleanup=true "
                            + "snapshotEqual=true hibernateDml=0 callerTransaction=false%n",
                    expectedIdentity(), expectedContextPath(), databaseUrl);
        }

        @Test
        @Tag("feature006-t004")
        void characterizesExistingPublicPaginationHttp() throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                // Both names contain T004: this control does not freeze the old ignored-name behavior.
                var products = List.of(
                        fixture.createProduct("T004 Alpha", "Fixture Alpha", "alpha.jpg", new BigDecimal("10.00")),
                        fixture.createProduct("T004 Beta", "Fixture Beta", "beta.jpg", new BigDecimal("20.00")));
                var before = database.snapshot();
                Map<String, JsonNode> controls = new LinkedHashMap<>();
                sql.clear();
                for (var input : paginationCases()) {
                    for (boolean withName : List.of(false, true)) {
                        for (boolean invalidBearer : List.of(false, true)) {
                            var query = input.query() + (withName
                                    ? (input.query().isEmpty() ? "" : "&") + "name=T004" : "");
                            var uri = URI.create("http://localhost:" + port + expectedContextPath()
                                    + "/products?" + query);
                            var headers = new HttpHeaders();
                            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                            if (invalidBearer) headers.setBearerAuth("deliberately-invalid");
                            assertNoCallerTransaction();
                            var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                            var body = response.getBody() == null ? mapper.getNodeFactory().nullNode()
                                    : mapper.readTree(response.getBody());
                            var observed = mapper.createObjectNode().put("context", expectedIdentity())
                                    .put("case", input.label()).put("withName", withName)
                                    .put("invalidBearer", invalidBearer).put("query", query)
                                    .put("status", response.getStatusCode().value());
                            observed.set("headers", mapper.valueToTree(response.getHeaders()));
                            observed.set("body", body);
                            System.out.println("T004_HTTP " + observed);

                            assertThat(response.getStatusCode().value()).as(input.label()).isEqualTo(input.status());
                            assertThat(response.getHeaders().getContentType()).isNotNull()
                                    .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                            assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isNull();
                            assertThat(body.isObject()).isTrue();
                            if (input.status() == 200) {
                                var expected = mapper.createObjectNode().put("page", input.page()).put("size", input.size())
                                        .put("totalElements", products.size())
                                        .put("totalPages", (products.size() + input.size() - 1) / input.size());
                                var content = expected.putArray("content");
                                products.stream().skip((long) input.page() * input.size()).limit(input.size())
                                        .forEach(product -> content.addObject().put("id", product.id())
                                                .put("name", product.name()).put("image", product.image())
                                                .put("price", product.price()));
                                assertThat(body).isEqualTo(mapper.readTree(expected.toString()));
                            } else if (input.boundMessage() != null) {
                                // Check ErrorResponse directly; the OpenAPI 400 anyOf is deliberately permissive.
                                var expected = mapper.createObjectNode().put("status", 400).put("error", "Bad Request")
                                        .put("message", input.boundMessage()).put("path", expectedContextPath() + "/products");
                                assertThat(body).isEqualTo(expected);
                            }
                            // Native conversion bodies are observed, not forced into the Bean Validation DTO.
                            var normalized = mapper.createObjectNode().put("status", response.getStatusCode().value());
                            ObjectNode stableBody = body.deepCopy();
                            if (stableBody.has("timestamp")) {
                                assertThat(stableBody.get("timestamp").isTextual()).isTrue();
                                OffsetDateTime.parse(stableBody.get("timestamp").asText());
                                stableBody.put("timestamp", "<ISO-8601 timestamp>");
                            }
                            normalized.set("body", stableBody);
                            var stableHeaders = normalized.putObject("headers");
                            for (var name : List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.CACHE_CONTROL, HttpHeaders.PRAGMA,
                                    HttpHeaders.EXPIRES, HttpHeaders.VARY, HttpHeaders.LOCATION, HttpHeaders.WWW_AUTHENTICATE,
                                    "X-Content-Type-Options", "X-XSS-Protection", "X-Frame-Options")) {
                                if (response.getHeaders().containsKey(name)) {
                                    stableHeaders.set(name, mapper.valueToTree(response.getHeaders().get(name)));
                                }
                            }
                            var date = response.getHeaders().getFirst(HttpHeaders.DATE);
                            if (date != null) ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME);
                            var control = controls.putIfAbsent(input.label(), normalized);
                            if (control != null) assertThat(normalized).as(input.label() + " name/token variants").isEqualTo(control);
                        }
                    }
                }
                assertThat(database.snapshot()).isEqualTo(before);
                assertThat(sql.events()).isNotEmpty().allSatisfy(event ->
                        assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select "));
                assertNoCallerTransaction();
            } finally {
                sql.clear();
            }
            assertEmptyDatabase(database);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.PublicCatalogNameFilterIntegrationTest#nameScenarios")
        @Tag("feature006-t005")
        void matchesNameSubstringCaseAccentsAndLiterals(NameScenario scenario) throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                var names = switch (scenario) {
                    case SUBSTRING, CASE -> List.of("Fone Azul", "Telefone Preto", "Cabo");
                    case ACCENTS -> List.of("Café Especial", "Cafe Claro");
                    case PERCENT -> List.of("Oferta 10%", "Oferta 100");
                    case UNDERSCORE -> List.of("Kit_A", "KitXA");
                };
                var products = new ArrayList<ProductData>();
                for (var name : names) {
                    products.add(fixture.createProduct(name, "Fixture T005", "t005.jpg", new BigDecimal("10.00")));
                }
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactlyElementsOf(products.stream().map(ProductData::id).toList());
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactlyElementsOf(names);
                var expectations = switch (scenario) {
                    case SUBSTRING -> List.of(new NameExpectation("fone", List.of(0, 1)));
                    case CASE -> List.of(new NameExpectation("fone", List.of(0, 1)),
                            new NameExpectation("FONE", List.of(0, 1)), new NameExpectation("FoNe", List.of(0, 1)));
                    case ACCENTS -> List.of(new NameExpectation("CAFÉ", List.of(0)), new NameExpectation("cafe", List.of(1)));
                    case PERCENT -> List.of(new NameExpectation("10%", List.of(0)));
                    case UNDERSCORE -> List.of(new NameExpectation("kit_", List.of(0)));
                };
                var assertions = new ArrayList<Executable>();
                var actualPages = new ArrayList<JsonNode>();
                for (var expectation : expectations) {
                    var expectedProducts = expectation.indices().stream().map(products::get).toList();
                    var expected = mapper.createObjectNode().put("page", 0).put("size", 12)
                            .put("totalElements", expectedProducts.size()).put("totalPages", 1);
                    var expectedContent = expected.putArray("content");
                    for (var product : expectedProducts) expectedContent.add(nameItem(product));
                    var canonicalExpected = mapper.readTree(expected.toString());
                    var requestId = UUID.randomUUID().toString();
                    var query = "name=" + URLEncoder.encode(expectation.filter(), StandardCharsets.UTF_8) + "&page=0&size=12";
                    var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                    var headers = new HttpHeaders();
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("X-Feature006-Name-Observation", requestId);
                    sql.clear();
                    assertNoCallerTransaction();
                    var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    var decoded = nameRequests.take(requestId);
                    // These guards distinguish setup/transport failures from the missing name selection.
                    assertThat(decoded).as("passive servlet observation").isNotNull();
                    assertThat(decoded.query()).isEqualTo(query);
                    assertThat(decoded.name()).isEqualTo(expectation.filter());
                    assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                    assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType()).isNotNull()
                            .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                    var actual = mapper.readTree(response.getBody());
                    assertThat(actual.get("content").isArray()).isTrue();
                    assertThat(actual.get("page").asInt()).isZero();
                    assertThat(actual.get("size").asInt()).isEqualTo(12);
                    assertThat(actual.get("totalElements").isIntegralNumber()).isTrue();
                    assertThat(actual.get("totalPages").isIntegralNumber()).isTrue();
                    var actualIds = new ArrayList<Long>();
                    for (var item : actual.get("content")) {
                        long id = item.get("id").asLong();
                        var prepared = products.stream().filter(product -> product.id() == id).findFirst().orElseThrow();
                        assertThat(item).isEqualTo(mapper.readTree(nameItem(prepared).toString()));
                        actualIds.add(id);
                    }
                    assertThat(actualIds).doesNotHaveDuplicates();
                    assertThat(database.snapshot()).isEqualTo(before);
                    assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                        assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                        assertThat(event.transactionActive()).isTrue();
                    });
                    assertNoCallerTransaction();
                    var observation = mapper.createObjectNode().put("context", expectedIdentity())
                            .put("scenario", scenario.name()).put("filter", expectation.filter())
                            .put("rawQuery", decoded.query()).put("decodedName", decoded.name())
                            .put("path", decoded.uri()).put("status", response.getStatusCode().value())
                            .put("guardsPassed", true);
                    observation.set("expected", canonicalExpected);
                    observation.set("actual", actual);
                    System.out.println("T005_HTTP " + observation);
                    actualPages.add(actual);
                    assertions.add(() -> assertThat(actual).as(scenario + " filter=" + expectation.filter()
                            + " literal selection, stored-name/id order and filtered totals").isEqualTo(canonicalExpected));
                }
                if (scenario == NameScenario.CASE) {
                    boolean equal = actualPages.stream().allMatch(actualPages.getFirst()::equals);
                    System.out.printf("T005_CASE_EQUIVALENCE context=%s requests=3 equal=%s%n", expectedIdentity(), equal);
                    assertions.add(() -> assertThat(actualPages).containsOnly(actualPages.getFirst()));
                }
                // Cleanup and preparation checks complete even when the expected functional RED is raised below.
                fixture.cleanUp();
                assertEmptyDatabase(database);
                assertAll("T005 " + scenario, assertions);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.PublicCatalogNameFilterIntegrationTest#lengthScenarios")
        @Tag("feature006-t006")
        void acceptsShortAndLongNameFilters(LengthScenario scenario) throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                var names = scenario == LengthScenario.SHORT
                        ? List.of("Fone Azul", "Cabo") : List.of("a".repeat(80), "a".repeat(81));
                var filter = scenario == LengthScenario.SHORT ? "f" : "A".repeat(81);
                var products = new ArrayList<ProductData>();
                for (var name : names) {
                    // Committed JDBC setup accepts legacy data without invoking or relaxing write validation.
                    products.add(fixture.createProduct(name, "Fixture T006", "t006.jpg", new BigDecimal("10.00")));
                }
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactlyElementsOf(products.stream().map(ProductData::id).toList());
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactlyElementsOf(names);
                // The explicit manifest, not a production query, identifies the single match.
                var match = products.get(scenario == LengthScenario.SHORT ? 0 : 1);
                var expected = mapper.createObjectNode().put("page", 0).put("size", 12)
                        .put("totalElements", 1).put("totalPages", 1);
                expected.putArray("content").add(nameItem(match));
                var canonicalExpected = mapper.readTree(expected.toString());
                var requestId = UUID.randomUUID().toString();
                var query = "name=" + URLEncoder.encode(filter, StandardCharsets.UTF_8) + "&page=0&size=12";
                var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                var headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("X-Feature006-Name-Observation", requestId);
                sql.clear();
                assertNoCallerTransaction();
                var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                var decoded = nameRequests.take(requestId);
                assertThat(decoded).as("passive servlet observation").isNotNull();
                assertThat(decoded.query()).isEqualTo(query);
                assertThat(decoded.name()).isEqualTo(filter);
                assertThat(decoded.name()).hasSize(scenario == LengthScenario.SHORT ? 1 : 81);
                assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                // Length acceptance already exists; selection below remains a separate functional expectation.
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getContentType()).isNotNull()
                        .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                var actual = mapper.readTree(response.getBody());
                assertThat(actual.get("content").isArray()).isTrue();
                var actualIds = new ArrayList<Long>();
                for (var item : actual.get("content")) {
                    long id = item.get("id").asLong();
                    var prepared = products.stream().filter(product -> product.id() == id).findFirst().orElseThrow();
                    assertThat(item).isEqualTo(mapper.readTree(nameItem(prepared).toString()));
                    actualIds.add(id);
                }
                assertThat(actualIds).doesNotHaveDuplicates();
                assertThat(database.snapshot()).isEqualTo(before);
                assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                    assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                    assertThat(event.transactionActive()).isTrue();
                });
                assertNoCallerTransaction();
                var observation = mapper.createObjectNode().put("context", expectedIdentity())
                        .put("scenario", scenario.name()).put("filter", filter).put("filterLength", filter.length())
                        .put("rawQuery", decoded.query()).put("decodedName", decoded.name())
                        .put("path", decoded.uri()).put("status", response.getStatusCode().value())
                        .put("guardsPassed", true);
                observation.set("storedNameLengths", mapper.valueToTree(names.stream().map(String::length).toList()));
                observation.set("expected", canonicalExpected);
                observation.set("actual", actual);
                System.out.println("T006_HTTP " + observation);
                fixture.cleanUp();
                assertEmptyDatabase(database);
                assertThat(actual).as("T006 " + scenario + " exact match without minimum length or truncation; filtered totals")
                        .isEqualTo(canonicalExpected);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.PublicCatalogNameFilterIntegrationTest#whitespaceScenarios")
        @Tag("feature006-t013")
        void normalizesOnlyExternalFilterWhitespace(WhitespaceScenario scenario) throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                var fone = fixture.createProduct("Fone Azul", "Fixture T013", "fone.jpg", new BigDecimal("10.00"));
                var cabo = fixture.createProduct("Cabo", "Fixture T013", "cabo.jpg", new BigDecimal("20.00"));
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactly(fone.id(), cabo.id());
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactly("Fone Azul", "Cabo");

                // Explicit first-page manifests: unfiltered starts with Cabo; FONE starts with Fone Azul.
                // Size 1 also distinguishes the unfiltered totals (2/2) from the filtered totals (1/1).
                var expected = mapper.createObjectNode().put("page", 0).put("size", 1)
                        .put("totalElements", scenario.unfiltered() ? 2 : 1)
                        .put("totalPages", scenario.unfiltered() ? 2 : 1);
                expected.putArray("content").add(nameItem(scenario.unfiltered() ? cabo : fone));
                var canonicalExpected = mapper.readTree(expected.toString());
                var query = (scenario.filter() == null ? ""
                        : "name=" + URLEncoder.encode(scenario.filter(), StandardCharsets.UTF_8) + "&")
                        + "page=0&size=1";
                var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                var requestId = UUID.randomUUID().toString();
                var headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("X-Feature006-Name-Observation", requestId);
                sql.clear();
                assertNoCallerTransaction();
                var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                var decoded = nameRequests.take(requestId);
                assertThat(decoded).as("T013 passive servlet observation").isNotNull();
                assertThat(decoded.query()).isEqualTo(query);
                assertThat(decoded.name()).isEqualTo(scenario.filter());
                assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getContentType()).isNotNull()
                        .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                var actual = mapper.readTree(response.getBody());
                assertThat(database.snapshot()).isEqualTo(before);
                assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                    assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                    assertThat(event.transactionActive()).isTrue();
                });
                assertNoCallerTransaction();
                var observation = mapper.createObjectNode().put("context", expectedIdentity())
                        .put("case", scenario.label()).put("filter", scenario.filter())
                        .put("unfiltered", scenario.unfiltered()).put("rawQuery", decoded.query())
                        .put("decodedName", decoded.name()).put("path", decoded.uri())
                        .put("status", response.getStatusCode().value()).put("guardsPassed", true)
                        .put("snapshotEqual", true).put("hibernateDml", 0).put("callerTransaction", false);
                observation.set("expected", canonicalExpected);
                observation.set("actual", actual);
                System.out.println("T013_HTTP " + observation);
                fixture.cleanUp();
                assertEmptyDatabase(database);
                assertThat(actual).as("T013 " + scenario.label() + " exact IDs, stored names and page metadata")
                        .isEqualTo(canonicalExpected);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.PublicCatalogNameFilterIntegrationTest#internalSpaceScenarios")
        @Tag("feature006-t014")
        void preservesInternalSpacesAndStoredNames(InternalSpaceScenario scenario) throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                var names = switch (scenario) {
                    case COMMON -> List.of("Fone Azul", "Fone  Azul");
                    case LEGACY -> List.of("  Fone  Azul  ");
                    case NBSP -> List.of("Fone Azul", "Fone\u00a0Azul");
                };
                var products = new ArrayList<ProductData>();
                for (var name : names) {
                    products.add(fixture.createProduct(name, "Fixture T014", "t014.jpg", new BigDecimal("10.00")));
                }
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactlyElementsOf(products.stream().map(ProductData::id).toList());
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactlyElementsOf(names);
                // Separate manifests prevent the legacy padded name from changing the COMMON scenario's matches.
                var expectations = switch (scenario) {
                    case COMMON -> List.of(new NameExpectation("fone azul", List.of(0)),
                            new NameExpectation("fone  azul", List.of(1)));
                    case LEGACY -> List.of(new NameExpectation(" fone  azul ", List.of(0)),
                            new NameExpectation("fone azul", List.of()));
                    case NBSP -> List.of(new NameExpectation("fone azul", List.of(0)),
                            new NameExpectation("fone\u00a0azul", List.of(1)));
                };
                var assertions = new ArrayList<Executable>();
                for (var expectation : expectations) {
                    var expectedProducts = expectation.indices().stream().map(products::get).toList();
                    var expected = mapper.createObjectNode().put("page", 0).put("size", 12)
                            .put("totalElements", expectedProducts.size())
                            .put("totalPages", expectedProducts.isEmpty() ? 0 : 1);
                    var expectedContent = expected.putArray("content");
                    for (var product : expectedProducts) expectedContent.add(nameItem(product));
                    var canonicalExpected = mapper.readTree(expected.toString());
                    var query = "name=" + URLEncoder.encode(expectation.filter(), StandardCharsets.UTF_8) + "&page=0&size=12";
                    var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                    var requestId = UUID.randomUUID().toString();
                    var headers = new HttpHeaders();
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("X-Feature006-Name-Observation", requestId);
                    sql.clear();
                    assertNoCallerTransaction();
                    var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    var decoded = nameRequests.take(requestId);
                    assertThat(decoded).as("T014 passive servlet observation").isNotNull();
                    assertThat(decoded.query()).isEqualTo(query);
                    assertThat(decoded.name()).isEqualTo(expectation.filter());
                    assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                    assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType()).isNotNull()
                            .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                    var actual = mapper.readTree(response.getBody());
                    assertThat(database.snapshot()).isEqualTo(before);
                    assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                        assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                        assertThat(event.transactionActive()).isTrue();
                    });
                    assertNoCallerTransaction();
                    var observation = mapper.createObjectNode().put("context", expectedIdentity())
                            .put("scenario", scenario.name()).put("filter", expectation.filter())
                            .put("rawQuery", decoded.query()).put("decodedName", decoded.name())
                            .put("path", decoded.uri()).put("status", response.getStatusCode().value())
                            .put("guardsPassed", true).put("snapshotEqual", true)
                            .put("hibernateDml", 0).put("callerTransaction", false);
                    observation.set("storedNames", mapper.valueToTree(names));
                    observation.set("expected", canonicalExpected);
                    observation.set("actual", actual);
                    System.out.println("T014_HTTP " + observation);
                    assertions.add(() -> assertThat(actual).as("T014 " + scenario + " filter=" + expectation.filter()
                            + " exact IDs, original name and filtered metadata").isEqualTo(canonicalExpected));
                }
                fixture.cleanUp();
                assertEmptyDatabase(database);
                assertAll("T014 " + scenario, assertions);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        @Test
        @Tag("feature006-t017")
        void paginatesFilteredResultsInStoredNameIdOrder() throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                var manifest = fixture.prepareV03();
                var products = manifest.insertedProducts();
                var expectedOrder = manifest.filteredOrder();
                var orderedProducts = expectedOrder.stream().map(id -> products.stream()
                        .filter(product -> product.id() == id).findFirst().orElseThrow()).toList();
                assertThat(products).extracting(ProductData::name)
                        .containsExactly("Telefone D", "Fone C", "Fone B", "Fone B", "Fone A", "Cabo");
                assertThat(orderedProducts).extracting(ProductData::name)
                        .containsExactly("Fone A", "Fone B", "Fone B", "Fone C", "Telefone D");
                assertThat(expectedOrder).hasSize(5).doesNotHaveDuplicates();
                assertThat(expectedOrder.get(1)).isLessThan(expectedOrder.get(2));
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactlyElementsOf(products.stream().map(ProductData::id).toList());
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactlyElementsOf(products.stream().map(ProductData::name).toList());
                assertThat(before.categories()).extracting(AdminProductDatabaseProbe.CategoryRow::id)
                        .containsExactlyElementsOf(manifest.categories().stream()
                                .map(AdminProductTestFixture.CategoryData::id).toList());
                assertThat(before.associations()).hasSize(2)
                        .containsExactlyInAnyOrderElementsOf(manifest.associations());
                assertThat(manifest.associations()).allSatisfy(pair ->
                        assertThat(pair.productId()).isEqualTo(expectedOrder.getFirst()));

                var seen = new ArrayList<Long>();
                var sizes = new ArrayList<Integer>();
                var assertions = new ArrayList<Executable>();
                for (int page = 0; page < 3; page++) {
                    // V03's insertion manifest supplies the oracle; no production query computes it.
                    var expectedProducts = orderedProducts.subList(page * 2, Math.min(page * 2 + 2, 5));
                    var expected = mapper.createObjectNode().put("page", page).put("size", 2)
                            .put("totalElements", 5).put("totalPages", 3);
                    var expectedContent = expected.putArray("content");
                    for (var product : expectedProducts) expectedContent.add(nameItem(product));
                    var canonicalExpected = mapper.readTree(expected.toString());
                    var query = "name=fone&page=" + page + "&size=2";
                    var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                    var requestId = UUID.randomUUID().toString();
                    var headers = new HttpHeaders();
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("X-Feature006-Name-Observation", requestId);
                    sql.clear();
                    assertNoCallerTransaction();
                    var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    var decoded = nameRequests.take(requestId);
                    assertThat(decoded).as("T017 passive servlet observation").isNotNull();
                    assertThat(decoded.query()).isEqualTo(query);
                    assertThat(decoded.name()).isEqualTo("fone");
                    assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                    assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType()).isNotNull()
                            .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                    var actual = mapper.readTree(response.getBody());
                    assertThat(actual.get("content").isArray()).isTrue();
                    sizes.add(actual.get("content").size());
                    for (var item : actual.get("content")) seen.add(item.get("id").asLong());
                    assertThat(database.snapshot()).isEqualTo(before);
                    assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                        assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                        assertThat(event.transactionActive()).isTrue();
                    });
                    assertNoCallerTransaction();
                    var observation = mapper.createObjectNode().put("context", expectedIdentity())
                            .put("page", page).put("filter", "fone").put("rawQuery", decoded.query())
                            .put("decodedName", decoded.name()).put("path", decoded.uri())
                            .put("status", response.getStatusCode().value()).put("guardsPassed", true)
                            .put("snapshotEqual", true).put("hibernateDml", 0).put("callerTransaction", false);
                    observation.set("filteredOrder", mapper.valueToTree(expectedOrder));
                    observation.set("expected", canonicalExpected);
                    observation.set("actual", actual);
                    System.out.println("T017_HTTP " + observation);
                    assertions.add(() -> assertThat(actual).as("T017 exact page, IDs, stored names and totals")
                            .isEqualTo(canonicalExpected));
                }
                assertions.add(() -> assertThat(sizes).containsExactly(2, 2, 1));
                assertions.add(() -> assertThat(seen).containsExactlyElementsOf(expectedOrder).doesNotHaveDuplicates());
                fixture.cleanUp();
                assertEmptyDatabase(database);
                assertAll("T017 complete V03 navigation with name/id ordering", assertions);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.devsuperior.dscommerce.controllers.PublicCatalogNameFilterIntegrationTest#emptyPageScenarios")
        @Tag("feature006-t018")
        void returnsCoherentEmptyFilteredPages(EmptyPageScenario scenario) throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                if (scenario.populated()) {
                    var manifest = fixture.prepareV03();
                    assertThat(manifest.insertedProducts()).extracting(ProductData::name)
                            .containsExactly("Telefone D", "Fone C", "Fone B", "Fone B", "Fone A", "Cabo");
                    assertThat(manifest.filteredOrder()).hasSize(5).doesNotHaveDuplicates();
                    var prepared = database.snapshot();
                    assertThat(prepared.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                            .containsExactlyElementsOf(manifest.insertedProducts().stream().map(ProductData::id).toList());
                    assertThat(prepared.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                            .containsExactlyElementsOf(manifest.insertedProducts().stream().map(ProductData::name).toList());
                    assertThat(prepared.categories()).extracting(AdminProductDatabaseProbe.CategoryRow::id)
                            .containsExactlyElementsOf(manifest.categories().stream()
                                    .map(AdminProductTestFixture.CategoryData::id).toList());
                    assertThat(prepared.associations()).hasSize(2)
                            .containsExactlyInAnyOrderElementsOf(manifest.associations());
                }
                var before = database.snapshot();
                var expected = mapper.createObjectNode().put("page", scenario.page()).put("size", scenario.size())
                        .put("totalElements", scenario.totalElements()).put("totalPages", scenario.totalPages());
                expected.putArray("content");
                var canonicalExpected = mapper.readTree(expected.toString());
                var query = (scenario.filter() == null ? "" : "name="
                        + URLEncoder.encode(scenario.filter(), StandardCharsets.UTF_8) + "&")
                        + "page=" + scenario.page() + "&size=" + scenario.size();
                var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                var requestId = UUID.randomUUID().toString();
                var headers = new HttpHeaders();
                headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                headers.set("X-Feature006-Name-Observation", requestId);
                sql.clear();
                assertNoCallerTransaction();
                var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                var decoded = nameRequests.take(requestId);
                assertThat(decoded).as("T018 passive servlet observation").isNotNull();
                assertThat(decoded.query()).isEqualTo(query);
                assertThat(decoded.name()).isEqualTo(scenario.filter());
                assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getHeaders().getContentType()).isNotNull()
                        .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                var actual = mapper.readTree(response.getBody());
                assertThat(database.snapshot()).isEqualTo(before);
                assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                    assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                    assertThat(event.transactionActive()).isTrue();
                });
                assertNoCallerTransaction();
                var observation = mapper.createObjectNode().put("context", expectedIdentity())
                        .put("scenario", scenario.label()).put("populated", scenario.populated())
                        .put("filter", scenario.filter()).put("rawQuery", decoded.query())
                        .put("decodedName", decoded.name()).put("path", decoded.uri())
                        .put("status", response.getStatusCode().value()).put("guardsPassed", true)
                        .put("snapshotEqual", true).put("hibernateDml", 0).put("callerTransaction", false);
                observation.set("expected", canonicalExpected);
                observation.set("actual", actual);
                System.out.println("T018_HTTP " + observation);
                assertThat(actual).as("T018 " + scenario.label() + " exact empty content and requested pagination/totals")
                        .isEqualTo(canonicalExpected);
                fixture.cleanUp();
                assertEmptyDatabase(database);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        @Test
        @Tag("feature006-t019")
        void doesNotRetainNameBetweenRequests() throws Exception {
            assertNoCallerTransaction();
            var database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
            assertEmptyDatabase(database);
            try (var fixture = new AdminProductTestFixture(context)) {
                var manifest = fixture.prepareV03();
                var products = manifest.insertedProducts();
                assertThat(products).extracting(ProductData::name)
                        .containsExactly("Telefone D", "Fone C", "Fone B", "Fone B", "Fone A", "Cabo");
                var completeOrder = manifest.unfilteredOrder();
                assertThat(completeOrder).hasSize(6).doesNotHaveDuplicates();
                assertThat(completeOrder.stream().map(id -> products.stream()
                        .filter(product -> product.id() == id).findFirst().orElseThrow().name()).toList())
                        .containsExactly("Cabo", "Fone A", "Fone B", "Fone B", "Fone C", "Telefone D");
                assertThat(manifest.filteredOrder()).containsExactlyElementsOf(completeOrder.subList(1, 6));
                var before = database.snapshot();
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::id)
                        .containsExactlyElementsOf(products.stream().map(ProductData::id).toList());
                assertThat(before.products()).extracting(AdminProductDatabaseProbe.ProductRow::name)
                        .containsExactlyElementsOf(products.stream().map(ProductData::name).toList());
                assertThat(before.categories()).extracting(AdminProductDatabaseProbe.CategoryRow::id)
                        .containsExactlyElementsOf(manifest.categories().stream()
                                .map(AdminProductTestFixture.CategoryData::id).toList());
                assertThat(before.associations()).hasSize(2)
                        .containsExactlyInAnyOrderElementsOf(manifest.associations());

                for (boolean filtered : List.of(true, false)) {
                    // Both page oracles come from the insertion manifest, never from the preceding response.
                    var order = filtered ? manifest.filteredOrder() : completeOrder;
                    var expected = mapper.createObjectNode().put("page", 0).put("size", 2)
                            .put("totalElements", filtered ? 5 : 6).put("totalPages", 3);
                    var expectedContent = expected.putArray("content");
                    for (var id : order.subList(0, 2)) {
                        expectedContent.add(nameItem(products.stream().filter(product -> product.id() == id)
                                .findFirst().orElseThrow()));
                    }
                    var canonicalExpected = mapper.readTree(expected.toString());
                    var query = (filtered ? "name=fone&" : "") + "page=0&size=2";
                    var uri = URI.create("http://localhost:" + port + expectedContextPath() + "/products?" + query);
                    var requestId = UUID.randomUUID().toString();
                    var headers = new HttpHeaders();
                    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
                    headers.set("X-Feature006-Name-Observation", requestId);
                    sql.clear();
                    assertNoCallerTransaction();
                    var response = http.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                    var decoded = nameRequests.take(requestId);
                    assertThat(decoded).as("T019 passive servlet observation").isNotNull();
                    assertThat(decoded.query()).isEqualTo(query);
                    assertThat(decoded.name()).isEqualTo(filtered ? "fone" : null);
                    assertThat(decoded.contextPath()).isEqualTo(expectedContextPath());
                    assertThat(decoded.uri()).isEqualTo(expectedContextPath() + "/products");
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType()).isNotNull()
                            .satisfies(type -> assertThat(type.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
                    var actual = mapper.readTree(response.getBody());
                    assertThat(database.snapshot()).isEqualTo(before);
                    assertThat(sql.events()).isNotEmpty().allSatisfy(event -> {
                        assertThat(event.sql().stripLeading().toLowerCase(Locale.ROOT)).startsWith("select ");
                        assertThat(event.transactionActive()).isTrue();
                    });
                    assertNoCallerTransaction();
                    var observation = mapper.createObjectNode().put("context", expectedIdentity())
                            .put("step", filtered ? "filtered" : "omitted-after-filter")
                            .put("rawQuery", decoded.query()).put("decodedName", decoded.name())
                            .put("path", decoded.uri()).put("status", response.getStatusCode().value())
                            .put("guardsPassed", true).put("snapshotEqual", true)
                            .put("hibernateDml", 0).put("callerTransaction", false);
                    observation.set("completeOrder", mapper.valueToTree(completeOrder));
                    observation.set("expected", canonicalExpected);
                    observation.set("actual", actual);
                    System.out.println("T019_HTTP " + observation);
                    assertThat(actual).as("T019 " + (filtered ? "restricted first request" : "complete catalog page after omission")
                            + " exact IDs, stored values and metadata").isEqualTo(canonicalExpected);
                }
                fixture.cleanUp();
                assertEmptyDatabase(database);
            } finally {
                nameRequests.clear();
                sql.clear();
            }
        }

        private ObjectNode nameItem(ProductData product) {
            return mapper.createObjectNode().put("id", product.id()).put("name", product.name())
                    .put("image", product.image()).put("price", product.price());
        }

        private static List<PaginationCase> paginationCases() {
            return List.of(
                    new PaginationCase("defaults", "", 0, 12, 200, null),
                    new PaginationCase("empty-page", "page=&size=2", 0, 2, 200, null),
                    new PaginationCase("empty-size", "page=1&size=", 1, 12, 200, null),
                    new PaginationCase("empty-both", "page=&size=", 0, 12, 200, null),
                    new PaginationCase("size-one", "page=0&size=1", 0, 1, 200, null),
                    new PaginationCase("size-fifty", "page=0&size=50", 0, 50, 200, null),
                    new PaginationCase("negative-page", "page=-1&size=12", -1, 12, 400, "page must be greater than or equal to 0"),
                    new PaginationCase("zero-size", "page=0&size=0", 0, 0, 400, "size must be between 1 and 50"),
                    new PaginationCase("excessive-size", "page=0&size=51", 0, 51, 400, "size must be between 1 and 50"),
                    new PaginationCase("non-numeric-page", "page=abc&size=12", 0, 12, 400, null),
                    new PaginationCase("non-numeric-size", "page=0&size=abc", 0, 12, 400, null));
        }

        private record PaginationCase(String label, String query, int page, int size, int status, String boundMessage) { }

        private static void assertEmptyDatabase(AdminProductDatabaseProbe database) {
            var snapshot = database.snapshot();
            assertThat(snapshot.products()).isEmpty();
            assertThat(snapshot.categories()).isEmpty();
            assertThat(snapshot.associations()).isEmpty();
            assertThat(snapshot.orders()).isEmpty();
            assertThat(snapshot.items()).isEmpty();
            assertThat(database.accountIds()).isEmpty();
        }

        private static void assertNoCallerTransaction() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        }
    }

    enum NameScenario { SUBSTRING, CASE, ACCENTS, PERCENT, UNDERSCORE }
    record NameExpectation(String filter, List<Integer> indices) { }
    static Stream<NameScenario> nameScenarios() { return Stream.of(NameScenario.values()); }

    enum LengthScenario { SHORT, LONG }
    static Stream<LengthScenario> lengthScenarios() { return Stream.of(LengthScenario.values()); }

    record WhitespaceScenario(String label, String filter, boolean unfiltered) {
        @Override public String toString() { return label; }
    }

    static Stream<WhitespaceScenario> whitespaceScenarios() {
        return Stream.of(
                new WhitespaceScenario("trimmed-control", "FONE", false),
                new WhitespaceScenario("common-padding", " FONE ", false),
                new WhitespaceScenario("tab-left", "\tFONE", false),
                new WhitespaceScenario("tab-right", "FONE\t", false),
                new WhitespaceScenario("tab-both", "\tFONE\t", false),
                new WhitespaceScenario("nbsp-left", "\u00a0FONE", false),
                new WhitespaceScenario("nbsp-right", "FONE\u00a0", false),
                new WhitespaceScenario("nbsp-both", "\u00a0FONE\u00a0", false),
                new WhitespaceScenario("em-left", "\u2003FONE", false),
                new WhitespaceScenario("em-right", "FONE\u2003", false),
                new WhitespaceScenario("em-both", "\u2003FONE\u2003", false),
                new WhitespaceScenario("mixed-padding", "\t\u00a0\u2003 FONE \u2003\u00a0\t", false),
                new WhitespaceScenario("omitted", null, true),
                new WhitespaceScenario("empty", "", true),
                new WhitespaceScenario("common-only", "   ", true),
                new WhitespaceScenario("tab-only", "\t", true),
                new WhitespaceScenario("nbsp-only", "\u00a0", true),
                new WhitespaceScenario("em-only", "\u2003", true),
                new WhitespaceScenario("mixed-only", " \t\u00a0\u2003\t ", true));
    }

    enum InternalSpaceScenario { COMMON, LEGACY, NBSP }
    static Stream<InternalSpaceScenario> internalSpaceScenarios() { return Stream.of(InternalSpaceScenario.values()); }

    record EmptyPageScenario(String label, boolean populated, String filter, int page, int size,
                             int totalElements, int totalPages) {
        @Override public String toString() { return label; }
    }

    static Stream<EmptyPageScenario> emptyPageScenarios() {
        return Stream.of(
                new EmptyPageScenario("no-matches-first", true, "inexistente", 0, 2, 0, 0),
                new EmptyPageScenario("no-matches-nonzero", true, "inexistente", 2, 2, 0, 0),
                new EmptyPageScenario("beyond-five-matches", true, "fone", 3, 2, 5, 3),
                new EmptyPageScenario("empty-filtered-first", false, "fone", 0, 2, 0, 0),
                new EmptyPageScenario("empty-filtered-nonzero", false, "fone", 2, 2, 0, 0),
                new EmptyPageScenario("empty-unfiltered-first", false, null, 0, 2, 0, 0),
                new EmptyPageScenario("empty-unfiltered-nonzero", false, null, 2, 2, 0, 0));
    }

    record NameRequest(String query, String name, String uri, String contextPath) { }

    static final class NameRequestObservation {
        private final Map<String, NameRequest> requests = new ConcurrentHashMap<>();
        void record(String id, HttpServletRequest request) {
            requests.put(id, new NameRequest(request.getQueryString(), request.getParameter("name"),
                    request.getRequestURI(), request.getContextPath()));
        }
        NameRequest take(String id) { return requests.remove(id); }
        void clear() { requests.clear(); }
    }

    // Explicit import only. TestConfiguration is excluded from application component scanning.
    @TestConfiguration(proxyBeanMethods = false)
    static class SqlProbeConfig {
        @Bean NameRequestObservation publicCatalogNameRequestObservation() { return new NameRequestObservation(); }

        @Bean FilterRegistrationBean<Filter> publicCatalogNameRequestObserver(NameRequestObservation observation) {
            var registration = new FilterRegistrationBean<Filter>((request, response, chain) -> {
                var http = (HttpServletRequest) request;
                var id = http.getHeader("X-Feature006-Name-Observation");
                if (id != null) observation.record(id, http);
                chain.doFilter(request, response);
            });
            registration.addUrlPatterns("/products");
            registration.setDispatcherTypes(DispatcherType.REQUEST);
            return registration;
        }

        @Bean AdminProductDatabaseProbe.SqlObservation publicCatalogNameSqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }

        @Bean HibernatePropertiesCustomizer publicCatalogNameSqlCustomizer(
                AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
