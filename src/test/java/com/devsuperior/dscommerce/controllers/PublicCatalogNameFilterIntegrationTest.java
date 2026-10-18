package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    // Explicit import only. TestConfiguration is excluded from application component scanning.
    @TestConfiguration(proxyBeanMethods = false)
    static class SqlProbeConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation publicCatalogNameSqlObservation() {
            return new AdminProductDatabaseProbe.SqlObservation();
        }

        @Bean HibernatePropertiesCustomizer publicCatalogNameSqlCustomizer(
                AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
