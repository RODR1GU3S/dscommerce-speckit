package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.OrderItemRequestDTO;
import com.devsuperior.dscommerce.dto.OrderRequestDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.ProductRow;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductCategory;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.ProductData;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.OrderService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** T068: native MVC failures and real security/database; the observer never resolves or fabricates errors. */
@SpringBootTest(classes = DscommerceApplication.class,
        properties = {"spring.jpa.open-in-view=false", "logging.level.root=INFO"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductFrameworkHttpErrorIntegrationTest.ObservationConfig.class)
class AdminProductFrameworkHttpErrorIntegrationTest {
    private static final String BODY_CANARY = "T068_PRIVATE_BODY";
    private static final String QUERY_CANARY = "T068_PRIVATE_QUERY";
    private static final String VALUE_CANARY = "T068_PRIVATE_VALUE";
    private static final String VENDOR = "application/vnd.t068+json";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderService orders;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private Observer observer;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private ProductData target, other;
    private long categoryA, categoryB;
    private final Set<Long> ownedOrders = new HashSet<>(), createdProducts = new HashSet<>();
    private MethodInterceptor maintenanceObservation;
    private int maintenanceCalls;

    @BeforeEach
    void committedPrivateCatalogAndIndependentHistoricalRows() {
        outsideTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty(); fixture = new AdminProductTestFixture(context); ownedOrders.clear(); createdProducts.clear();
        categoryA = fixture.createCategory("T068 restricted category").id();
        categoryB = fixture.createCategory("T068 restricted category").id();
        fixture.createCategory("T068 unused category");
        target = fixture.createProduct("T068 restricted target", "T068 restricted description", "https://example.invalid/t068.jpg", new BigDecimal("10.00"));
        other = fixture.createProduct("T068 other", "T068 other description", "https://example.invalid/t068-other.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), categoryA); fixture.associate(target.id(), categoryB); fixture.associate(other.id(), categoryA);
        var buyer = fixture.prepareBuyer();
        try {
            assertThat(orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(other.id(), 3))),
                    new AuthenticatedAccountPrincipal(buyer.id())).total()).isEqualTo("9.75");
        } finally { database.snapshot().orders().forEach(order -> ownedOrders.add(order.id())); }
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(other));
        assertThat(prepared.categories()).hasSize(3);
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(target.id(), categoryA),
                new ProductCategory(target.id(), categoryB), new ProductCategory(other.id(), categoryA));
        assertThat(prepared.orders()).hasSize(1); assertThat(prepared.items()).hasSize(1);
        var order = prepared.orders().getFirst(); var item = prepared.items().getFirst();
        assertThat(order.buyerId()).isEqualTo(buyer.id()); assertThat(order.createdAt()).isNotNull();
        assertThat(order.status()).isEqualTo("WAITING_PAYMENT");
        assertThat(item.orderId()).isEqualTo(order.id()); assertThat(item.productId()).isEqualTo(other.id());
        assertThat(item.position()).isZero(); assertThat(item.quantity()).isEqualTo(3);
        assertThat(item.unitPrice()).isEqualTo(new BigDecimal("3.25"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("9.75"));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(
                context.getBean(com.devsuperior.dscommerce.security.AdminDiagnosticLoggingFilter.class), context.getBean(FilterChainProxy.class)).build();
        maintenanceCalls = 0;
        maintenanceObservation = invocation -> { maintenanceCalls++; return invocation.proceed(); };
        assertThat(maintenance).isInstanceOf(Advised.class);
        ((Advised) maintenance).addAdvice(0, maintenanceObservation);
    }

    static Stream<Arguments> frameworkCases() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(Identity.values())
                .flatMap(identity -> Stream.of(HttpCase.values()).map(sample -> Arguments.of(prefix, identity, sample))));
    }

    @ParameterizedTest(name = "framework context={0} identity={1} request={2}")
    @MethodSource("frameworkCases")
    void nativeErrorsKeepHeadersAndSecurityPrecedesInvalidHttp(String prefix, Identity identity, HttpCase sample) throws Exception {
        String token = login(identity);
        String route = sample.operation.route(Long.toString(target.id()));
        var call = request(HttpMethod.valueOf(sample.method), prefix + route).contextPath(prefix).servletPath(route)
                .accept(sample.accept).contentType(sample.contentType)
                .content(sample.status == 400 ? "{\"" + BODY_CANARY + "\":" : validBody());
        if (token != null) call.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var before = database.snapshot(); String accounts = accountFingerprint(); observer.seen = null; sql.clear();
        var result = perform(call); var after = database.snapshot();
        assertThat(after).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(maintenanceCalls).isZero(); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        int expected = identity == Identity.ADMIN ? sample.status : identity.status;
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        if (identity != Identity.ADMIN) {
            assertThat(result.getHandler()).isNull(); assertThat(observer.seen).isNull();
            assertEnvelope(result, expected, prefix + route, token, null, null);
        } else {
            var seen = observer.seen; assertThat(seen).isNotNull(); assertThat(seen.admin()).isTrue();
            assertThat(seen.failure()).isInstanceOf(sample.exceptionType());
            assertThat(result.getResolvedException()).isSameAs(seen.failure());
            if (sample.status != 400) {
                assertThat(seen.failure()).isInstanceOf(ErrorResponse.class);
                assertThat(((ErrorResponse) seen.failure()).getStatusCode().value()).isEqualTo(sample.status);
            }
            var headers = new HttpHeaders(); result.getResponse().getHeaderNames()
                    .forEach(name -> headers.put(name, result.getResponse().getHeaders(name)));
            assertThat(headers.getAllow()).isEqualTo(seen.headers().getAllow());
            assertThat(headers.getAccept()).isEqualTo(seen.headers().getAccept());
            if (sample.status == 405) assertThat(headers.getAllow()).containsAll(sample.operation.supported());
            if (sample.status == 415) assertThat(headers.getAccept()).contains(MediaType.APPLICATION_JSON);
            if (sample.status == 406) assertThat(((HttpMediaTypeNotAcceptableException) seen.failure()).getSupportedMediaTypes())
                    .contains(MediaType.APPLICATION_JSON);
            if (sample.accept.equals("application/xml")) {
                // No writable representation is promised for an XML-only client. If written, it must still be safe JSON.
                if (result.getResponse().getContentAsByteArray().length > 0) assertEnvelope(result, expected, prefix + route, token, null, null);
            } else {
                assertEnvelope(result, expected, prefix + route, token, sample.status == 400 ? "body" : null,
                        sample.status == 400 ? "INVALID_BODY" : null);
                if (sample.status == 406) assertThat(MediaType.parseMediaType(result.getResponse().getContentType())
                        .isCompatibleWith(MediaType.parseMediaType(VENDOR))).isTrue();
            }
        }
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(expected == 401 ? "Bearer" : null);
        System.out.printf("T068_HTTP context=%s identity=%s case=%s status=%d native=%s bodyBytes=%d headersPreserved=true maintenance=0 ormDml=0 snapshotEqual=true%n",
                prefix.isEmpty() ? "ROOT" : prefix, identity, sample, expected,
                observer.seen == null ? "NONE" : observer.seen.failure().getClass().getSimpleName(), result.getResponse().getContentAsByteArray().length);
    }

    static Stream<Arguments> parameterCases() {
        var samples = new ArrayList<ParameterCase>();
        for (String value : List.of("-1", "abc", "2147483648")) samples.add(new ParameterCase("page-" + value, Operation.LIST, null, "page", "page", List.of(value)));
        for (String value : List.of("0", "51", "abc")) samples.add(new ParameterCase("size-" + value, Operation.LIST, null, "size", "size", List.of(value)));
        for (String key : List.of("page", "size", "name")) samples.add(new ParameterCase("repeated-" + key, Operation.LIST, null, key, key, List.of("1", "2")));
        samples.add(new ParameterCase("unknown-list-query", Operation.LIST, null, "body", QUERY_CANARY, List.of(VALUE_CANARY)));
        for (Operation operation : List.of(Operation.DETAIL, Operation.PUT, Operation.DELETE)) {
            for (String id : List.of("0", "-1", "abc", "9223372036854775808")) samples.add(new ParameterCase(operation + "-id-" + id, operation, id, "id", null, List.of()));
        }
        for (Operation operation : List.of(Operation.DETAIL, Operation.CATEGORIES, Operation.POST, Operation.PUT, Operation.DELETE)) {
            samples.add(new ParameterCase("forbidden-query-" + operation, operation, null, "body", QUERY_CANARY, List.of(VALUE_CANARY)));
        }
        return Stream.of("", "/api").flatMap(prefix -> samples.stream().map(sample -> Arguments.of(prefix, sample)));
    }

    static Stream<Arguments> vendorGetCases() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(Identity.values()).flatMap(identity ->
                Stream.of(Operation.LIST, Operation.DETAIL).map(operation -> Arguments.of(prefix, identity, operation))));
    }

    @ParameterizedTest(name = "vendor GET context={0} identity={1} operation={2}")
    @MethodSource("vendorGetCases")
    void vendorJsonGetIsSupportedButStillRequiresAdmin(String prefix, Identity identity, Operation operation) throws Exception {
        String token = login(identity); String route = operation.route(Long.toString(target.id()));
        var call = request(HttpMethod.GET, prefix + route).contextPath(prefix).servletPath(route).accept(VENDOR);
        if (token != null) call.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var before = database.snapshot(); String accounts = accountFingerprint(); observer.seen = null; sql.clear();
        var result = perform(call);
        assertThat(database.snapshot()).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(maintenanceCalls).isZero(); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        assertThat(observer.seen).isNull(); assertThat(result.getResolvedException()).isNull();
        int expected = identity == Identity.ADMIN ? 200 : identity.status;
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        if (identity == Identity.ADMIN) {
            assertThat(result.getHandler()).isNotNull();
            assertThat(MediaType.parseMediaType(result.getResponse().getContentType()).isCompatibleWith(MediaType.parseMediaType(VENDOR))).isTrue();
            var body = mapper.readTree(result.getResponse().getContentAsByteArray());
            if (operation == Operation.LIST) {
                assertThat(body.path("totalElements").asInt()).isEqualTo(2); assertThat(body.path("content")).hasSize(2);
                assertThat(body.path("content").get(0).path("id").longValue()).isEqualTo(other.id());
                assertThat(body.path("content").get(1).path("id").longValue()).isEqualTo(target.id());
                assertThat(body.path("content").get(1).path("price").isTextual()).isTrue();
                assertThat(body.path("content").get(1).path("price").asText()).isEqualTo("10.00");
            } else {
                assertThat(body.path("id").longValue()).isEqualTo(target.id());
                assertThat(body.path("name").asText()).isEqualTo(target.name());
                assertThat(body.path("description").asText()).isEqualTo(target.description());
                assertThat(body.path("image").asText()).isEqualTo(target.image());
                assertThat(body.path("price").asText()).isEqualTo("10.00");
                assertThat(body.path("categories")).hasSize(2);
            }
        } else {
            assertThat(result.getHandler()).isNull(); assertEnvelope(result, expected, prefix + route, token, null, null);
        }
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T068_VENDOR context=%s identity=%s operation=%s status=%d maintenance=0 ormDml=0 snapshotEqual=true%n",
                prefix.isEmpty() ? "ROOT" : prefix, identity, operation, expected);
    }

    @ParameterizedTest(name = "parameters context={0} request={1}")
    @MethodSource("parameterCases")
    void invalidQueriesAndIdsAreSafe400WithoutMaintenance(String prefix, ParameterCase sample) throws Exception {
        String token = login(Identity.ADMIN);
        String id = sample.id() == null ? Long.toString(target.id()) : sample.id();
        String route = sample.operation().route(id);
        var call = request(HttpMethod.valueOf(sample.operation().method), prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON);
        if (sample.operation() == Operation.POST || sample.operation() == Operation.PUT) call.contentType(MediaType.APPLICATION_JSON).content(validBody());
        if (sample.queryKey() != null) call.queryParam(sample.queryKey(), sample.values().toArray(String[]::new));
        var before = database.snapshot(); String accounts = accountFingerprint(); observer.seen = null; sql.clear();
        var result = perform(call);
        assertThat(database.snapshot()).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(maintenanceCalls).isZero(); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        assertThat(result.getResolvedException()).isInstanceOf(AdminQueryParameters.InvalidParameterException.class);
        assertEnvelope(result, 400, prefix + route, token, sample.field(), "INVALID_PARAMETER");
        // The contractual path contains an invalid path ID; it must not be echoed again in messages/errors.
        if (sample.id() != null) {
            var body = mapper.readTree(result.getResponse().getContentAsByteArray());
            assertThat(body.path("message").asText() + body.path("errors")).doesNotContain(sample.id());
        }
        System.out.printf("T068_PARAMETER context=%s case=%s field=%s status=400 maintenance=0 ormDml=0 snapshotEqual=true%n",
                prefix.isEmpty() ? "ROOT" : prefix, sample.name(), sample.field());
    }

    static Stream<Arguments> positiveCases() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(Operation.values()).map(operation -> Arguments.of(prefix, operation)));
    }

    @ParameterizedTest(name = "positive context={0} operation={1}")
    @MethodSource("positiveCases")
    void validOperationsKeepContextPathAndLocationAfterRealService(String prefix, Operation operation) throws Exception {
        String token = login(Identity.ADMIN); String route = operation.route(Long.toString(target.id()));
        var call = request(HttpMethod.valueOf(operation.method), prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON);
        if (operation == Operation.POST || operation == Operation.PUT) call.contentType(MediaType.APPLICATION_JSON).content(validBody());
        var before = database.snapshot(); String accounts = accountFingerprint(); sql.clear();
        var result = perform(call); var after = database.snapshot();
        assertThat(result.getResponse().getStatus()).isEqualTo(operation.status); assertThat(result.getResolvedException()).isNull();
        assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(after.categories()).isEqualTo(before.categories()); assertThat(after.orders()).isEqualTo(before.orders()); assertThat(after.items()).isEqualTo(before.items());
        if (operation == Operation.POST || operation == Operation.PUT) {
            var body = mapper.readTree(result.getResponse().getContentAsByteArray()); long id = body.path("id").longValue();
            if (operation == Operation.POST) assertThat(id).isPositive().isNotIn(target.id(), other.id());
            else assertThat(id).isEqualTo(target.id());
            var products = new ArrayList<>(before.products()); if (operation == Operation.PUT) products.remove(row(target));
            products.add(new ProductRow(id, "T068 replacement", "T068 replacement description", "https://example.invalid/t068-new.jpg", new BigDecimal("12.00")));
            products.sort(java.util.Comparator.comparingLong(ProductRow::id));
            var pairs = new HashSet<>(before.associations()); if (operation == Operation.PUT) pairs.removeIf(pair -> pair.productId() == target.id());
            pairs.add(new ProductCategory(id, categoryB));
            assertThat(after).isEqualTo(new Snapshot(products, before.categories(), pairs, before.orders(), before.items()));
            if (operation == Operation.POST) {
                var location = java.net.URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
                assertThat(location.getPath()).isEqualTo(prefix + "/admin/products/" + id);
                assertThat(location.getRawQuery()).isNull(); assertThat(location.getRawFragment()).isNull();
                var reread = perform(request(HttpMethod.GET, location.getPath()).contextPath(prefix).servletPath("/admin/products/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON));
                assertThat(reread.getResponse().getStatus()).isEqualTo(200);
                assertThat(mapper.readTree(reread.getResponse().getContentAsByteArray())).isEqualTo(body);
                assertThat(database.snapshot()).isEqualTo(after);
            }
        } else if (operation == Operation.DELETE) {
            assertThat(result.getResponse().getContentAsByteArray()).isEmpty(); assertThat(result.getResponse().getContentType()).isNull();
            var pairs = new HashSet<>(before.associations()); pairs.removeIf(pair -> pair.productId() == target.id());
            assertThat(after).isEqualTo(new Snapshot(List.of(row(other)), before.categories(), pairs, before.orders(), before.items()));
        } else {
            assertThat(after).isEqualTo(before); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
            var body = mapper.readTree(result.getResponse().getContentAsByteArray());
            if (operation == Operation.LIST) { assertThat(body.path("content")).hasSize(2); assertThat(body.path("totalElements").asInt()).isEqualTo(2); }
            if (operation == Operation.DETAIL) assertThat(body.path("id").longValue()).isEqualTo(target.id());
            if (operation == Operation.CATEGORIES) assertThat(body).hasSize(3);
        }
        assertThat(maintenanceCalls).isEqualTo(operation.writes() ? 1 : 0);
        if (operation != Operation.POST) assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T068_POSITIVE context=%s operation=%s status=%d locationChecked=true independentState=true history=9.75%n",
                prefix.isEmpty() ? "ROOT" : prefix, operation, operation.status);
    }

    private String validBody() throws Exception {
        var body = mapper.createObjectNode().put("name", "T068 replacement").put("description", "T068 replacement description")
                .put("price", "12.00").put("image", "https://example.invalid/t068-new.jpg");
        body.putArray("categoryIds").add(categoryB); return mapper.writeValueAsString(body);
    }
    private String login(Identity identity) {
        if (identity == Identity.ANONYMOUS) return null;
        Set<UserRole> roles = identity == Identity.ADMIN ? Set.of(UserRole.ADMIN) : identity == Identity.CLIENT ? Set.of(UserRole.CLIENT) : Set.of();
        var account = fixture.createAccount("T068 request account", "Disposable-T068-password", roles);
        assertThat(database.roles(account.id())).containsExactlyInAnyOrderElementsOf(roles);
        return fixture.login(account);
    }
    private MvcResult perform(MockHttpServletRequestBuilder call) throws Exception {
        var priorIds = database.snapshot().products().stream().map(ProductRow::id).toList(); outsideTransaction();
        try { return SanitizationProbe.perform(context, () -> mvc.perform(call).andReturn()); }
        finally {
            outsideTransaction(); database.snapshot().products().stream().filter(product -> !priorIds.contains(product.id()))
                    .forEach(product -> createdProducts.add(product.id()));
        }
    }
    private void assertEnvelope(MvcResult result, int status, String route, String token, String field, String code) throws Exception {
        var response = result.getResponse(); assertThat(response.getStatus()).isEqualTo(status);
        var contentType = MediaType.parseMediaType(response.getContentType());
        assertThat(contentType.getSubtype().equals("json") || contentType.getSubtype().endsWith("+json")).isTrue();
        var body = mapper.readTree(response.getContentAsByteArray());
        var names = new ArrayList<String>(); body.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("error").asText()).isEqualTo(HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(status == 400 ? "Invalid product maintenance data"
                : status == 401 ? "Authentication required or invalid" : status == 403 ? "Product maintenance not permitted" : "Product maintenance request not supported");
        assertThat(body.path("path").asText()).isEqualTo(route); assertThat(body.path("errors").isArray()).isTrue();
        if (field == null) assertThat(body.path("errors")).isEmpty();
        else {
            assertThat(body.path("errors")).hasSize(1); var error = body.path("errors").get(0);
            names.clear(); error.fieldNames().forEachRemaining(names::add); assertThat(names).containsExactlyInAnyOrder("field", "code", "message");
            assertThat(error.path("field").asText()).isEqualTo(field); assertThat(error.path("code").asText()).isEqualTo(code);
            assertThat(error.path("message").asText()).isNotBlank();
        }
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(status == 401 ? "Bearer" : null);
        if (token != null) assertThat(response.getContentAsString().contains(token)).as("token must not appear in response").isFalse();
        assertThat(response.getContentAsString()).doesNotContain(BODY_CANARY, QUERY_CANARY, VALUE_CANARY,
                "T068 restricted", "T068 replacement", "https://example.invalid/t068-new.jpg",
                "select ", "insert into", "tb_product", "password_hash", "SQLException", "stackTrace", "Disposable-T068");
    }
    private String accountFingerprint() throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var c = database.openConnection(); var s = c.createStatement()) {
            for (String query : List.of("select id,name,password_hash from tb_user_account order by id",
                    "select user_account_id,role from tb_user_account_role order by user_account_id,role")) {
                try (var rows = s.executeQuery(query)) {
                    while (rows.next()) for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                        byte[] value = rows.getString(i).getBytes(StandardCharsets.UTF_8);
                        digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.length).array()); digest.update(value);
                    }
                }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
    @AfterEach
    void removeOnlyOwnAdviceAndRowsAfterAssertions() throws Exception {
        outsideTransaction();
        if (maintenanceObservation != null) assertThat(((Advised) maintenance).removeAdvice(maintenanceObservation)).isTrue();
        if (fixture == null) return;
        try (var c = database.openConnection()) {
            c.setAutoCommit(false);
            try {
                for (long id : ownedOrders) {
                    try (var item = c.prepareStatement("delete from tb_order_item where order_id=?"); var order = c.prepareStatement("delete from tb_order where id=?")) {
                        item.setLong(1, id); item.executeUpdate(); order.setLong(1, id); order.executeUpdate();
                    }
                }
                for (long id : createdProducts) {
                    try (var pair = c.prepareStatement("delete from tb_product_category where product_id=?"); var product = c.prepareStatement("delete from tb_product where id=?")) {
                        pair.setLong(1, id); pair.executeUpdate(); product.setLong(1, id); product.executeUpdate();
                    }
                }
                c.commit();
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }
    private static boolean dml(String sql) {
        String normalized = sql.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(java.util.Locale.ROOT);
        return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(normalized::startsWith);
    }
    private static ProductRow row(ProductData p) { return new ProductRow(p.id(), p.name(), p.description(), p.image(), p.price()); }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmpty() {
        var s = database.snapshot(); assertThat(s.products()).isEmpty(); assertThat(s.categories()).isEmpty();
        assertThat(s.associations()).isEmpty(); assertThat(s.orders()).isEmpty(); assertThat(s.items()).isEmpty(); assertThat(database.accountIds()).isEmpty();
    }
    enum Identity { ADMIN(200), ANONYMOUS(401), CLIENT(403), NONE(403); final int status; Identity(int status) { this.status = status; } }
    enum Operation {
        LIST("GET", 200), DETAIL("GET", 200), CATEGORIES("GET", 200), POST("POST", 201), PUT("PUT", 200), DELETE("DELETE", 204);
        final String method; final int status;
        Operation(String method, int status) { this.method = method; this.status = status; }
        String route(String id) { return this == CATEGORIES ? "/admin/categories" : this == DETAIL || this == PUT || this == DELETE ? "/admin/products/" + id : "/admin/products"; }
        boolean writes() { return this == POST || this == PUT || this == DELETE; }
        Set<HttpMethod> supported() { return this == CATEGORIES ? Set.of(HttpMethod.GET)
                : this == LIST || this == POST ? Set.of(HttpMethod.GET, HttpMethod.POST) : Set.of(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE); }
    }
    enum HttpCase {
        METHOD_COLLECTION(Operation.LIST, "DELETE", 405, "application/json", "application/json"),
        METHOD_ITEM(Operation.DETAIL, "PATCH", 405, "application/json", "application/json"),
        METHOD_CATEGORY(Operation.CATEGORIES, "POST", 405, "application/json", "application/json"),
        VENDOR_POST(Operation.POST, "POST", 406, VENDOR, "application/json"),
        VENDOR_PUT(Operation.PUT, "PUT", 406, VENDOR, "application/json"),
        XML_LIST(Operation.LIST, "GET", 406, "application/xml", "application/json"),
        XML_DETAIL(Operation.DETAIL, "GET", 406, "application/xml", "application/json"),
        XML_POST(Operation.POST, "POST", 406, "application/xml", "application/json"),
        XML_PUT(Operation.PUT, "PUT", 406, "application/xml", "application/json"),
        CONTENT_POST_TEXT(Operation.POST, "POST", 415, "application/json", "text/plain"),
        CONTENT_PUT_TEXT(Operation.PUT, "PUT", 415, "application/json", "text/plain"),
        CONTENT_POST_XML(Operation.POST, "POST", 415, "application/json", "application/xml"),
        CONTENT_PUT_XML(Operation.PUT, "PUT", 415, "application/json", "application/xml"),
        BODY_POST(Operation.POST, "POST", 400, "application/json", "application/json"),
        BODY_PUT(Operation.PUT, "PUT", 400, "application/json", "application/json");
        final Operation operation; final String method, accept, contentType; final int status;
        HttpCase(Operation operation, String method, int status, String accept, String contentType) {
            this.operation = operation; this.method = method; this.status = status; this.accept = accept; this.contentType = contentType;
        }
        Class<? extends Exception> exceptionType() { return status == 405 ? HttpRequestMethodNotSupportedException.class
                : status == 406 ? HttpMediaTypeNotAcceptableException.class : status == 415 ? HttpMediaTypeNotSupportedException.class : HttpMessageNotReadableException.class; }
    }
    record ParameterCase(String name, Operation operation, String id, String field, String queryKey, List<String> values) { }

    static Stream<Arguments> sensitiveBodies() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of("POST", "PUT")
                .flatMap(method -> Stream.of(false, true).map(broken -> Arguments.of(prefix, method, broken))));
    }

    @ParameterizedTest(name = "sanitized input context={0} method={1} broken={2}")
    @MethodSource("sensitiveBodies")
    void sanitizesActualBodyPathFieldsAndNormalLogs(String prefix, String method, boolean broken) throws Exception {
        String token = login(Identity.ADMIN), canary = "T073_INPUT_" + java.util.UUID.randomUUID();
        String route = "/admin/products" + (method.equals("PUT") ? "/" + target.id() : "");
        var body = mapper.createObjectNode().put("name", "ab").put("description", canary)
                .put("price", "0").put("image", "/" + canary).put(canary, canary);
        body.putArray("categoryIds").add(Long.MAX_VALUE);
        String payload = broken ? "{\"" + canary + "\":\"" + canary : mapper.writeValueAsString(body);
        var before = database.snapshot(); String accounts = accountFingerprint(); sql.clear();
        var result = perform(request(HttpMethod.valueOf(method), prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON).content(payload));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(database.snapshot()).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(maintenanceCalls).isZero(); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        var returned = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(returned.path("errors").size()).isEqualTo(broken ? 1 : 5);
        System.out.printf("T073_INPUT status=400 broken=%s independentState=true%n", broken);
    }

    static Stream<Arguments> sensitiveFailures() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(SensitiveFailure.values())
                .map(kind -> Arguments.of(prefix, kind)));
    }

    @ParameterizedTest(name = "sanitized exception context={0} kind={1}")
    @MethodSource("sensitiveFailures")
    void sanitizesExceptionDetailsWithoutReclassifyingInternalFailures(String prefix, SensitiveFailure kind) throws Exception {
        String token = login(Identity.ADMIN), canary = "T073_FAILURE_" + java.util.UUID.randomUUID();
        String details = canary + " " + token + " " + context.getEnvironment().getRequiredProperty("security.jwt.secret-base64")
                + " select password_hash from tb_user_account\n\tat private.Internal.call(Internal.java:1)";
        var query = (Advised) context.getBean(com.devsuperior.dscommerce.services.AdminProductQueryService.class);
        MethodInterceptor failure = invocation -> { throw kind.exception(details); };
        var before = database.snapshot(); String accounts = accountFingerprint(); sql.clear();
        query.addAdvice(0, failure);
        try {
            var result = perform(request(HttpMethod.GET, prefix + "/admin/products").contextPath(prefix).servletPath("/admin/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).queryParam("name", canary).accept(MediaType.APPLICATION_JSON));
            assertThat(result.getResponse().getStatus()).isEqualTo(kind.status);
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(kind.status == 401 ? "Bearer" : null);
            assertThat(database.snapshot()).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
            assertThat(maintenanceCalls).isZero(); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
            System.out.printf("T073_FAILURE kind=%s status=%d independentState=true%n", kind, kind.status);
        } finally { assertThat(query.removeAdvice(failure)).isTrue(); }
    }

    enum SensitiveFailure {
        INTERNAL(500), VALIDATION_INTERNAL(500), AUTHENTICATION(401), AUTHORIZATION(403), INTERNAL_WITH_SECURITY_CAUSE(500);
        final int status;
        SensitiveFailure(int status) { this.status = status; }
        RuntimeException exception(String detail) {
            return switch (this) {
                case INTERNAL -> new IllegalStateException(detail);
                case VALIDATION_INTERNAL -> new jakarta.validation.ValidationException(detail, new IllegalStateException(detail));
                case AUTHENTICATION -> new jakarta.validation.ValidationException(detail,
                        new org.springframework.security.authentication.BadCredentialsException(detail));
                case AUTHORIZATION -> new jakarta.validation.ValidationException(detail,
                        new org.springframework.security.access.AccessDeniedException(detail));
                case INTERNAL_WITH_SECURITY_CAUSE -> new jakarta.validation.ValidationException(detail,
                        new com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException(detail,
                                new org.springframework.security.authentication.BadCredentialsException(detail)));
            };
        }
    }

    static Stream<Arguments> unwritableNativeCases() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(HttpCase.METHOD_COLLECTION,
                HttpCase.CONTENT_POST_TEXT, HttpCase.CONTENT_PUT_TEXT).map(sample -> Arguments.of(prefix, sample)));
    }

    @ParameterizedTest(name = "unwritable native context={0} request={1}")
    @MethodSource("unwritableNativeCases")
    void unwritableNativeErrorsKeepStatusHeadersWithoutLoggingStackTraces(String prefix, HttpCase sample) throws Exception {
        String token = login(Identity.ADMIN), route = sample.operation.route(Long.toString(target.id()));
        var before = database.snapshot(); String accounts = accountFingerprint(); observer.seen = null; sql.clear();
        var result = perform(request(HttpMethod.valueOf(sample.method), prefix + route).contextPath(prefix).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_XML)
                .contentType(sample.contentType).content(validBody()));
        assertThat(result.getResponse().getStatus()).isEqualTo(sample.status);
        assertThat(observer.seen).isNotNull(); assertThat(observer.seen.failure()).isInstanceOf(sample.exceptionType());
        assertThat(result.getResponse().getHeaders(HttpHeaders.ALLOW)).isEqualTo(observer.seen.headers().getOrEmpty(HttpHeaders.ALLOW));
        assertThat(result.getResponse().getHeaders(HttpHeaders.ACCEPT)).isEqualTo(observer.seen.headers().getOrEmpty(HttpHeaders.ACCEPT));
        assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(database.snapshot()).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(maintenanceCalls).isZero(); assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        System.out.printf("T073_NATIVE status=%d headersPreserved=true bodyEmpty=true independentState=true%n", sample.status);
    }

    /** Shared only by the two T073 classes. Values stay in memory; assertion failures never print them. */
    static final class SanitizationProbe {
        private static final String CONTROL = "T073 normal-log capture control";
        static MvcResult perform(WebApplicationContext context, java.util.concurrent.Callable<MvcResult> action) throws Exception {
            var forbidden = new ArrayList<String>();
            var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            for (String query : List.of("select name,password_hash from tb_user_account", "select name,description,image from tb_product",
                    "select name from tb_category")) {
                jdbc.queryForList(query).forEach(row -> row.values().forEach(value -> { if (value != null) forbidden.add(value.toString()); }));
            }
            String key = context.getEnvironment().getRequiredProperty("security.jwt.secret-base64");
            forbidden.add(key);
            byte[] rawKey = java.util.Base64.getDecoder().decode(key);
            forbidden.add(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(rawKey));
            forbidden.add(java.util.HexFormat.of().formatHex(rawKey));
            forbidden.addAll(List.of("Disposable-T066-password", "Disposable-T066-decoy", "Disposable-T068-password",
                    BODY_CANARY, QUERY_CANARY, VALUE_CANARY, "ROLE_ADMIN", "ROLE_CLIENT"));
            var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
            capture.setContext(root.getLoggerContext()); capture.start(); root.addAppender(capture);
            MvcResult result;
            try {
                org.slf4j.LoggerFactory.getLogger(SanitizationProbe.class).info(CONTROL);
                result = action.call();
            } finally { root.detachAppender(capture); capture.stop(); }
            var request = result.getRequest();
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null) {
                forbidden.add(authorization);
                if (authorization.startsWith("Bearer ")) forbidden.add(authorization.substring(7));
            }
            byte[] inputBytes = request.getContentAsByteArray();
            String input = inputBytes == null ? null : new String(inputBytes, StandardCharsets.UTF_8);
            if (input != null && input.length() > 12) forbidden.add(input);
            var mapper = context.getBean(ObjectMapper.class);
            if (input != null && !input.isBlank()) {
                try { collectInput(mapper.readTree(input), forbidden); } catch (com.fasterxml.jackson.core.JsonProcessingException unexaminable) {
                    java.util.regex.Matcher markers = java.util.regex.Pattern.compile("T073_[A-Z]+_[a-f0-9-]+").matcher(input);
                    while (markers.find()) forbidden.add(markers.group());
                }
            }
            request.getParameterMap().values().forEach(values -> { for (String value : values) if (value.length() >= 8) forbidden.add(value); });
            String logs = capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(capture.list.stream().anyMatch(event -> event.getFormattedMessage().equals(CONTROL)))
                    .as("T073 normal-log capture is active at configured levels").isTrue();
            assertThat(discloses(logs, forbidden)).as("T073 normal logs must not disclose protected values or SQL").isFalse();
            assertThat(capture.list.stream().anyMatch(event -> event.getThrowableProxy() != null))
                    .as("T073 normal logs must not contain stack traces").isFalse();
            int status = result.getResponse().getStatus();
            if (status >= 400) {
                String actual = result.getResponse().getContentAsString();
                assertThat(discloses(actual, forbidden)).as("T073 actual returned body/path/errors must be sanitized").isFalse();
                assertThat(discloses(result.getResponse().getHeaderNames().stream()
                        .flatMap(name -> result.getResponse().getHeaders(name).stream()).collect(java.util.stream.Collectors.joining("\n")), forbidden))
                        .as("T073 error response headers must be sanitized").isFalse();
                if (actual.isEmpty()) assertThat(Set.of(405, 406, 415).contains(status)).as("T073 empty native error body only when not writable").isTrue();
                else {
                    JsonNode body;
                    try { body = mapper.readTree(actual); } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                        throw new AssertionError("T073 returned error is not safe JSON");
                    }
                    assertThat(discloses(body.toString(), forbidden)).as("T073 decoded error values must be sanitized").isFalse();
                    assertThat(keys(body).equals(Set.of("status", "error", "message", "path", "errors")))
                            .as("T073 actual envelope has only contracted keys").isTrue();
                    assertThat(body.path("status").asInt() == status && body.path("error").asText().equals(HttpStatus.valueOf(status).getReasonPhrase()))
                            .as("T073 actual envelope keeps status/reason").isTrue();
                    assertThat(body.path("path").asText().equals(request.getRequestURI())).as("T073 external path excludes query").isTrue();
                    String expectedMessage = status == 400 ? "Invalid product maintenance data" : status == 401 ? "Authentication required or invalid"
                            : status == 403 ? "Product maintenance not permitted" : status == 500 ? "Product maintenance could not be completed"
                            : "Product maintenance request not supported";
                    assertThat(body.path("message").asText().equals(expectedMessage)).as("T073 actual message is fixed and safe").isTrue();
                    assertThat(body.path("errors").isArray()).as("T073 actual errors is an array").isTrue();
                    for (JsonNode error : body.path("errors")) {
                        assertThat(keys(error).equals(Set.of("field", "code", "message"))).as("T073 actual field error has no rejected value").isTrue();
                        assertThat(error.path("field").asText().matches("body|id|page|size|name|description|price|image|categoryIds(\\[[0-9]+\\])?"))
                                .as("T073 field identifier is contracted").isTrue();
                    }
                }
            }
            System.out.printf("T073_SAFE status=%d bodyChecked=%s logEvents=%d captureControl=true sensitiveValuesPrinted=false%n",
                    status, status >= 400, capture.list.size());
            return result;
        }
        private static Set<String> keys(JsonNode node) {
            var keys = new HashSet<String>(); node.fieldNames().forEachRemaining(keys::add); return keys;
        }
        private static void collectInput(JsonNode node, List<String> forbidden) {
            if (node == null) return;
            if (node.isTextual() && node.asText().length() >= 8) forbidden.add(node.asText());
            if (node.isObject()) node.fields().forEachRemaining(entry -> {
                if (!Set.of("name", "description", "price", "image", "categoryIds").contains(entry.getKey()) && entry.getKey().length() >= 8)
                    forbidden.add(entry.getKey());
                collectInput(entry.getValue(), forbidden);
            });
            else if (node.isArray()) node.forEach(value -> collectInput(value, forbidden));
        }
        private static boolean discloses(String actual, List<String> forbidden) {
            return forbidden.stream().filter(value -> value.length() >= 8).anyMatch(actual::contains)
                    || java.util.regex.Pattern.compile("(?is)\\bselect\\b.{0,2000}\\bfrom\\b|\\binsert\\s+into\\b|\\bdelete\\s+from\\b|\\bupdate\\s+tb_|\\btb_(product|category|user_account|order)\\b|password_hash|SQLException|SQLState|stackTrace|Caused by:|\\bat [\\w.$]+\\([^)]*\\.java:[0-9]+\\)|\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                    .matcher(actual).find();
        }
    }
    record Seen(Exception failure, HttpHeaders headers, boolean admin) { }
    static class Observer implements HandlerExceptionResolver, Ordered {
        Seen seen;
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
            if (!request.getRequestURI().startsWith(request.getContextPath() + "/admin/")) return null;
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean admin = authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal
                    && authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
            var headers = new HttpHeaders();
            if (failure instanceof ErrorResponse error) error.getHeaders().forEach((key, values) -> headers.put(key, List.copyOf(values)));
            seen = new Seen(failure, headers, admin); return null;
        }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean Observer t068FrameworkObserver() { return new Observer(); }
        @Bean AdminProductDatabaseProbe.SqlObservation t068SqlObservation() { return new AdminProductDatabaseProbe.SqlObservation(); }
        @Bean HibernatePropertiesCustomizer t068SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
