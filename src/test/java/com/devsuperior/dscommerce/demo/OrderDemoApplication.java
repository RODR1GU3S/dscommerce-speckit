package com.devsuperior.dscommerce.demo;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.OrderTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Explicit, finite HTTP demonstration. Never discovered as application configuration or a JUnit test. */
public final class OrderDemoApplication {

    public static void main(String[] args) {
        var application = new SpringApplication(DscommerceApplication.class, DemoFixtures.class);
        application.setLogStartupInfo(false);
        // Command-line properties take precedence over shared resources; no resource is edited.
        try (var context = application.run("--spring.profiles.active=test",
                "--spring.datasource.url=" + OrderTestConfig.isolatedDatabaseUrl(),
                "--server.address=127.0.0.1", "--server.port=0", "--spring.main.banner-mode=off",
                "--logging.level.root=OFF", "--spring.jpa.show-sql=false", "--spring.jpa.open-in-view=false");
             var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                     .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var jdbc = context.getBean(JdbcTemplate.class);
            var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
            require(url != null && url.startsWith("jdbc:h2:mem:order-test-"), "isolated database");
            require(Set.of(context.getEnvironment().getActiveProfiles()).equals(Set.of("test")), "test profile");
            require(context.getBeansOfType(OrderTestFixture.class).size() == 1, "explicit fixture bean");
            var independent = new JdbcTemplate(new DriverManagerDataSource(url, "sa", ""));
            var seedProducts = independent.queryForList("select * from tb_product order by id");
            var seedCategories = independent.queryForList("select * from tb_category order by id");
            var seedLinks = independent.queryForList("select * from tb_product_category order by product_id, category_id");
            var seedAccounts = independent.queryForList("select * from tb_user_account order by id");
            require(seedProducts.size() == 52 && count(independent, "tb_order") == 0, "initial seed");

            var fixture = context.getBean(OrderTestFixture.class);
            // Ephemeral credentials exist only in memory, never in process arguments or evidence.
            var name = "order-demo-" + UUID.randomUUID();
            var password = UUID.randomUUID().toString();
            long buyer = fixture.createAccount(name, password, Set.of(UserRole.CLIENT));
            long firstProduct = fixture.createProduct("T051 product A", new BigDecimal("10.00"));
            long secondProduct = fixture.createProduct("T051 product B", new BigDecimal("7.50"));
            require(independent.queryForList("select role from tb_user_account_role where user_account_id = ?",
                    String.class, buyer).equals(List.of("CLIENT")), "committed fixture role");
            var mapper = context.getBean(ObjectMapper.class);
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            var base = "http://127.0.0.1:" + port;
            var catalogBefore = request(client, mapper, base, "/products?page=0&size=50", null, null, 200);
            require(catalogBefore.path("totalElements").asLong() == 54, "public catalog with owned fixtures");
            var detailBefore = request(client, mapper, base, "/products/1", null, null, 200);
            var firstDetail = request(client, mapper, base, "/products/" + firstProduct, null, null, 200);
            var secondDetail = request(client, mapper, base, "/products/" + secondProduct, null, null, 200);
            require(firstDetail.path("price").decimalValue().compareTo(new BigDecimal("10.00")) == 0
                    && secondDetail.path("price").decimalValue().compareTo(new BigDecimal("7.50")) == 0,
                    "fixture prices from public HTTP");

            var loginBody = mapper.createObjectNode().put("name", name).put("password", password).toString();
            var token = login(client, mapper, context.getBean(JwtDecoder.class), base, loginBody, buyer);
            var orderBody = "{\"items\":[{\"productId\":" + firstProduct + ",\"quantity\":2},"
                    + "{\"productId\":" + secondProduct + ",\"quantity\":3}]}";
            request(client, mapper, base, "/orders", orderBody, null, 401);
            var firstOrder = submit(client, mapper, independent, base, orderBody, token, buyer, firstProduct, secondProduct);
            var firstRows = rows(independent, firstOrder.path("id").asLong());
            var secondOrder = submit(client, mapper, independent, base, orderBody, token, buyer, firstProduct, secondProduct);
            require(firstOrder.path("id").asLong() != secondOrder.path("id").asLong(), "new id on repeated submission");
            require(firstRows.equals(rows(independent, firstOrder.path("id").asLong())), "first order preserved");
            require(count(independent, "tb_order") == 2 && count(independent, "tb_order_item") == 4, "two complete commits");
            require(catalogBefore.equals(request(client, mapper, base, "/products?page=0&size=50", null, null, 200)),
                    "public catalog after orders");
            require(detailBefore.equals(request(client, mapper, base, "/products/1", null, null, 200)),
                    "public detail after orders");
            login(client, mapper, context.getBean(JwtDecoder.class), base, loginBody, buyer);
            require(seedProducts.equals(independent.queryForList("select * from tb_product where id not in (?, ?) order by id",
                    firstProduct, secondProduct)) && seedCategories.equals(independent.queryForList("select * from tb_category order by id"))
                    && seedLinks.equals(independent.queryForList("select * from tb_product_category order by product_id, category_id"))
                    && seedAccounts.equals(independent.queryForList("select * from tb_user_account where id <> ? order by id", buyer)),
                    "public seed unchanged");
            // Only allowlisted, non-secret values enter the evidence.
            System.out.println("ORDER_DEMO_RESULT " + mapper.writeValueAsString(Map.of(
                    "fixtureActivated", true, "profile", "test", "isolatedH2", true, "realHttpPort", port,
                    "seedProductsPreserved", 52, "publicChecksBeforeAndAfter", true, "loginBeforeAndAfter", true,
                    "orders", List.of(firstOrder, secondOrder), "persistedOrders", 2, "persistedItems", 4)));
        } catch (Exception failure) {
            // Do not print exception messages, request/response bodies, tokens or Spring bean diagnostics.
            System.err.println("ORDER_DEMO_FAILED category=" + failure.getClass().getSimpleName());
            System.exit(1);
        }
        System.out.println("ORDER_DEMO_CLOSED ownedContext=true");
    }

