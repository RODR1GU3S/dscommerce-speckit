package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.Account;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
class CustomerRegistrationDuplicateHttpIntegrationTest extends CustomerRegistrationDuplicateScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T071 authorship only. T076 owns the first execution and discovery of both concrete children. */
@Execution(ExecutionMode.SAME_THREAD)
abstract class CustomerRegistrationDuplicateScenarios extends CustomerRegistrationHttpTestSupport {
    private static final Signup ORIGINAL = new Signup("Reserved.client", "Original Person",
            "Reserved.Email@Example.test", "+5511999999999", "2000-02-29", "Original-password-009");
    private static final Signup SECOND = new Signup("Second.client", "Second Person",
            "second@example.test", "+12345678", "1985-01-02", "Second-password-009");
    private final Set<Long> createdAccounts = new LinkedHashSet<>();

    @BeforeEach
    void prepareCommittedGuardsForAllObservedTables() {
        fixture.prepareScenario();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(ConflictKind.class)
    void newAccountsRejectNameEmailAndCombinedConflictsWithoutMerging(ConflictKind kind) throws SQLException {
        long originalId = register(ORIGINAL, "reserved.email@example.test");
        long secondId = register(SECOND, "second@example.test");
        assertConflict(exchange(conflictingAttempt(kind)));
        assertCredentialsUnchanged(originalId, ORIGINAL.name(), ORIGINAL.password());
        assertCredentialsUnchanged(secondId, SECOND.name(), SECOND.password());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("equivalentEmails")
    void equivalentEmailCaseAndEveryApprovedExternalSpaceKeepTheSameConflict(EmailSpelling spelling)
            throws SQLException {
        register(ORIGINAL, "reserved.email@example.test");
        assertConflict(exchange(rival("Available.client", spelling.email())));
    }

    static Stream<EmailSpelling> equivalentEmails() {
        var caseVariants = Stream.of(
                new EmailSpelling("exact", "Reserved.Email@Example.test"),
                new EmailSpelling("local-case", "rESERVED.eMAIL@Example.test"),
                new EmailSpelling("domain-case", "Reserved.Email@eXAMPLE.TEST"),
                new EmailSpelling("all-case", "RESERVED.EMAIL@EXAMPLE.TEST"));
        // Literal spec list; do not obtain inputs or expectations from the production trim/key helper.
        int[] externalSpaces = {0x0009, 0x000A, 0x000B, 0x000C, 0x000D, 0x0020, 0x0085, 0x00A0,
                0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006, 0x2007, 0x2008,
                0x2009, 0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000};
        return Stream.concat(caseVariants, Arrays.stream(externalSpaces).mapToObj(point -> {
            String space = new String(Character.toChars(point));
            return new EmailSpelling(String.format("external-U+%04X", point),
                    space + "RESERVED.EMAIL@EXAMPLE.TEST" + space);
        }));
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("legacyConflicts")
    void legacyIdentitiesKeepExactHashesNullDataAndEveryExistingRoleSet(LegacyConflict vector)
            throws SQLException {
        var legacy = fixture.createAccount("Legacy.reserved", vector.credential(), vector.roles());
        if (vector.kind() != ConflictKind.NAME) { addPresentLegacyEmail(legacy.id()); }
        assertLegacyData(legacy, vector.kind() != ConflictKind.NAME);
        String attemptedName = vector.kind() == ConflictKind.EMAIL ? "Available.client" : legacy.name();
        String attemptedEmail = vector.kind() == ConflictKind.NAME
                ? "free@example.test" : "\u00A0 LEGACY.RESERVED@EXAMPLE.TEST \u3000";
        assertConflict(exchange(rival(attemptedName, attemptedEmail)));
        assertLegacyData(legacy, vector.kind() != ConflictKind.NAME);
        assertCredentialsUnchanged(legacy.id(), legacy.name(), legacy.password());
    }

    static Stream<LegacyConflict> legacyConflicts() {
        var profiles = List.of(
                new LegacyProfile("CLIENT", Set.of(UserRole.CLIENT), LegacyCredential.SHORT),
                new LegacyProfile("ADMIN", Set.of(UserRole.ADMIN), LegacyCredential.LONG),
                new LegacyProfile("BOTH", Set.of(UserRole.CLIENT, UserRole.ADMIN), LegacyCredential.LONG),
                new LegacyProfile("NONE", Set.of(), LegacyCredential.SHORT));
        return profiles.stream().flatMap(profile -> Stream.of(ConflictKind.NAME, ConflictKind.EMAIL, ConflictKind.BOTH)
                .map(kind -> new LegacyConflict(profile.label() + "-" + kind, kind,
                        profile.roles(), profile.credential())));
    }

    @Test
    void sequentialIdenticalAndChangedReplaysNeverReuseConfirmationOrUpdateTheWinner() throws SQLException {
        long id = register(ORIGINAL, "reserved.email@example.test");
        // Successful delivery was observed. This does not simulate lost transport or uncertain commit.
        for (var attempt : List.of(ORIGINAL, ORIGINAL, conflictingAttempt(ConflictKind.NAME),
                conflictingAttempt(ConflictKind.EMAIL), conflictingAttempt(ConflictKind.BOTH))) {
            assertConflict(exchange(attempt));
        }
        assertCredentialsUnchanged(id, ORIGINAL.name(), ORIGINAL.password());
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(value = ConflictKind.class, names = {"NAME", "EMAIL"})
    void rejectionDoesNotReserveTheOtherAvailableIdentity(ConflictKind kind) throws SQLException {
        long originalId = register(ORIGINAL, "reserved.email@example.test");
        assertConflict(exchange(conflictingAttempt(kind)));
        Signup recovered = kind == ConflictKind.NAME
                ? rival("Recovered.client", "free@example.test")
                : rival("Available.client", "recovered@example.test");
        long recoveredId = register(recovered,
                kind == ConflictKind.NAME ? "free@example.test" : "recovered@example.test");
        assertThat(recoveredId).isNotEqualTo(originalId);
    }

    private static Signup conflictingAttempt(ConflictKind kind) {
        return switch (kind) {
            case NAME -> rival(ORIGINAL.name(), "free@example.test");
            case EMAIL -> rival("Available.client", "reserved.email@example.test");
            case BOTH -> rival(ORIGINAL.name(), "reserved.email@example.test");
            case SPLIT_IDENTITIES -> rival(ORIGINAL.name(), SECOND.email());
        };
    }

    private static Signup rival(String name, String email) {
        return new Signup(name, "Different Person", email, "+442012345678", "1991-12-31", "Different-password-009");
    }

    private Exchange exchange(Signup input) {
        var payload = mapper.createObjectNode().put("name", input.name()).put("fullName", input.fullName())
                .put("email", input.email()).put("phone", input.phone()).put("birthDate", input.birthDate())
                .put("password", input.password());
        var before = database.snapshot();
        var response = send(jsonRequest("/customers")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(payload))).build());
        var after = database.snapshot();
        var previous = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .collect(java.util.stream.Collectors.toSet());
        after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row, "ID"))
                .filter(value -> !previous.contains(value)).forEach(createdAccounts::add);
        return new Exchange(response, before, after);
    }

