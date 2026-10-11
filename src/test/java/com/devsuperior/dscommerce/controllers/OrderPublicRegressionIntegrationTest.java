package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.JwtProperties;
import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.OrderRepository;
import com.devsuperior.dscommerce.services.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.DatabaseState;
import static com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.ItemRow;
import static com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.OrderRow;
import static com.devsuperior.dscommerce.controllers.OrderRollbackIntegrationTest.SqlStep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Rejection, independent persistence proof and both public modes in one unchanged real context. */
@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc(addFilters = true, print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import({OrderTestConfig.class, OrderRollbackIntegrationTest.SqlObservationConfig.class,
        OrderRollbackIntegrationTest.RepositorySpyConfig.class})
class OrderPublicRegressionIntegrationTest extends OrderHttpTestSupport {

    private static final String PUBLIC_PASSWORD = "public-regression-test-password";
    private static final long MISSING_PRODUCT = Long.MAX_VALUE;

    @Autowired private OrderService service;
    @Autowired private OrderRepository repositorySpy;
    @Autowired private OrderRollbackIntegrationTest.SqlInsertProbe sqlProbe;
    @Autowired private OrderRollbackIntegrationTest.PostFlushProbe postFlush;
    @Autowired private ApplicationContext context;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProperties jwtProperties;

    private JdbcTemplate independent;
    private List<ProductRow> products;
    private List<?> categorySeed;
    private List<?> categoryLinks;
    private List<?> accountSeed;
    private int publicRequests;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        OrderTestConfig.isolatedDatabase(registry);
    }

    @BeforeEach
    void prepareIndependentObservationWithoutChangingThePublicSeed() {
        assertOutsideTransaction();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        assertThat(service).isInstanceOf(Advised.class);
        assertThat(mockingDetails(service).isMock()).isFalse();
        assertThat(mockingDetails(decoder).isMock()).isFalse();
        assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
        assertThat(jdbc.getDataSource()).isInstanceOf(OrderRollbackIntegrationTest.ObservedDataSource.class);
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        assertThat(url).startsWith("jdbc:h2:mem:order-test-");
        independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
        assertThat(independent.getDataSource()).isNotSameAs(jdbc.getDataSource());
        products = productSnapshot();
        assertThat(products).hasSize(52);
        categorySeed = independent.queryForList("select id, name from tb_category order by id");
        categoryLinks = independent.queryForList("select product_id, category_id from tb_product_category order by product_id, category_id");
        accountSeed = independent.queryForList("select id, name, password_hash from tb_user_account order by id");
        assertThat(categorySeed).hasSize(3);
        assertThat(categoryLinks).hasSize(3);
        assertThat(accountSeed).hasSize(1);
        assertThat(snapshot()).isEqualTo(new DatabaseState(List.of(), List.of()));
        sqlProbe.clear();
        postFlush.disarm();
        publicRequests = 0;
    }

    @AfterEach
    void disableObservationBeforeTheInheritedOwnedFixtureCleanup() {
        // Inherited cleanup deletes only owned order/account rows; no assertion relies on that cleanup.
        postFlush.disarm();
        sqlProbe.clear();
    }

    static Stream<Arguments> rejections() {
        return Stream.of(Rejection.values()).flatMap(rejection -> Stream.of("", "/api")
                .map(path -> Arguments.of(rejection, path)));
    }

    @ParameterizedTest(name = "public contracts after {0}, context={1}")
    @MethodSource("rejections")
    void shouldPreservePublicJourneysAfterTheRealOrderRejection(Rejection rejection, String contextPath)
            throws Exception {
        var originalContext = context;
        var originalFactory = entityManagerFactory;
        var originalFilters = securityFilters;
        var account = login(rejection == Rejection.NO_ROLE ? Set.of() : Set.of(UserRole.CLIENT));
        assertThat(independent.queryForObject("select count(*) from tb_user_account where id = ?", Long.class,
                account.id())).isEqualTo(1);
        assertThat(independent.queryForObject("select count(*) from tb_product where id = ?", Long.class,
                MISSING_PRODUCT)).isZero();
        var baseline = snapshot();
        var request = post(contextPath + "/orders").contextPath(contextPath).servletPath("/orders")
                .contentType(MediaType.APPLICATION_JSON).content(rejection.body());
        if (rejection != Rejection.NO_TOKEN) request.header(HttpHeaders.AUTHORIZATION,
                "Bearer " + (rejection == Rejection.MALFORMED_TOKEN ? "not-a-valid-jwt" : account.token()));
        assertChain(request, true);
        sqlProbe.arm(account.id(), null);
        if (rejection == Rejection.POST_FLUSH_FAILURE) {
            assertThat(mockingDetails(repositorySpy).isSpy()).isTrue();
            OrderService target = AopTestUtils.getUltimateTargetObject(service);
            assertThat(ReflectionTestUtils.getField(target, "orders")).isSameAs(repositorySpy);
            var realRepository = ReflectionTestUtils.getField(postFlush, "realRepository");
            assertThat(AopUtils.isAopProxy(realRepository)).isTrue();
            assertThat(mockingDetails(realRepository).isMock()).isFalse();
            postFlush.arm(account.id(), jdbc);
        }

        MvcResult rejected;
        DatabaseState after;
        List<SqlStep> sql;
        try {
            rejected = mvc.perform(request).andReturn();
        } finally {
            // A fresh, unbound connection after synchronous request completion, before any fixture cleanup.
            after = snapshot();
            sql = sqlProbe.steps();
            postFlush.disarm();
            sqlProbe.clear();
            System.out.printf("T046_PERSISTENCE case=%s context=%s statusExpected=%d independent=true "
                            + "ordersDelta=%d itemsDelta=%d baselineRestored=%s completedInserts=%d%n",
                    rejection, contextPath, rejection.status, after.orders().size() - baseline.orders().size(),
                    after.items().size() - baseline.items().size(), after.equals(baseline),
                    sql.stream().filter(SqlStep::completed).count());
            assertThat(after).as("after request, before disarming/cleanup, complete independent baseline").isEqualTo(baseline);
        }
        assertError(rejected, rejection.status, rejection.message, contextPath + "/orders");
        assertThat(rejected.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        if (rejection.status == 401) {
            assertThat(rejected.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        }
        if (rejection == Rejection.POST_FLUSH_FAILURE) assertRealFlushAndRollback(rejected, account.id(), sql);
        else assertThat(sql).as("pre-persistence rejection must execute no order/item insert").isEmpty();
        assertThat(ReflectionTestUtils.getField(postFlush, "buyer")).isNull();
        assertThat(ReflectionTestUtils.getField(postFlush, "transactionJdbc")).isNull();
        assertSeedPreserved();

        var publicName = "public-regression-" + UUID.randomUUID();
        var publicAccount = fixture.createAccount(publicName, PUBLIC_PASSWORD);
        ownedAccounts.add(publicAccount);
        assertThat(independent.queryForObject("select count(*) from tb_user_account_role where user_account_id = ?",
                Long.class, publicAccount)).isZero();
        for (boolean invalidBearer : List.of(false, true)) {
            int requestsBefore = publicRequests;
            verifyCatalog(contextPath, invalidBearer);
            verifyDetails(contextPath, invalidBearer);
            verifyLogin(contextPath, invalidBearer, publicAccount, publicName);
            assertThat(context).isSameAs(originalContext);
            assertThat(context.getBean(EntityManagerFactory.class)).isSameAs(originalFactory);
            assertThat(context.getBean("springSecurityFilterChain")).isSameAs(originalFilters);
            assertThat(snapshot()).as("public journeys cannot create orders/items").isEqualTo(baseline);
            assertSeedPreserved();
            System.out.printf("T046_PUBLIC case=%s context=%s invalidBearer=%s requests=%d catalog=52 "
                            + "details=real login=real noRoles=true contextSame=true baselineRestored=true%n",
                    rejection, contextPath, invalidBearer, publicRequests - requestsBefore);
        }
    }

    private void assertRealFlushAndRollback(MvcResult result, long buyer, List<SqlStep> sql) {
        assertThat(sql).hasSize(3).allMatch(SqlStep::completed)
                .allMatch(step -> step.affected() == 1 && step.transactionActive() && !step.autoCommit());
        assertThat(sql).extracting(SqlStep::table).containsExactly("tb_order", "tb_order_item", "tb_order_item");
        assertThat(sql.get(1).productId()).isEqualTo(1L);
        assertThat(sql.get(2).productId()).isEqualTo(2L);
        assertThat(sql.getLast().view().orders()).hasSize(1);
        assertThat(sql.getLast().view().orders().getFirst().buyerId()).isEqualTo(buyer);
        assertThat(sql.getLast().view().items()).hasSize(2);
        assertThat(sql.getLast().view().items()).extracting(ItemRow::productId).containsExactly(1L, 2L);
        assertThat(sql.getLast().view().items()).extracting(ItemRow::position).containsExactly(0, 1);
        assertThat(sql.getLast().view().items()).extracting(ItemRow::quantity).containsExactly(2, 3);
        assertThat(sql.getLast().view().items()).extracting(ItemRow::unitPrice)
                .containsExactly(product(1).price(), product(2).price());
        for (var flag : List.of("flushReturned", "transactionActive", "boundConnection", "exceptionThrown")) {
            assertThat(ReflectionTestUtils.getField(postFlush, flag)).as(flag).isEqualTo(true);
        }
        assertThat(ReflectionTestUtils.getField(postFlush, "autoCommit")).isEqualTo(false);
        assertThat(result.getResolvedException()).isSameAs(ReflectionTestUtils.getField(postFlush, "failure"))
                .isInstanceOf(OrderRollbackIntegrationTest.PostFlushTestFailure.class);
        assertThat(ReflectionTestUtils.getField(postFlush, "view")).isEqualTo(sql.getLast().view());
        System.out.println("T046_ROLLBACK completedInserts=3 orderRowsInTransaction=1 itemRowsInTransaction=2 "
                + "realFlushReturned=true boundConnection=true autoCommit=false fault=PostFlushTestFailure disarmed=true");
    }

    private void verifyCatalog(String path, boolean bearer) throws Exception {
        catalog(path, bearer, null, null, 0, 12);
        catalog(path, bearer, 1, 7, 1, 7);
        catalog(path, bearer, 0, 1, 0, 1);
        var first = catalog(path, bearer, 0, 50, 0, 50);
        var last = catalog(path, bearer, 1, 50, 1, 50);
        var ids = new ArrayList<Long>();
        first.path("content").forEach(item -> ids.add(item.path("id").longValue()));
        last.path("content").forEach(item -> ids.add(item.path("id").longValue()));
        assertThat(ids).hasSize(52).doesNotHaveDuplicates().containsExactlyElementsOf(products.stream().map(ProductRow::id).toList());
        catalog(path, bearer, 99, 12, 99, 12);
        for (var invalid : List.of(new String[]{"-1", "12", "page must be greater than or equal to 0"},
                new String[]{"0", "0", "size must be between 1 and 50"},
                new String[]{"0", "51", "size must be between 1 and 50"})) {
            var result = publicRequest(get(path + "/products"), path, "/products", bearer)
                    .param("page", invalid[0]).param("size", invalid[1]);
            assertError(performPublic(result), 400, invalid[2], path + "/products");
        }
    }

    private JsonNode catalog(String path, boolean bearer, Integer page, Integer size, int expectedPage, int expectedSize)
            throws Exception {
        var request = publicRequest(get(path + "/products"), path, "/products", bearer);
        if (page != null) request.param("page", page.toString());
        if (size != null) request.param("size", size.toString());
        var result = success(performPublic(request));
        assertFields(result, "content", "page", "size", "totalElements", "totalPages");
        for (var field : List.of("page", "size", "totalElements", "totalPages")) {
            assertThat(result.path(field).isIntegralNumber()).as(field).isTrue();
        }
        assertThat(result.path("page").intValue()).isEqualTo(expectedPage);
        assertThat(result.path("size").intValue()).isEqualTo(expectedSize);
        assertThat(result.path("totalElements").longValue()).isEqualTo(52);
        assertThat(result.path("totalPages").intValue()).isEqualTo((52 + expectedSize - 1) / expectedSize);
        assertThat(result.path("content").isArray()).isTrue();
        var start = Math.min(expectedPage * expectedSize, 52);
        var expected = products.subList(start, Math.min(start + expectedSize, 52));
        assertThat(result.path("content").size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) assertProduct(result.path("content").get(i), expected.get(i), false);
        return result;
    }

    private void verifyDetails(String path, boolean bearer) throws Exception {
        for (long id : List.of(1L, 2L, 3L)) {
            var logical = "/products/" + id;
            var result = success(performPublic(publicRequest(get(path + logical), path, logical, bearer)));
            assertProduct(result, product(id), true);
        }
        assertThat(product(1).categories()).containsExactly("Computers", "Electronics");
        assertThat(product(2).categories()).containsExactly("Accessories");
        assertThat(product(3).categories()).isEmpty();
        var missing = "/products/" + MISSING_PRODUCT;
        assertError(performPublic(publicRequest(get(path + missing), path, missing, bearer)),
                404, "Product not found", path + missing);
    }

    private void verifyLogin(String path, boolean bearer, long id, String name) throws Exception {
        var body = mapper.createObjectNode().put("name", name).put("password", PUBLIC_PASSWORD).toString();
        var result = success(performPublic(publicRequest(post(path + "/login"), path, "/login", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)));
        assertFields(result, "accessToken", "tokenType");
        assertThat(result.path("tokenType").textValue()).isEqualTo("Bearer");
        assertThat(result.path("accessToken").isTextual()).isTrue();
        var token = result.path("accessToken").textValue();
        assertThat(token.isBlank()).isFalse();
        var jwt = decodeWithoutLeakingToken(token);
        assertThat(jwt.getHeaders().get("alg")).hasToString("HS256");
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(id));
        assertThat(jwt.getClaimAsString("name")).isEqualTo(name);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(jwtProperties.getTtl());
        assertThat(jwt.getClaims().keySet()).doesNotContain("roles", "password", "passwordHash", "password_hash", "secret");
        var hash = independent.queryForObject("select password_hash from tb_user_account where id = ?", String.class, id);
        assertThat(passwordEncoder.matches(PUBLIC_PASSWORD, hash)).isTrue();
        for (var sensitive : List.of(PUBLIC_PASSWORD, hash, Base64.getEncoder().encodeToString(jwtProperties.getSecret()),
                new String(jwtProperties.getSecret(), StandardCharsets.UTF_8))) {
            assertThat(token.contains(sensitive) || jwt.getClaims().values().stream()
                    .anyMatch(value -> value.toString().contains(sensitive))).as("sensitive token/claim material").isFalse();
        }
        assertThat(independent.queryForObject("select count(*) from tb_user_account_role where user_account_id = ?",
                Long.class, id)).isZero();
        for (var invalid : List.of("{\"name\":\" \",\"password\":\"" + PUBLIC_PASSWORD + "\"}",
                "{\"name\":", "{\"name\":7,\"password\":\"" + PUBLIC_PASSWORD + "\"}",
                "{\"name\":\"" + name + "\",\"password\":\"" + PUBLIC_PASSWORD + "\",\"roles\":[]}", "{}")) {
            assertError(performPublic(publicRequest(post(path + "/login"), path, "/login", bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)), 400, "Invalid login data", path + "/login");
        }
        JsonNode unknown = null;
        for (var invalid : List.of(new String[]{"absent-" + name, PUBLIC_PASSWORD}, new String[]{name, "wrong-test-password"})) {
            var response = performPublic(publicRequest(post(path + "/login"), path, "/login", bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.createObjectNode()
                            .put("name", invalid[0]).put("password", invalid[1]).toString()));
            assertError(response, 401, "Invalid credentials", path + "/login");
            var parsed = mapper.readTree(response.getResponse().getContentAsString());
            if (unknown == null) unknown = parsed;
            else assertThat(parsed).isEqualTo(unknown);
        }
    }

    private MvcResult performPublic(MockHttpServletRequestBuilder request) throws Exception {
        assertOutsideTransaction();
        assertChain(request, false);
        publicRequests++;
        var result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        return result;
    }

    private void assertChain(MockHttpServletRequestBuilder request, boolean order) {
        var actual = request.buildRequest(mvc.getDispatcherServlet().getServletContext());
        var selected = securityFilters.getFilterChains().stream().filter(chain -> chain.matches(actual)).findFirst().orElseThrow();
        assertThat(selected.getFilters()).anyMatch(AuthorizationFilter.class::isInstance);
        assertThat(selected.getFilters().stream().anyMatch(BearerTokenAuthenticationFilter.class::isInstance)).isEqualTo(order);
    }

    private static MockHttpServletRequestBuilder publicRequest(MockHttpServletRequestBuilder request, String contextPath,
                                                               String logicalPath, boolean bearer) {
        request.contextPath(contextPath).servletPath(logicalPath);
        if (bearer) request.header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt");
        return request;
    }

    private JsonNode success(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertError(MvcResult result, int status, String message, String path) throws Exception {
        assertOutsideTransaction();
        assertThat(result.getResponse().getStatus()).isEqualTo(status).isNotEqualTo(201);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(mapper.readTree(result.getResponse().getContentAsString())).isEqualTo(mapper.createObjectNode()
                .put("status", status).put("error", HttpStatus.valueOf(status).getReasonPhrase()).put("message", message).put("path", path));
    }

    private void assertProduct(JsonNode actual, ProductRow expected, boolean detail) {
        if (detail) assertFields(actual, "id", "name", "description", "image", "price", "categories");
        else assertFields(actual, "id", "name", "image", "price");
        assertThat(actual.path("id").isIntegralNumber()).isTrue();
        assertThat(actual.path("id").longValue()).isEqualTo(expected.id());
        assertThat(actual.path("name").textValue()).isEqualTo(expected.name());
        assertThat(actual.path("image").textValue()).isEqualTo(expected.image());
        assertThat(actual.path("price").isNumber()).isTrue();
        assertThat(actual.path("price").decimalValue()).isEqualByComparingTo(expected.price());
        if (detail) {
            assertThat(actual.path("description").textValue()).isEqualTo(expected.description());
            assertThat(actual.path("categories").isArray()).isTrue();
            var names = new ArrayList<String>();
            actual.path("categories").forEach(category -> { assertThat(category.isTextual()).isTrue(); names.add(category.textValue()); });
            assertThat(names).containsExactlyElementsOf(expected.categories()).doesNotHaveDuplicates();
        }
    }

    private static void assertFields(JsonNode object, String... expected) {
        assertThat(object.isObject()).isTrue();
        var names = new ArrayList<String>();
        object.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsExactlyInAnyOrder(expected);
    }

    private ProductRow product(long id) { return products.stream().filter(product -> product.id() == id).findFirst().orElseThrow(); }

    private List<ProductRow> productSnapshot() {
        return independent.query("select id, name, description, image, price from tb_product order by name asc, id asc",
                (rows, index) -> new ProductRow(rows.getLong("id"), rows.getString("name"), rows.getString("description"),
                        rows.getString("image"), rows.getBigDecimal("price"), independent.queryForList(
                        "select distinct c.name from tb_category c join tb_product_category pc on pc.category_id=c.id "
                                + "where pc.product_id=? order by c.name", String.class, rows.getLong("id"))));
    }

    private void assertSeedPreserved() {
        assertOutsideTransaction();
        assertThat(productSnapshot()).isEqualTo(products);
        assertThat(independent.queryForList("select id, name from tb_category order by id")).isEqualTo(categorySeed);
        assertThat(independent.queryForList("select product_id, category_id from tb_product_category order by product_id, category_id"))
                .isEqualTo(categoryLinks);
        var originalAccounts = independent.queryForList("select id, name, password_hash from tb_user_account "
                + "where name='demo' order by id");
        assertThat(originalAccounts.equals(accountSeed)).as("seed account unchanged, diagnostics contain no hash").isTrue();
    }

    private DatabaseState snapshot() {
        assertOutsideTransaction();
        return independent.execute((ConnectionCallback<DatabaseState>) connection -> {
            assertThat(connection.getAutoCommit()).isTrue();
            assertThat(DataSourceUtils.isConnectionTransactional(connection, jdbc.getDataSource())).isFalse();
            return readOrders(connection);
        });
    }

    private static DatabaseState readOrders(Connection connection) throws SQLException {
        var orders = new ArrayList<OrderRow>();
        var items = new ArrayList<ItemRow>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "select id, buyer_id, created_at, status from tb_order order by id")) {
            while (rows.next()) orders.add(new OrderRow(rows.getLong("id"), rows.getLong("buyer_id"),
                    rows.getObject("created_at", OffsetDateTime.class).toInstant(), rows.getString("status")));
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "select id, order_id, product_id, position, quantity, unit_price from tb_order_item order by order_id, position")) {
            while (rows.next()) items.add(new ItemRow(rows.getLong("id"), rows.getLong("order_id"), rows.getLong("product_id"),
                    rows.getInt("position"), rows.getInt("quantity"), rows.getBigDecimal("unit_price")));
        }
        return new DatabaseState(List.copyOf(orders), List.copyOf(items));
    }

    private static void assertOutsideTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    record ProductRow(long id, String name, String description, String image, BigDecimal price, List<String> categories) { }

    enum Rejection {
        INVALID_QUANTITY(400, "Invalid order data", "1:2,2:0,3:3"),
        NO_TOKEN(401, "Authentication required or invalid", "1:2,2:3"),
        MALFORMED_TOKEN(401, "Authentication required or invalid", "1:2,2:3"),
        NO_ROLE(403, "Order registration not permitted", "1:2,2:3"),
        MISSING(404, "Product not found", "1:2," + MISSING_PRODUCT + ":1,2:3"),
        DUPLICATE(422, "Duplicate product in order", "1:2,2:3,1:1"),
        POST_FLUSH_FAILURE(500, "Order registration could not be completed", "1:2,2:3");

        final int status;
        final String message;
        final String items;
        Rejection(int status, String message, String items) { this.status=status; this.message=message; this.items=items; }
        String body() {
            return "{\"items\":[" + String.join(",", Stream.of(items.split(",")).map(item -> {
                var pair=item.split(":"); return "{\"productId\":" + pair[0] + ",\"quantity\":" + pair[1] + "}";
            }).toList()) + "]}";
        }
    }
}