    private static String login(HttpClient client, ObjectMapper mapper, JwtDecoder decoder, String base,
                                String body, long buyer) throws Exception {
        var response = request(client, mapper, base, "/login", body, null, 200);
        fields(response, "accessToken", "tokenType");
        require(response.path("accessToken").isTextual() && !response.path("accessToken").asText().isBlank()
                && response.path("tokenType").asText().equals("Bearer"), "login contract");
        var token = response.path("accessToken").asText();
        var decoded = decoder.decode(token);
        require(decoded.getSubject().equals(Long.toString(buyer)) && decoded.getHeaders().get("alg").equals("HS256"),
                "real signed token and buyer");
        return token;
    }

    private static JsonNode submit(HttpClient client, ObjectMapper mapper, JdbcTemplate independent, String base,
                                   String body, String token, long buyer, long firstProduct, long secondProduct) throws Exception {
        var started = Instant.now();
        var order = request(client, mapper, base, "/orders", body, token, 201);
        var ended = Instant.now();
        fields(order, "id", "accountId", "createdAt", "status", "items", "total");
        require(order.path("id").isIntegralNumber() && order.path("id").asLong() > 0
                && order.path("accountId").isIntegralNumber() && order.path("accountId").asLong() == buyer,
                "response identity");
        require(order.path("status").asText().equals("WAITING_PAYMENT"), "initial status");
        require(order.path("createdAt").asText().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z"), "UTC milliseconds");
        var created = Instant.parse(order.path("createdAt").asText());
        require(!created.isBefore(started.minusMillis(1)) && !created.isAfter(ended), "creation within request");
        require(order.path("items").isArray() && order.path("items").size() == 2, "complete item list");
        for (int index = 0; index < 2; index++) {
            var item = order.path("items").get(index);
            fields(item, "productId", "quantity", "unitPrice", "subtotal");
            require(item.path("productId").isIntegralNumber() && item.path("productId").asLong() == (index == 0 ? firstProduct : secondProduct)
                    && item.path("quantity").isIntegralNumber() && item.path("quantity").asInt() == (index == 0 ? 2 : 3), "item order and quantities");
            require(item.path("unitPrice").isTextual() && item.path("unitPrice").asText().equals(index == 0 ? "10.00" : "7.50")
                    && item.path("subtotal").isTextual() && item.path("subtotal").asText().equals(index == 0 ? "20.00" : "22.50"), "exact line money");
        }
        require(order.path("total").isTextual() && order.path("total").asText().equals("42.50"), "exact total");
        long id = order.path("id").asLong();
        require(independent.queryForObject("select buyer_id from tb_order where id = ?", Long.class, id) == buyer
                && independent.queryForObject("select status from tb_order where id = ?", String.class, id).equals("WAITING_PAYMENT")
                && independent.queryForObject("select created_at from tb_order where id = ?", OffsetDateTime.class, id).toInstant().equals(created),
                "committed order matches response");
        var items = independent.queryForList("select product_id, quantity, position, unit_price from tb_order_item where order_id = ? order by position", id);
        require(items.size() == 2, "committed items");
        for (int i = 0; i < 2; i++) {
            var row = items.get(i);
            require(((Number) row.get("PRODUCT_ID")).longValue() == (i == 0 ? firstProduct : secondProduct)
                    && ((Number) row.get("QUANTITY")).intValue() == (i == 0 ? 2 : 3)
                    && ((Number) row.get("POSITION")).intValue() == i
                    && ((BigDecimal) row.get("UNIT_PRICE")).compareTo(new BigDecimal(i == 0 ? "10.00" : "7.50")) == 0, "committed ordered snapshot");
        }
        return order;
    }

    private static JsonNode request(HttpClient client, ObjectMapper mapper, String base, String path,
                                    String body, String token, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body == null) builder.GET();
        else builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() == status, "HTTP status");
        require(response.headers().firstValue("X-Frame-Options").orElse("").equals("SAMEORIGIN"), "real security headers");
        require(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), "JSON transport");
        require(response.headers().firstValue("Location").isEmpty(), "no promised order query");
        return mapper.readTree(response.body());
    }

    private static List<Map<String, Object>> rows(JdbcTemplate jdbc, long id) {
        return jdbc.queryForList("select o.buyer_id, o.created_at, o.status, i.* from tb_order o join tb_order_item i on i.order_id=o.id where o.id=? order by i.position", id);
    }

    private static long count(JdbcTemplate jdbc, String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }

    private static void fields(JsonNode node, String... names) {
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        require(node.isObject() && actual.equals(Set.of(names)), "exact response fields");
    }

    private static void require(boolean condition, String check) {
        if (!condition) throw new IllegalStateException(check);
    }

    // Lite configuration registered only as an explicit SpringApplication source, never by component scan.
    @Profile("test")
    public static class DemoFixtures {
        @Bean
        OrderTestFixture orderDemoFixture(JdbcTemplate jdbc, PlatformTransactionManager transactions,
                                          PasswordEncoder passwords, JwtEncoder encoder) {
            return new OrderTestFixture(jdbc, transactions, passwords, encoder);
        }
    }
}
