package com.devsuperior.dscommerce.controllers;

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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** T019: real authentication/authorization before MVC on each newly mapped GET. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
class AdminProductGetHttpSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;

    @BeforeEach
    void prepareIsolatedRealSecurity() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    @AfterEach
    void cleanOnlyOwnedRows() {
        assertNoCallerTransaction();
        if (fixture != null) {
            fixture.close();
            assertEmptyDatabase();
        }
    }

    @ParameterizedTest(name = "T019 GET {0}, client={1}")
    @MethodSource("rejectedGets")
    void shouldRejectAnonymousOrClientBeforeTheNewGetHandler(String route, boolean client) throws Exception {
        var request = get("/api" + route).contextPath("/api").servletPath(route).accept(MediaType.APPLICATION_JSON);
        if (client) {
            var account = fixture.createAccount("T019 Client", "Disposable-T019", Set.of(UserRole.CLIENT));
            assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(account));
        }
        var before = database.snapshot();
        var accountIds = database.accountIds();

        var result = mvc.perform(request).andReturn();

        int status = client ? 403 : 401;
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", client ? "Forbidden" : "Unauthorized")
                .put("message", client ? "Product maintenance not permitted" : "Authentication required or invalid")
                .put("path", "/api" + route);
        expected.putArray("errors");
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(expected);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(client ? null : "Bearer");
        assertThat(result.getHandler()).isNull();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(database.accountIds()).isEqualTo(accountIds);
        assertNoCallerTransaction();
    }

    static Stream<Arguments> rejectedGets() {
        return Stream.of("/admin/products", "/admin/products/999", "/admin/categories")
                .flatMap(route -> Stream.of(false, true).map(client -> Arguments.of(route, client)));
    }

    @ParameterizedTest(name = "T021 invalid GET {1}, context={0}, client={2}")
    @MethodSource("invalidGets")
    void shouldKeepSecurityBeforeInvalidGetParameters(String externalContext, String route, boolean client) throws Exception {
        var request = get(externalContext + route).contextPath(externalContext).servletPath(route)
                .queryParam("page", "-1").queryParam("size", "0")
                .queryParam("name", "fone", "fone").queryParam("T021_PRIVATE_KEY", "T021_PRIVATE_VALUE")
                .accept(MediaType.APPLICATION_JSON);
        if (client) {
            var account = fixture.createAccount("T021 Client", "Disposable-T021", Set.of(UserRole.CLIENT));
            assertThat(database.roles(account.id())).containsExactly(UserRole.CLIENT);
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + fixture.login(account));
        }
        var before = database.snapshot();
        var accountIds = database.accountIds();

        var result = mvc.perform(request).andReturn();

        int status = client ? 403 : 401;
        var expected = mapper.createObjectNode().put("status", status)
                .put("error", client ? "Forbidden" : "Unauthorized")
                .put("message", client ? "Product maintenance not permitted" : "Authentication required or invalid")
                .put("path", externalContext + route);
        expected.putArray("errors");
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(expected);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(client ? null : "Bearer");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("T021_PRIVATE_KEY", "T021_PRIVATE_VALUE");
        assertThat(result.getHandler()).isNull();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(database.accountIds()).isEqualTo(accountIds);
        assertNoCallerTransaction();
    }

    static Stream<Arguments> invalidGets() {
        return Stream.of("", "/api").flatMap(context -> Stream.of("/admin/products", "/admin/products/abc", "/admin/categories")
                .flatMap(route -> Stream.of(false, true).map(client -> Arguments.of(context, route, client))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/api"})
    void shouldTreatLongMaximumAsAValidAbsentId(String externalContext) throws Exception {
        var account = fixture.createAccount("T021 Admin", "Disposable-T021", Set.of(UserRole.ADMIN));
        var token = fixture.login(account);
        var route = "/admin/products/" + Long.MAX_VALUE;
        var before = database.snapshot();

        var result = mvc.perform(get(externalContext + route).contextPath(externalContext).servletPath(route)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).accept(MediaType.APPLICATION_JSON)).andReturn();

        var expected = mapper.createObjectNode().put("status", 404).put("error", "Not Found")
                .put("message", "Product not found").put("path", externalContext + route);
        expected.putArray("errors");
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        assertThat(mapper.readTree(result.getResponse().getContentAsByteArray())).isEqualTo(expected);
        assertThat(database.snapshot()).isEqualTo(before);
        assertNoCallerTransaction();
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
}
