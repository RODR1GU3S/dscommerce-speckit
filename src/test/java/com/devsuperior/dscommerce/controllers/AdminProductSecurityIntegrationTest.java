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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/** T066: real filters/login/decoder/services/H2, without an outer test transaction or mock users. */
@SpringBootTest(classes = DscommerceApplication.class,
        properties = {"spring.jpa.open-in-view=false", "logging.level.root=INFO"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductSecurityIntegrationTest.SqlConfig.class)
class AdminProductSecurityIntegrationTest {
    private static final String RESTRICTED_NAME = "T066 restricted product";
    private static final String RESTRICTED_DESCRIPTION = "T066 restricted description";
    private static final String RESTRICTED_CATEGORY = "T066 restricted category";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private OrderService orders;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private AdminProductDatabaseProbe.SqlObservation sql;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private ProductData target;
    private ProductData sold;
    private long categoryA, categoryB;
    private final Set<Long> ownedOrders = new HashSet<>();
    private final Set<Long> createdProducts = new HashSet<>();

    @BeforeEach
    void prepareCommittedOwnedCatalogAndHistory() {
        outsideTransaction();
        assertThat(AopUtils.isAopProxy(orders)).isTrue();
        assertThat(AopUtils.isAopProxy(maintenance)).isTrue();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmpty();
        fixture = new AdminProductTestFixture(context); ownedOrders.clear(); createdProducts.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(
                context.getBean(com.devsuperior.dscommerce.security.AdminDiagnosticLoggingFilter.class), context.getBean(FilterChainProxy.class)).build();
        categoryA = fixture.createCategory(RESTRICTED_CATEGORY).id();
        categoryB = fixture.createCategory(RESTRICTED_CATEGORY).id();
        fixture.createCategory("T066 unused category");
        target = fixture.createProduct(RESTRICTED_NAME, RESTRICTED_DESCRIPTION,
                "https://example.invalid/t066-target.jpg", new BigDecimal("10.00"));
        sold = fixture.createProduct("T066 other product", "T066 other description",
                "https://example.invalid/t066-other.jpg", new BigDecimal("3.25"));
        fixture.associate(target.id(), categoryA); fixture.associate(target.id(), categoryB);
        fixture.associate(sold.id(), categoryA);
        var buyer = fixture.prepareBuyer();
        assertThat(database.roles(buyer.id())).containsExactly(UserRole.CLIENT);
        try {
            var order = orders.register(new OrderRequestDTO(List.of(new OrderItemRequestDTO(sold.id(), 3))),
                    new AuthenticatedAccountPrincipal(buyer.id()));
            assertThat(order.total()).isEqualTo("9.75");
        } finally { database.snapshot().orders().forEach(order -> ownedOrders.add(order.id())); }
        var prepared = database.snapshot();
        assertThat(prepared.products()).containsExactly(row(target), row(sold));
        assertThat(prepared.categories()).hasSize(3);
        assertThat(prepared.associations()).containsExactlyInAnyOrder(new ProductCategory(target.id(), categoryA),
                new ProductCategory(target.id(), categoryB), new ProductCategory(sold.id(), categoryA));
        assertThat(prepared.orders()).hasSize(1);
        assertThat(prepared.orders().getFirst().buyerId()).isEqualTo(buyer.id());
        assertThat(prepared.orders().getFirst().createdAt()).isNotNull();
        assertThat(prepared.orders().getFirst().status()).isEqualTo("WAITING_PAYMENT");
        assertThat(prepared.items()).hasSize(1);
        var item = prepared.items().getFirst();
        assertThat(item.orderId()).isEqualTo(prepared.orders().getFirst().id());
        assertThat(item.productId()).isEqualTo(sold.id()); assertThat(item.position()).isZero();
        assertThat(item.quantity()).isEqualTo(3); assertThat(item.unitPrice()).isEqualTo(new BigDecimal("3.25"));
        assertThat(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))).isEqualTo(new BigDecimal("9.75"));
        assertThat(prepared.items()).noneMatch(line -> line.productId() == target.id());
        outsideTransaction();
    }

    static Stream<Arguments> authorizedOperations() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(Identity.ADMIN, Identity.BOTH)
                .flatMap(identity -> Stream.of(Operation.values()).map(operation -> Arguments.of(prefix, identity, operation))));
    }

    @ParameterizedTest(name = "authorized context={0} identity={1} operation={2}")
    @MethodSource("authorizedOperations")
    void currentAdminWithOrWithoutClientCanPerformEachOperation(String prefix, Identity identity, Operation operation) throws Exception {
        String token = credential(identity);
        var before = database.snapshot(); var accounts = accountFingerprint(); sql.clear();
        var result = perform(call(prefix, operation, false).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        outsideTransaction(); var after = database.snapshot();
        assertThat(result.getResponse().getStatus()).isEqualTo(operation.status);
        assertThat(result.getHandler()).isNotNull(); assertThat(result.getResolvedException()).isNull();
        assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.orders()).isEqualTo(before.orders()); assertThat(after.items()).isEqualTo(before.items());
        if (operation == Operation.POST || operation == Operation.PUT) {
            var body = mapper.readTree(result.getResponse().getContentAsByteArray());
            long id = body.path("id").longValue();
            assertThat(id).isPositive();
            if (operation == Operation.POST) assertThat(id).isNotIn(target.id(), sold.id());
            else assertThat(id).isEqualTo(target.id());
            fields(body, "id", "name", "description", "image", "price", "categories");
            assertThat(body.path("name").asText()).isEqualTo("T066 changed product");
            assertThat(body.path("description").asText()).isEqualTo("T066 changed description");
            assertThat(body.path("image").asText()).isEqualTo("https://example.invalid/t066-new.jpg");
            assertThat(body.path("price").isTextual()).isTrue(); assertThat(body.path("price").asText()).isEqualTo("16.25");
            assertThat(body.path("categories")).hasSize(1);
            assertThat(body.path("categories").get(0).path("id").longValue()).isEqualTo(categoryB);
            assertThat(body.path("categories").get(0).path("name").asText()).isEqualTo(RESTRICTED_CATEGORY);
            var expectedProducts = new ArrayList<>(before.products());
            if (operation == Operation.PUT) expectedProducts.remove(row(target));
            expectedProducts.add(new ProductRow(id, "T066 changed product", "T066 changed description",
                    "https://example.invalid/t066-new.jpg", new BigDecimal("16.25")));
            expectedProducts.sort(java.util.Comparator.comparingLong(ProductRow::id));
            var pairs = new HashSet<>(before.associations());
            if (operation == Operation.PUT) pairs.removeIf(pair -> pair.productId() == target.id());
            pairs.add(new ProductCategory(id, categoryB));
            assertThat(after).isEqualTo(new Snapshot(expectedProducts, before.categories(), pairs, before.orders(), before.items()));
            if (operation == Operation.POST) {
                var location = java.net.URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION));
                assertThat(location.getPath()).isEqualTo(prefix + "/admin/products/" + id);
                assertThat(location.getRawQuery()).isNull(); assertThat(location.getRawFragment()).isNull();
            }
        } else if (operation == Operation.DELETE) {
            assertThat(result.getResponse().getContentAsByteArray()).isEmpty();
            assertThat(result.getResponse().getContentType()).isNull();
            var pairs = new HashSet<>(before.associations()); pairs.removeIf(pair -> pair.productId() == target.id());
            assertThat(after).isEqualTo(new Snapshot(List.of(row(sold)), before.categories(), pairs, before.orders(), before.items()));
        } else {
            assertThat(after).isEqualTo(before);
            assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
            var body = mapper.readTree(result.getResponse().getContentAsByteArray());
            if (operation == Operation.LIST) {
                fields(body, "content", "page", "size", "totalElements", "totalPages");
                assertThat(body.path("totalElements").asInt()).isEqualTo(1);
                assertThat(body.path("content")).hasSize(1);
                assertThat(body.path("content").get(0).path("id").longValue()).isEqualTo(target.id());
                assertThat(body.path("content").get(0).path("price").asText()).isEqualTo("10.00");
            } else if (operation == Operation.DETAIL) {
                assertThat(body.path("id").longValue()).isEqualTo(target.id());
                assertThat(body.path("name").asText()).isEqualTo(RESTRICTED_NAME);
                assertThat(body.path("description").asText()).isEqualTo(RESTRICTED_DESCRIPTION);
                assertThat(body.path("categories")).hasSize(2);
            } else {
                assertThat(body).hasSize(3);
                assertThat(body.findValues("id").stream().map(JsonNode::longValue).toList()).contains(categoryA, categoryB);
            }
        }
        if (operation != Operation.POST) assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        System.out.printf("T066_AUTH context=%s identity=%s operation=%s status=%d independentState=true history=9.75%n",
                prefix.isEmpty() ? "ROOT" : prefix, identity, operation, operation.status);
    }

    static Stream<Arguments> rejectedOperations() {
        return Stream.of("", "/api").flatMap(prefix -> Stream.of(Identity.values()).filter(identity -> identity.status != 200)
                .flatMap(identity -> Stream.of(Operation.values()).flatMap(operation -> Stream.of(false, true)
                        .map(invalid -> Arguments.of(prefix, identity, operation, invalid)))));
    }

    @ParameterizedTest(name = "rejected context={0} identity={1} operation={2} invalidData={3}")
    @MethodSource("rejectedOperations")
    void rejectsBeforeMvcWithoutWritingOrDisclosingRestrictedData(String prefix, Identity identity,
                                                                 Operation operation, boolean invalidData) throws Exception {
        String token = credential(identity);
        var call = call(prefix, operation, invalidData);
        if (token != null) call.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        var before = database.snapshot(); var accounts = accountFingerprint(); sql.clear();
        var result = perform(call);
        outsideTransaction(); var after = database.snapshot();
        assertThat(after).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
        assertThat(sql.events()).noneMatch(event -> dml(event.sql()));
        assertThat(result.getHandler()).as("security must reject before the real controller or validation").isNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(identity.status);
        var expected = mapper.createObjectNode().put("status", identity.status)
                .put("error", HttpStatus.valueOf(identity.status).getReasonPhrase())
                .put("message", identity.status == 401 ? "Authentication required or invalid" : "Product maintenance not permitted")
                .put("path", prefix + operation.route(target.id()));
        expected.putArray("errors");
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(expected);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(identity.status == 401 ? "Bearer" : null);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(RESTRICTED_NAME, RESTRICTED_DESCRIPTION,
                RESTRICTED_CATEGORY, "CATEGORY_NOT_FOUND", "categoryIds");
        System.out.printf("T066_REJECT context=%s identity=%s operation=%s invalidData=%s status=%d beforeMvc=true ormDml=0 independentSnapshotEqual=true%n",
                prefix.isEmpty() ? "ROOT" : prefix, identity, operation, invalidData, identity.status);
    }

    private MockHttpServletRequestBuilder call(String prefix, Operation operation, boolean invalid) throws Exception {
        String route = operation.route(target.id());
        var call = request(HttpMethod.valueOf(operation.method), prefix + route).contextPath(prefix).servletPath(route)
                .accept(MediaType.APPLICATION_JSON);
        if (operation == Operation.POST || operation == Operation.PUT) {
            var body = mapper.createObjectNode().put("name", invalid ? "ab" : "T066 changed product")
                    .put("description", invalid ? "123456789" : "T066 changed description")
                    .put("price", invalid ? "0" : "16.25")
                    .put("image", invalid ? "/relative" : "https://example.invalid/t066-new.jpg");
            body.putArray("categoryIds").add(invalid ? Long.MAX_VALUE : categoryB);
            call.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        } else if (invalid) call.queryParam("page", "-1").queryParam("T066_INVALID_PARAMETER", "invalid");
        else if (operation == Operation.LIST) call.queryParam("name", RESTRICTED_NAME);
        return call;
    }

    static Stream<String> lookupSanitizationContexts() { return Stream.of("", "/api"); }

    @ParameterizedTest(name = "sanitized real lookup failure context={0}")
    @MethodSource("lookupSanitizationContexts")
    void realLookupFailureDoesNotExposeSqlOrAccountsInNormalLogs(String prefix) throws Exception {
        String token = credential(Identity.ADMIN);
        var before = database.snapshot(); String accounts = accountFingerprint();
        var jdbc = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
        var result = AdminProductFrameworkHttpErrorIntegrationTest.SanitizationProbe.perform(context, () -> {
            jdbc.execute("alter table tb_user_account rename to t073_account_unavailable");
            MvcResult response;
            try {
                assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='TB_USER_ACCOUNT'", Integer.class)).isZero();
                response = mvc.perform(request(HttpMethod.GET, prefix + "/admin/products").contextPath(prefix).servletPath("/admin/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON)).andReturn();
            } finally { jdbc.execute("alter table t073_account_unavailable rename to tb_user_account"); }
            assertThat(response.getResponse().getStatus()).isEqualTo(500);
            assertThat(response.getHandler()).isNull(); assertThat(response.getResolvedException()).isNull();
            assertThat(database.snapshot()).isEqualTo(before); assertThat(accountFingerprint()).isEqualTo(accounts);
            System.out.println("T073_LOOKUP_REACHED status=500 independentState=true beforeDml=true");
            return response;
        });
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        System.out.println("T073_LOOKUP_SAFE status=500 actualFilterAndDatabase=true");
    }

    static Stream<Arguments> diagnosticLoggingScopes() {
        return Stream.of(new Object[]{"", "/admin/products", true}, new Object[]{"/api", "/admin/products", true},
                new Object[]{"", "/products", false}, new Object[]{"/api", "/administrator/products", false},
                new Object[]{"/admin", "/products", false}, new Object[]{"", "/login", false})
                .flatMap(route -> Stream.of(false, true).map(failing -> Arguments.of(route[0], route[1], route[2], failing)));
    }

    @ParameterizedTest(name = "diagnostic log scope context={0} path={1} administrative={2} failing={3}")
    @MethodSource("diagnosticLoggingScopes")
    void diagnosticLoggingScopePreservesSeverityAndRestoresThreadState(String prefix, String path, boolean administrative, boolean failing) throws Exception {
        var filter = context.getBean(com.devsuperior.dscommerce.security.AdminDiagnosticLoggingFilter.class);
        var root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var source = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.hibernate.engine.jdbc.spi.SqlExceptionHelper");
        var sourceLevel = source.getLevel(); var rootLevel = root.getLevel();
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.setContext(root.getLoggerContext()); capture.start(); root.addAppender(capture);
        String harmless = "T073 harmless diagnostic control";
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setContextPath(prefix); request.setRequestURI(prefix + path);
        var sentinel = new jakarta.servlet.ServletException("T073 harmless chain failure");
        try {
            Exception caught = null;
            try {
                filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(), (req, response) -> {
                    source.warn(harmless); source.error(harmless);
                    if (failing) throw sentinel;
                });
            } catch (jakarta.servlet.ServletException failure) { caught = failure; }
            assertThat(caught == (failing ? sentinel : null)).as("T073 logging adapter preserves chain outcome").isTrue();
            assertThat(capture.list.size() == 2).as("T073 neither diagnostic event is discarded").isTrue();
            assertThat(capture.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getLevel).toList()
                    .equals(List.of(ch.qos.logback.classic.Level.WARN, ch.qos.logback.classic.Level.ERROR)))
                    .as("T073 diagnostic severity is preserved").isTrue();
            String expected = administrative ? "Administrative persistence failure" : harmless;
            assertThat(capture.list.stream().allMatch(event -> event.getFormattedMessage().equals(expected) && event.getThrowableProxy() == null))
                    .as("T073 only administrative diagnostics are adapted").isTrue();
            capture.list.clear(); source.warn(harmless); source.error(harmless);
            assertThat(capture.list.size() == 2 && capture.list.stream().allMatch(event -> event.getFormattedMessage().equals(harmless)))
                    .as("T073 request state is removed after success and exception").isTrue();
            assertThat(java.util.Objects.equals(sourceLevel, source.getLevel()) && java.util.Objects.equals(rootLevel, root.getLevel()))
                    .as("T073 no logging level is changed").isTrue();
            System.out.printf("T073_SCOPE administrative=%s failing=%s normalLevelsPreserved=true stateRestored=true%n", administrative, failing);
        } finally { root.detachAppender(capture); capture.stop(); }
    }

    private MvcResult perform(MockHttpServletRequestBuilder call) throws Exception {
        var priorIds = database.snapshot().products().stream().map(ProductRow::id).toList();
        try { return AdminProductFrameworkHttpErrorIntegrationTest.SanitizationProbe.perform(context, () -> mvc.perform(call).andReturn()); }
        finally {
            outsideTransaction();
            database.snapshot().products().stream().filter(product -> !priorIds.contains(product.id()))
                    .forEach(product -> createdProducts.add(product.id()));
        }
    }

    /** Positive credentials always come from /login. Hand-signed credentials are negative controls only. */
    private String credential(Identity identity) throws Exception {
        if (identity == Identity.ANONYMOUS) return null;
        Set<UserRole> roles = switch (identity) {
            case BOTH -> Set.of(UserRole.ADMIN, UserRole.CLIENT);
            case CLIENT, FORGED_CLIENT -> Set.of(UserRole.CLIENT);
            case NONE, FORGED_NONE -> Set.of();
            default -> Set.of(UserRole.ADMIN);
        };
        var account = fixture.createAccount("T066 account " + identity, "Disposable-T066-password", roles);
        assertThat(database.roles(account.id())).containsExactlyInAnyOrderElementsOf(roles);
        String realLogin = fixture.login(account);
        if (identity == Identity.ADMIN || identity == Identity.BOTH || identity == Identity.CLIENT || identity == Identity.NONE) return realLogin;
        if (identity == Identity.REMOVED_ACCOUNT) {
            try (var connection = database.openConnection()) {
                connection.setAutoCommit(false);
                try (var role = connection.prepareStatement("delete from tb_user_account_role where user_account_id=?");
                     var user = connection.prepareStatement("delete from tb_user_account where id=?")) {
                    role.setLong(1, account.id()); role.executeUpdate(); user.setLong(1, account.id());
                    assertThat(user.executeUpdate()).isEqualTo(1); connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            }
            assertThat(database.accountIds()).doesNotContain(account.id());
            return realLogin; // Removed before the filter; no T067 between-layer race is simulated here.
        }
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issueTime(Date.from(now.minusSeconds(600)))
                .expirationTime(Date.from(identity == Identity.EXPIRED ? now.minusSeconds(120) : now.plusSeconds(600)));
        if (identity != Identity.MISSING_SUB) claims.subject(identity == Identity.ABSENT_ACCOUNT ? Long.toString(Long.MAX_VALUE)
                : identity == Identity.MALFORMED_SUB ? "not-an-account-id" : Long.toString(account.id()));
        if (identity == Identity.ABSENT_ACCOUNT) assertThat(database.accountIds()).doesNotContain(Long.MAX_VALUE);
        if (identity == Identity.FORGED_CLIENT || identity == Identity.FORGED_NONE) {
            var decoy = fixture.createAccount("T066 forged ADMIN name", "Disposable-T066-decoy", Set.of(UserRole.ADMIN));
            assertThat(database.roles(decoy.id())).containsExactly(UserRole.ADMIN);
            claims.claim("name", decoy.name()).claim("roles", List.of("ADMIN", "CLIENT"))
                    .claim("authorities", List.of("ROLE_ADMIN")).claim("scope", "ADMIN");
        }
        byte[] realKey = Base64.getDecoder().decode(context.getEnvironment().getRequiredProperty("security.jwt.secret-base64"));
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(identity == Identity.INVALID_SIGNATURE ? new byte[32] : realKey));
        assertThat(jwt.verify(new MACVerifier(realKey))).isEqualTo(identity != Identity.INVALID_SIGNATURE);
        return jwt.serialize();
    }

    /** Compare all account/role fields without putting names, password hashes or roles in assertion output. */
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
    private static boolean dml(String sql) {
        String normalized = sql.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(java.util.Locale.ROOT);
        return List.of("insert ", "update ", "delete ", "merge ").stream().anyMatch(normalized::startsWith);
    }
    private static ProductRow row(ProductData p) { return new ProductRow(p.id(), p.name(), p.description(), p.image(), p.price()); }
    private static void fields(JsonNode body, String... expected) {
        var fields = new ArrayList<String>(); body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }
    private static void outsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmpty() {
        var s = database.snapshot(); assertThat(s.products()).isEmpty(); assertThat(s.categories()).isEmpty();
        assertThat(s.associations()).isEmpty(); assertThat(s.orders()).isEmpty(); assertThat(s.items()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }
    @AfterEach
    void cleanOnlyOwnedDataAfterAssertions() throws Exception {
        outsideTransaction(); if (fixture == null) return;
        try (var c = database.openConnection()) {
            c.setAutoCommit(false);
            try (var items = c.prepareStatement("delete from tb_order_item where order_id=?");
                 var order = c.prepareStatement("delete from tb_order where id=?")) {
                for (long id : ownedOrders) { items.setLong(1, id); items.executeUpdate(); order.setLong(1, id); order.executeUpdate(); }
                c.commit();
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
        // Only IDs observed as new during this class's request, including an unexpected write on failure.
        try (var c = database.openConnection()) {
            c.setAutoCommit(false);
            try (var pairs = c.prepareStatement("delete from tb_product_category where product_id=?");
                 var product = c.prepareStatement("delete from tb_product where id=?")) {
                for (long id : createdProducts) {
                    pairs.setLong(1, id); pairs.executeUpdate(); product.setLong(1, id); product.executeUpdate();
                }
                c.commit();
            } catch (Exception failure) { c.rollback(); throw failure; }
        }
        fixture.close(); assertEmpty();
    }
    enum Identity {
        ADMIN(200), BOTH(200), CLIENT(403), NONE(403), ANONYMOUS(401), INVALID_SIGNATURE(401), EXPIRED(401),
        MISSING_SUB(401), MALFORMED_SUB(401), ABSENT_ACCOUNT(401), REMOVED_ACCOUNT(401), FORGED_CLIENT(403), FORGED_NONE(403);
        final int status; Identity(int status) { this.status = status; }
    }
    enum Operation {
        LIST("GET", 200), DETAIL("GET", 200), CATEGORIES("GET", 200), POST("POST", 201), PUT("PUT", 200), DELETE("DELETE", 204);
        final String method; final int status;
        Operation(String method, int status) { this.method = method; this.status = status; }
        String route(long id) { return this == CATEGORIES ? "/admin/categories"
                : this == DETAIL || this == PUT || this == DELETE ? "/admin/products/" + id : "/admin/products"; }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfig {
        @Bean AdminProductDatabaseProbe.SqlObservation t066SqlObservation() { return new AdminProductDatabaseProbe.SqlObservation(); }
        @Bean HibernatePropertiesCustomizer t066SqlCustomizer(AdminProductDatabaseProbe.SqlObservation observation) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observation);
        }
    }
}