    private long register(Signup input, String expectedEmailKey) throws SQLException {
        var exchange = exchange(input);
        assertThat(exchange.response().statusCode()).isEqualTo(201);
        assertJsonAndNoAuthenticationOrLocation(exchange.response());
        var body = jsonBody(exchange.response());
        assertThat(body.isObject() && body.size() == 2 && body.path("id").isIntegralNumber()
                && body.path("id").canConvertToLong() && body.path("id").longValue() > 0
                && input.name().equals(body.path("name").textValue()))
                .as("exact new id/name confirmation without personal data or credentials").isTrue();
        long id = body.path("id").longValue();
        assertOnlyNewAccountAndClient(exchange.before(), exchange.after(), id);
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, full_name, email, email_key, phone, birth_date, password_hash from tb_user_account where id = ?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).as("new account is externally visible after completed request").isTrue();
                assertThat(input.name().equals(rows.getString(1)) && input.fullName().equals(rows.getString(2))
                        && input.email().equals(rows.getString(3))
                        && Arrays.equals(expectedEmailKey.getBytes(StandardCharsets.UTF_8), rows.getBytes(4))
                        && input.phone().equals(rows.getString(5))
                        && LocalDate.parse(input.birthDate()).equals(rows.getObject(6, LocalDate.class)))
                        .as("all accepted data and independent literal email key belong to the winner").isTrue();
                String hash = rows.getString(7);
                assertThat(hash != null && hash.length() == 97 && hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$")
                        && !input.password().equals(hash)).as("approved stored hash without plaintext").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                roles.setLong(1, id);
                try (var rows = roles.executeQuery()) {
                    assertThat(rows.next() && "CLIENT".equals(rows.getString(1)) && !rows.next())
                            .as("new account has exactly CLIENT").isTrue();
                }
            }
        }
        return id;
    }

    private void assertConflict(Exchange exchange) {
        assertThat(exchange.response().statusCode()).isEqualTo(409);
        assertJsonAndNoAuthenticationOrLocation(exchange.response());
        var expected = mapper.createObjectNode().put("status", 409).put("error", "Conflict")
                .put("message", "Customer registration not available").put("path", expectedContextPath() + "/customers");
        assertThat(expected.equals(jsonBody(exchange.response())))
                .as("same generic envelope: no identity, conflicting field, old confirmation or secret").isTrue();
        assertUnchanged(exchange.before(), exchange.after());
    }

    private void assertCredentialsUnchanged(long id, String name, String originalPassword) {
        var before = database.snapshot();
        realHttpLogin(id, name, originalPassword);
        assertUnchanged(before, database.snapshot());
        var login = mapper.createObjectNode().put("name", name).put("password", "Different-password-009");
        var response = send(jsonRequest("/login").POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(login))).build());
        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonAndNoAuthenticationOrLocation(response);
        var expected = mapper.createObjectNode().put("status", 401).put("error", "Unauthorized")
                .put("message", "Invalid credentials").put("path", expectedContextPath() + "/login");
        assertThat(expected.equals(jsonBody(response))).as("rejected candidate cannot replace existing credentials").isTrue();
        assertUnchanged(before, database.snapshot());
    }

    private static void assertJsonAndNoAuthenticationOrLocation(HttpResponse<byte[]> response) {
        boolean json;
        try {
            json = MediaType.parseMediaType(response.headers().firstValue("Content-Type").orElse(""))
                    .isCompatibleWith(MediaType.APPLICATION_JSON);
        } catch (IllegalArgumentException invalidHeader) { json = false; }
        assertThat(json).as("JSON response").isTrue();
        for (String header : List.of("Set-Cookie", "Authorization", "Location")) {
            assertThat(response.headers().allValues(header).isEmpty()).as("no %s header", header).isTrue();
        }
    }

    private void addPresentLegacyEmail(long id) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "update tb_user_account set email = ?, email_key = ? where id = ?")) {
            assertThat(connection.getAutoCommit()).as("legacy fixture setup commits before HTTP").isTrue();
            statement.setString(1, "Legacy.Reserved@Example.test");
            statement.setBytes(2, "legacy.reserved@example.test".getBytes(StandardCharsets.UTF_8));
            statement.setLong(3, id);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void assertLegacyData(Account legacy, boolean emailPresent) throws SQLException {
        try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                "select name, password_hash, full_name, email, email_key, phone, birth_date from tb_user_account where id = ?")) {
            statement.setLong(1, legacy.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                String hash = rows.getString(2);
                assertThat(legacy.name().equals(rows.getString(1)) && legacy.passwordHash().equals(hash)
                        && Arrays.equals(legacy.passwordHash().getBytes(StandardCharsets.UTF_8),
                        hash == null ? null : hash.getBytes(StandardCharsets.UTF_8))
                        && rows.getString(3) == null && rows.getString(6) == null
                        && rows.getObject(7, LocalDate.class) == null)
                        .as("legacy identity, exact fixed hash and absent personal data remain unchanged").isTrue();
                assertThat(emailPresent
                        ? "Legacy.Reserved@Example.test".equals(rows.getString(4))
                        && Arrays.equals("legacy.reserved@example.test".getBytes(StandardCharsets.UTF_8), rows.getBytes(5))
                        : rows.getString(4) == null && rows.getBytes(5) == null)
                        .as("legacy email presence is explicit, never filled by rejected signup").isTrue();
                assertThat(rows.next()).isFalse();
            }
            try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                roles.setLong(1, legacy.id());
                try (var rows = roles.executeQuery()) {
                    var actual = new LinkedHashSet<String>();
                    while (rows.next()) { assertThat(actual.add(rows.getString(1))).isTrue(); }
                    var expected = legacy.roles().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());
                    assertThat(actual.equals(expected)).as("legacy profiles are neither merged nor repaired").isTrue();
                }
            }
        }
    }

    private static void assertOnlyNewAccountAndClient(Snapshot before, Snapshot after, long id) {
        assertThat(before.tables().keySet().equals(after.tables().keySet())).as("same observed tables").isTrue();
        before.tables().forEach((table, previous) -> {
            var current = after.tables().get(table);
            if (!Set.of("TB_USER_ACCOUNT", "TB_USER_ACCOUNT_ROLE").contains(table)) {
                assertThat(previous.equals(current)).as("unchanged table %s", table).isTrue();
            } else {
                String key = "TB_USER_ACCOUNT".equals(table) ? "ID" : "USER_ACCOUNT_ID";
                assertThat(previous.columns().equals(current.columns())).as("unchanged schema %s", table).isTrue();
                assertThat(current.rows().size()).as("one new row in %s", table).isEqualTo(previous.rows().size() + 1);
                assertThat(current.rows().stream().filter(row -> id(row, key) != id).toList().equals(previous.rows()))
                        .as("all previous fields, hashes, identities and profiles preserved in %s", table).isTrue();
            }
        });
    }

    private static long id(Row row, String column) {
        Object value = row.values().get(column).value();
        assertThat(value instanceof Number).as("numeric snapshot identity").isTrue();
        return ((Number) value).longValue();
    }

    /** Runs before inherited fixture cleanup; no production compensation or table-wide deletion. */
    @AfterEach
    void cleanOnlyAccountsObservedAsNewDuringCompletedRequests() throws SQLException {
        assertNoCallerTransaction();
        if (createdAccounts.isEmpty()) { return; }
        try (var connection = openIndependentConnection()) {
            connection.setAutoCommit(false);
            try {
                for (long id : createdAccounts) {
                    delete(connection, "delete from tb_user_account_role where user_account_id = ?", id);
                    delete(connection, "delete from tb_user_account where id = ?", id);
                }
                connection.commit();
                createdAccounts.clear();
            } catch (SQLException | RuntimeException | Error failure) {
                connection.rollback();
                throw new AssertionError("Cleanup of duplicate-scenario owned rows failed");
            }
        } catch (SQLException failure) {
            throw new SQLException("Cleanup of duplicate-scenario owned rows could not complete");
        }
    }

    private static void delete(Connection connection, String sql, long id) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    enum ConflictKind { NAME, EMAIL, BOTH, SPLIT_IDENTITIES }
    record EmailSpelling(String label, String email) {
        @Override public String toString() { return label; }
    }
    record LegacyProfile(String label, Set<UserRole> roles, LegacyCredential credential) {
        @Override public String toString() { return label; }
    }
    record LegacyConflict(String label, ConflictKind kind, Set<UserRole> roles, LegacyCredential credential) {
        @Override public String toString() { return label; }
    }
    private record Signup(String name, String fullName, String email, String phone, String birthDate, String password) {
        @Override public String toString() { return "DuplicateSignup[redacted]"; }
    }
    private record Exchange(HttpResponse<byte[]> response, Snapshot before, Snapshot after) {
        @Override public String toString() { return "DuplicateExchange[redacted]"; }
    }
}
