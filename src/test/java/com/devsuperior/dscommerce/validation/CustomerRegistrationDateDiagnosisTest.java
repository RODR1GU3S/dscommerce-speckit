package com.devsuperior.dscommerce.validation;

import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.ApiHttpContext;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import com.devsuperior.dscommerce.controllers.CustomerRegistrationHttpTestSupport;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationTestFixture.LegacyCredential;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Row;
import com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.Snapshot;
import com.devsuperior.dscommerce.services.CustomerRegistrationCommand;
import com.devsuperior.dscommerce.services.CustomerRegistrationService;
import com.devsuperior.dscommerce.services.CustomerRegistrationTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.type.descriptor.java.LocalDateJavaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.util.AopTestUtils;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static com.devsuperior.dscommerce.fixtures.CustomerRegistrationDatabaseProbe.assertUnchanged;
import static org.mockito.Mockito.mockingDetails;

/** Focal T050 diagnosis. Exact SQL DATE remains the oracle; legacy round trips are observations only. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CustomerRegistrationDateDiagnosisTest {

    @TempDir
    static Path temporaryEvidenceDirectory;

    @Test
    void observesTheActualHibernateLegacyConversionWithoutReplacingTheDateOracle() throws IOException {
        var rows = new ArrayList<Map<String, Object>>();
        for (String text : List.of("0001-01-01", "1582-10-04", "1582-10-15", "2000-02-29")) {
            var parsed = RegistrationInputRules.parseBirthDate(text);
            Date legacy = LocalDateJavaType.INSTANCE.unwrap(parsed, Date.class, null);
            var row = new LinkedHashMap<String, Object>();
            row.put("input", text);
            row.put("javaLocalDate", parsed.toString());
            row.put("hibernateUnwrappedClass", legacy.getClass().getName());
            row.put("legacyText", legacy.toString());
            row.put("legacyToLocalDate", legacy.toLocalDate().toString());
            row.put("legacyEpochMillis", legacy.getTime());
            row.put("prolepticDateAtSameEpochAndZone",
                    Instant.ofEpochMilli(legacy.getTime()).atZone(ZoneId.systemDefault()).toLocalDate().toString());
            row.put("zone", ZoneId.systemDefault().getId());
            rows.add(row);
            assertThat(parsed.toString().equals(text) && legacy.toLocalDate().equals(parsed)).isTrue();
        }
        save("hibernate-legacy-conversion.json", rows);
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @RootHttpContext
    class RootHttpTest extends DateCases {
        @Override protected String expectedContextPath() { return ""; }
    }

    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @ApiHttpContext
    class ApiHttpTest extends DateCases {
        @Override protected String expectedContextPath() { return "/api"; }
    }

    abstract static class DateCases extends CustomerRegistrationHttpTestSupport {
        @SpyBean private CustomerRegistrationService service;
        @SpyBean(proxyTargetAware = true) private CustomerRegistrationTransaction worker;
        private final Set<Long> createdAccounts = new LinkedHashSet<>();

        @ParameterizedTest(name = "{index}: {0}")
        @MethodSource("dates")
        void tracesInputCommandEntitySqlAndBothJdbcRepresentations(String birthDate) throws Exception {
            // A fixed test-only reference admits the entire contractual year range, including 9999.
            clock.setInstant(Instant.parse("9999-12-31T23:59:59Z"));
            var before = database.snapshot();
            var payload = mapper.createObjectNode().put("name", "Ana").put("fullName", "Ana Silva")
                    .put("email", "ana@example.test").put("phone", "+12345678")
                    .put("birthDate", birthDate).put("password", "Chosen-password-009");
            var response = send(jsonRequest("/customers").POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes(payload))).build());
            var after = database.snapshot();
            remember(before, after);
            assertThat(response.statusCode()).isEqualTo(201);
            long accountId = jsonBody(response).path("id").asLong(-1);
            assertThat(accountId).isPositive();
            var commandCalls = mockingDetails(AopTestUtils.getUltimateTargetObject(worker)).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("register")).toList();
            var inputCalls = mockingDetails(AopTestUtils.getUltimateTargetObject(service)).getInvocations().stream()
                    .filter(call -> call.getMethod().getName().equals("register")).toList();
            assertThat(commandCalls.size()).isEqualTo(1);
            assertThat(inputCalls.size()).isEqualTo(1);
            var raw = (CustomerRegistrationRequestDTO) inputCalls.getFirst().getArguments()[0];
            var command = (CustomerRegistrationCommand) commandCalls.getFirst().getArguments()[0];
            var constructed = new UserAccount(command.name(), command.passwordHash(), command.fullName(), command.email(),
                    command.emailKey(), command.phone(), command.birthDate());
            var evidence = new LinkedHashMap<String, Object>();
            evidence.put("context", expectedContextPath().isEmpty() ? "ROOT" : "/api");
            evidence.put("expected", birthDate);
            evidence.put("requestDto", raw.birthDate());
            evidence.put("parsed", RegistrationInputRules.parseBirthDate(raw.birthDate()).toString());
            evidence.put("workerCommand", command.birthDate().toString());
            evidence.put("entityConstructor", constructed.getBirthDate().toString());
            evidence.put("zone", ZoneId.systemDefault().getId());
            evidence.put("configuredHibernateJdbcTimeZone",
                    context.getEnvironment().getProperty("spring.jpa.properties.hibernate.jdbc.time_zone", "<absent>"));

            var manager = context.getBean(EntityManagerFactory.class).createEntityManager();
            try {
                var loaded = manager.find(UserAccount.class, accountId, Map.of(
                        "jakarta.persistence.cache.retrieveMode", CacheRetrieveMode.BYPASS,
                        "jakarta.persistence.cache.storeMode", CacheStoreMode.BYPASS));
                evidence.put("freshJpaRead", loaded.getBirthDate().toString());
            } finally { manager.close(); }
            boolean otherFields;
            boolean emailKey;
            boolean singleAccount;
            boolean onlyClient;
            String sqlDate;
            LocalDate jdbcLocalDate;
            try (var connection = openIndependentConnection(); var statement = connection.prepareStatement(
                    "select name, full_name, email, phone, birth_date, email_key, cast(birth_date as varchar), "
                            + "extract(year from birth_date), extract(month from birth_date), extract(day from birth_date) "
                            + "from tb_user_account where id = ?")) {
                statement.setLong(1, accountId);
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    otherFields = "Ana".equals(rows.getString(1)) && "Ana Silva".equals(rows.getString(2))
                            && "ana@example.test".equals(rows.getString(3)) && "+12345678".equals(rows.getString(4));
                    jdbcLocalDate = rows.getObject(5, LocalDate.class);
                    sqlDate = rows.getString(7);
                    evidence.put("sqlType", rows.getMetaData().getColumnTypeName(5));
                    evidence.put("jdbcString", rows.getString(5));
                    evidence.put("jdbcLocalDate", jdbcLocalDate.toString());
                    evidence.put("jdbcLegacyToLocalDate", rows.getDate(5).toLocalDate().toString());
                    evidence.put("jdbcLegacyEpochMillis", rows.getDate(5).getTime());
                    evidence.put("sqlCast", sqlDate);
                    evidence.put("sqlYear", rows.getInt(8));
                    evidence.put("sqlMonth", rows.getInt(9));
                    evidence.put("sqlDay", rows.getInt(10));
                    emailKey = Arrays.equals("ana@example.test".getBytes(StandardCharsets.UTF_8), rows.getBytes(6));
                    singleAccount = !rows.next();
                }
                try (var roles = connection.prepareStatement("select role from tb_user_account_role where user_account_id = ?")) {
                    roles.setLong(1, accountId);
                    try (var rows = roles.executeQuery()) {
                        onlyClient = rows.next() && "CLIENT".equals(rows.getString(1)) && !rows.next();
                    }
                }
            }
            evidence.put("otherFieldsExact", otherFields);
            evidence.put("emailKeyExact", emailKey);
            evidence.put("singleAccountRow", singleAccount);
            evidence.put("exactlyClient", onlyClient);
            save((expectedContextPath().isEmpty() ? "ROOT" : "api") + "-" + birthDate + ".json", evidence);
            // Confirm all previously interrupted checks before testing the exact civil date.
            assertThat(otherFields && emailKey && singleAccount && onlyClient)
                    .as("other fields, exact binary key, single row and exactly CLIENT").isTrue();
            assertThat(birthDate.equals(raw.birthDate()) && birthDate.equals(command.birthDate().toString())
                    && birthDate.equals(constructed.getBirthDate().toString())).as("input/conversion/command/entity date exact").isTrue();
            assertThat(birthDate.equals(sqlDate) && LocalDate.parse(birthDate).equals(jdbcLocalDate))
                    .as("exact SQL DATE and independent JDBC 4.2 civil date").isTrue();
            assertThat(birthDate.equals(evidence.get("freshJpaRead")))
                    .as("fresh JPA extraction preserves the exact civil date").isTrue();
        }

        static Stream<String> dates() {
            return Stream.of("0001-01-01", "1582-10-04", "1582-10-15", "2000-02-29",
                    "0001-12-31", "1500-02-28", "1600-02-29", "1900-02-28", "1970-01-01", "9999-12-31");
        }

        @Test
        void dateStorageRemainsNullableAndLegacyNullSurvivesJpaExtraction() throws Exception {
            var legacy = fixture.createAccount("Legacy.date.guard", LegacyCredential.SHORT, Set.of(UserRole.CLIENT));
            var before = database.snapshot();
            try (var connection = openIndependentConnection()) {
                try (var columns = connection.getMetaData().getColumns(null, "PUBLIC", "TB_USER_ACCOUNT", "BIRTH_DATE")) {
                    assertThat(columns.next()).isTrue();
                    assertThat(columns.getInt("DATA_TYPE")).isEqualTo(Types.DATE);
                    assertThat(columns.getInt("NULLABLE")).isEqualTo(DatabaseMetaData.columnNullable);
                    assertThat(columns.next()).isFalse();
                }
                try (var statement = connection.prepareStatement("select birth_date from tb_user_account where id = ?")) {
                    statement.setLong(1, legacy.id());
                    try (var rows = statement.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getObject(1, LocalDate.class)).isNull();
                        assertThat(rows.next()).isFalse();
                    }
                }
            }
            var manager = context.getBean(EntityManagerFactory.class).createEntityManager();
            try {
                assertThat(manager.find(UserAccount.class, legacy.id()).getBirthDate()).isNull();
            } finally { manager.close(); }
            assertUnchanged(before, database.snapshot());
            save((expectedContextPath().isEmpty() ? "ROOT" : "api") + "-nullable-legacy.json",
                    Map.of("sqlDate", true, "nullable", true, "legacyNullJdbcAndJpa", true, "snapshotUnchanged", true));
        }

        private void remember(Snapshot before, Snapshot after) {
            var previous = before.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row)).toList();
            after.tables().get("TB_USER_ACCOUNT").rows().stream().map(row -> id(row))
                    .filter(id -> !previous.contains(id)).forEach(createdAccounts::add);
        }

        @AfterEach
        void cleanOwnedDateDiagnosisAccounts() throws SQLException {
            assertNoCallerTransaction();
            try (var connection = openIndependentConnection()) {
                connection.setAutoCommit(false);
                try {
                    for (long id : createdAccounts) {
                        for (String sql : List.of("delete from tb_user_account_role where user_account_id = ?",
                                "delete from tb_user_account where id = ?")) {
                            try (var statement = connection.prepareStatement(sql)) {
                                statement.setLong(1, id);
                                statement.executeUpdate();
                            }
                        }
                    }
                    connection.commit();
                    createdAccounts.clear();
                } catch (SQLException | RuntimeException | Error failure) {
                    connection.rollback();
                    throw new AssertionError("Date diagnosis cleanup failed");
                }
            } catch (SQLException failure) { throw new SQLException("Date diagnosis cleanup could not complete"); }
        }
        private static long id(Row row) { return ((Number) row.values().get("ID").value()).longValue(); }
    }

    private static void save(String name, Object evidence) throws IOException {
        var destination = temporaryEvidenceDirectory;
        var configuredDirectory = System.getProperty("feature009.date-diagnosis-dir");
        if (configuredDirectory != null) {
            var root = Path.of("").toAbsolutePath().normalize().resolve("target/feature009-evidence");
            destination = Path.of(configuredDirectory).toAbsolutePath().normalize();
            if (!destination.startsWith(root)) { throw new AssertionError("Use the owned ignored date evidence directory"); }
        }
        Files.createDirectories(destination);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destination.resolve(name).toFile(), evidence);
    }
}
