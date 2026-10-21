package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe.SqlObservation;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.OwnOrderQueryTestFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared real-HTTP authentication gates only. No query implementation or future story scenarios. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class OwnOrderQueryHttpTestSupport {

    @Autowired protected WebApplicationContext context;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected OwnOrderQueryDatabaseProbe database;
    @Autowired protected SqlObservation sql;
    @Autowired private JwtDecoder decoder;
    @Autowired private JwtEncoder encoder;
    @LocalServerPort protected int port;

    private final Map<Credential, String> tokens = new EnumMap<>(Credential.class);
    private OwnOrderQueryTestFixture fixture;
    private HttpClient http;
    private int logins;
    private int queryRequests;

    protected abstract String expectedContextPath();
    protected abstract String expectedContextIdentity();

    @BeforeAll
    void prepareCommittedAccountsAndRealHttpCredentials() {
        noCallerTransaction();
        var environment = context.getEnvironment();
        require(expectedContextIdentity().equals(environment.getRequiredProperty("feature007.own-order-query-test-context")),
                "Unexpected context identity");
        require(expectedContextPath().equals(context.getServletContext().getContextPath()), "Unexpected servlet context path");
        require("false".equals(environment.getRequiredProperty("spring.jpa.open-in-view")), "OSIV must be disabled");
        require("create-drop".equals(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")), "Keep create-drop");
        var url = environment.getRequiredProperty("spring.datasource.url");
        var metadataUrl = context.getBean(JdbcTemplate.class).execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getURL());
        require(url.startsWith("jdbc:h2:mem:admin-product-test-") && url.split(";", 2)[0].equals(metadataUrl),
                "Use the stable isolated H2 database");
        require(url.equals(environment.getRequiredProperty("spring.datasource.url")), "Database URL must remain stable");
        require(database.snapshot().accountIds().isEmpty(), "Start in the seed-free isolated context");

        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        fixture = new OwnOrderQueryTestFixture(context);
        var accounts = fixture.prepareAccessAccounts();
        var removed = fixture.prepareBuyer();
        for (var entry : Map.of(Credential.CLIENT, accounts.client(), Credential.ADMIN, accounts.admin(),
                Credential.BOTH, accounts.both(), Credential.NO_PROFILE, accounts.none(),
                Credential.REMOVED_ACCOUNT, removed).entrySet()) {
            tokens.put(entry.getKey(), realHttpLogin(entry.getValue()));
        }
        var prepared = database.snapshot();
        for (var account : List.of(accounts.client(), accounts.admin(), accounts.both(), accounts.none(), removed)) {
            var actualRoles = prepared.roles().stream().filter(role -> role.accountId() == account.id())
                    .map(OwnOrderQueryDatabaseProbe.AccountRole::role).collect(java.util.stream.Collectors.toSet());
            require(actualRoles.equals(account.roles()), "Persisted fixture roles must match the requested roles");
        }
        var now = context.getBean(Clock.class).instant();
        var expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(Long.toString(accounts.client().id()))
                        .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build())).getTokenValue();
        tokens.put(Credential.EXPIRED, expired);
        tokens.put(Credential.MALFORMED, "invalid-t005-bearer");
        var pieces = tokens.get(Credential.CLIENT).split("\\.");
        require(pieces.length == 3 && !pieces[2].isEmpty(), "A real signed JWT is required");
        pieces[2] = (pieces[2].charAt(0) == 'A' ? "B" : "A") + pieces[2].substring(1);
        tokens.put(Credential.BAD_SIGNATURE, String.join(".", pieces));
        requireRejectedByRealDecoder(Credential.MALFORMED);
        requireRejectedByRealDecoder(Credential.BAD_SIGNATURE);
        requireRejectedByRealDecoder(Credential.EXPIRED);

        // Explicit committed setup removal after a successful real login; the signed token remains authentic.
        new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var jdbc = context.getBean(JdbcTemplate.class);
            jdbc.update("delete from tb_user_account_role where user_account_id = ?", removed.id());
            require(jdbc.update("delete from tb_user_account where id = ?", removed.id()) == 1, "Remove only the owned account");
        });
        require(!database.snapshot().accountIds().contains(removed.id()), "Removed account must be committed");
        require(Long.toString(removed.id()).equals(decoder.decode(tokens.get(Credential.REMOVED_ACCOUNT)).getSubject()),
                "Removed-account token must still have a valid signature and lifetime");
        System.out.println("T005_CONTEXT context=" + expectedContextIdentity() + " path="
                + (expectedContextPath().isEmpty() ? "ROOT" : expectedContextPath()) + " port=" + port
                + " database=" + metadataUrl + " httpLogins=" + logins + " mockMvcDispatches=0");
    }

    @AfterAll
    void cleanOnlyOwnedSetupOutsideObservation() {
        try {
            if (fixture != null) { fixture.close(); }
        } finally {
            if (http != null) { http.close(); }
            tokens.clear();
            System.out.println("T005_COUNTS context=" + expectedContextIdentity() + " httpLogins=" + logins
                    + " queryHttpRequests=" + queryRequests + " mockMvcDispatches=0");
        }
    }

    @ParameterizedTest(name = "[{index}] {0}: {1}")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryHttpTestSupport#unauthenticatedCases")
    void authenticationGateRejectsMissingInvalidExpiredAndRemovedAccounts(Credential credential, Target target) {
        var response = query(credential, target, 401);
        assertRejected(response, target, 401, "Unauthorized", "Authentication required or invalid");
    }

    @ParameterizedTest(name = "[{index}] NO_PROFILE: {0}")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryHttpTestSupport#allTargets")
    void authenticationGateRejectsAccountWithoutProfilesBeforeBinding(Target target) {
        var response = query(Credential.NO_PROFILE, target, 403);
        assertRejected(response, target, 403, "Forbidden", "Order query not permitted");
    }

    @ParameterizedTest(name = "[{index}] {0}: {1}")
    @MethodSource("com.devsuperior.dscommerce.controllers.OwnOrderQueryHttpTestSupport#authorizedCases")
    void authenticationGateAllowsCurrentAuthorizedProfilesToReachRouting(Credential credential, Target target) {
        var response = query(credential, target, 0);
        // T005 proves the gate only. Business 200 is mandatory in the later list/detail stories.
        assertThat(response.statusCode()).as(label(credential, target) + " authorized routing status")
                .isIn(200, 404, 405);
        if (target.method().equals("HEAD")) { assertThat(response.body().length).as("HEAD response body").isZero(); }
    }

    static Stream<Arguments> unauthenticatedCases() {
        return Stream.of(Credential.ABSENT, Credential.MALFORMED, Credential.BAD_SIGNATURE,
                Credential.EXPIRED, Credential.REMOVED_ACCOUNT)
                .flatMap(credential -> allTargets().map(target -> Arguments.of(credential, target)));
    }

    static Stream<Arguments> authorizedCases() {
        return Stream.of(Credential.CLIENT, Credential.ADMIN, Credential.BOTH)
                .flatMap(credential -> allTargets().filter(Target::validInput)
                        .map(target -> Arguments.of(credential, target)));
    }

    static Stream<Target> allTargets() {
        return Stream.of("GET", "HEAD").flatMap(method -> Stream.of(
                new Target(method, "/orders?page=0&size=2", true),
                new Target(method, "/orders?page=not-an-integer&size=0", false),
                new Target(method, "/orders/1", true),
                new Target(method, "/orders/not-an-integer", false)));
    }

    private HttpResponse<byte[]> query(Credential credential, Target target, int expected) {
        noCallerTransaction();
        var request = HttpRequest.newBuilder(uri(target.path())).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").method(target.method(), HttpRequest.BodyPublishers.noBody());
        if (credential != Credential.ABSENT) { request.header("Authorization", "Bearer " + tokens.get(credential)); }
        var observed = database.observeRead(sql, () -> send(request.build()));
        var response = observed.result();
        queryRequests++;
        System.out.println("T005_HTTP context=" + expectedContextIdentity() + " credential=" + credential
                + " method=" + target.method() + " path=" + target.path() + " expected="
                + (expected == 0 ? "200/404/405" : Integer.toString(expected)) + " actual=" + response.statusCode()
                + " bodyBytes=" + response.body().length);
        observed.assertNoWrites();
        if (expected != 0) {
            assertThat(response.statusCode()).as(label(credential, target) + " authentication gate status").isEqualTo(expected);
        }
        return response;
    }

    private void assertRejected(HttpResponse<byte[]> response, Target target, int status, String error, String message) {
        if (status == 401) {
            require(response.headers().allValues("WWW-Authenticate").equals(List.of("Bearer")), "Expected Bearer challenge");
        }
        if (target.method().equals("HEAD")) {
            assertThat(response.body().length).as("HEAD rejection body").isZero();
            return;
        }
        var expected = mapper.createObjectNode().put("status", status).put("error", error).put("message", message)
                .put("path", expectedContextPath() + target.path().split("\\?", 2)[0]);
        try {
            require(expected.equals(mapper.readTree(response.body())), "Expected the exact safe four-field rejection envelope");
        } catch (IOException failure) {
            throw new AssertionError("Rejection body must be safe JSON");
        }
    }

    private String realHttpLogin(Account account) {
        noCallerTransaction();
        try {
            var request = HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(
                            Map.of("name", account.name(), "password", account.password())))).build();
            var response = send(request);
            logins++;
            require(response.statusCode() == 200, "Fixture real HTTP login must return 200");
            var token = mapper.readValue(response.body(), TokenResponseDTO.class);
            require("Bearer".equals(token.tokenType()) && token.accessToken() != null, "Expected a real bearer credential");
            require(Long.toString(account.id()).equals(decoder.decode(token.accessToken()).getSubject()),
                    "Real login must identify the prepared account");
            return token.accessToken();
        } catch (IOException | JwtException failure) {
            // Do not attach a body, credential, raw JWT or sensitive parsing/decoder cause.
            throw new IllegalStateException("Fixture real HTTP login could not be verified");
        }
    }

    private void requireRejectedByRealDecoder(Credential credential) {
        try {
            decoder.decode(tokens.get(credential));
        } catch (JwtException expected) {
            return;
        }
        throw new IllegalStateException("Invalid fixture credential must be rejected by the real decoder");
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Real order-query HTTP request interrupted");
        } catch (IOException failure) {
            throw new IllegalStateException("Real order-query HTTP request could not be completed");
        }
    }

    private URI uri(String relative) {
        return URI.create("http://localhost:" + port + expectedContextPath() + relative);
    }

    private String label(Credential credential, Target target) {
        return expectedContextIdentity() + " " + credential + " " + target;
    }

    private static void noCallerTransaction() {
        require(!TransactionSynchronizationManager.isActualTransactionActive(), "HTTP must have no caller transaction");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    enum Credential { ABSENT, MALFORMED, BAD_SIGNATURE, EXPIRED, REMOVED_ACCOUNT, NO_PROFILE, CLIENT, ADMIN, BOTH }

    record Target(String method, String path, boolean validInput) {
        @Override public String toString() { return method + " " + path; }
    }
}
