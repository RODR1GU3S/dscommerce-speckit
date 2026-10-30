package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.CustomerRegistrationConfirmationDTO;
import com.devsuperior.dscommerce.dto.CustomerRegistrationRequestDTO;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepository.IdentityKey;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepository.RegistrationIdentityMetadata;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.RegistrationPasswordHasher;
import com.devsuperior.dscommerce.services.exceptions.CustomerRegistrationException;
import jakarta.persistence.RollbackException;
import jakarta.validation.Validator;
import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.hibernate.MappingException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/** T075 authorship: public facade boundary, without importing the not-yet-written T077 classifier. */
class CustomerRegistrationPersistenceClassificationTest {
    private static final String NAME = "Classify.client";
    private static final String PASSWORD = "Classification-password-009";
    private static final String NAME_INDEX = "NAME_PHYSICAL_INDEX";
    private static final String EMAIL_INDEX = "EMAIL_PHYSICAL_INDEX";
    private static final String INSERT = "insert into tb_user_account (name,password_hash) values (?,?)";
    // Canonical representation fixture only: no password derivation or credential claim.
    private static final String PHC = "$argon2id$v=19$m=19456,t=2,p=1$" + "A".repeat(22) + "$" + "A".repeat(43);
    private Validator validator;
    private RegistrationPasswordHasher hasher;
    private UserAccountRepository accounts;
    private CustomerRegistrationTransaction worker;
    private CustomerRegistrationService service;

    @BeforeEach
    void prepareOnlyMocksAndAnExplicitReferenceWithoutSpringDatabaseOrKdf() {
        validator = mock(Validator.class);
        hasher = mock(RegistrationPasswordHasher.class);
        worker = mock(CustomerRegistrationTransaction.class);
        accounts = mock(UserAccountRepository.class);
        when(validator.validate(any(CustomerRegistrationRequestDTO.class))).thenReturn(Set.of());
        when(hasher.hash(anyString())).thenReturn(PHC);
        when(accounts.findByName(anyString())).thenReturn(Optional.empty());
        when(accounts.findByEmailKey(any(byte[].class))).thenReturn(Optional.empty());
        when(accounts.registrationIdentityMetadata()).thenReturn(metadata(NAME_INDEX, EMAIL_INDEX));
        service = new CustomerRegistrationService(validator, RegistrationAttemptReference.fixed(
                Instant.parse("2030-01-01T12:00:00Z")), hasher, accounts, worker);
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Recognized.class)
    void onlyClosedIdentityDiagnosesWithHealthyMetadataBecomeGenericConflicts(Recognized vector) {
        boolean name = vector == Recognized.NAME_DIRECT || vector == Recognized.NAME_JPA;
        RuntimeException failure = unique(name ? NAME_INDEX : EMAIL_INDEX, name ? "NAME" : "EMAIL_KEY");
        failure = switch (vector) {
            case NAME_DIRECT -> failure;
            case EMAIL_SPRING -> new DataIntegrityViolationException("Controlled wrapper", failure);
            case NAME_JPA -> new RollbackException("Controlled wrapper", failure);
            case EMAIL_TRANSACTION -> new TransactionSystemException("Controlled wrapper",
                    new RollbackException("Controlled wrapper", failure));
        };
        failWorker(failure);
        assertFailure(409);
        assertThat(calls(accounts, "registrationIdentityMetadata")).as("capture healthy exact metadata before the write").isEqualTo(1);
        assertOneWorkerAndNoRecovery();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(Unproven.class)
    void unprovenIntegrityOperationalAndCommitFailuresStaySafeProcessingFailures(Unproven vector) {
        failWorker(unproven(vector));
        assertFailure(500);
        assertOneWorkerAndNoRecovery();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(MetadataProblem.class)
    void unavailableOrAmbiguousMetadataNeverAuthorizesAConflict(MetadataProblem vector) {
        var good = metadata(NAME_INDEX, EMAIL_INDEX);
        if (vector == MetadataProblem.UNAVAILABLE) {
            when(accounts.registrationIdentityMetadata()).thenThrow(new IllegalStateException("Controlled metadata failure"));
        } else {
            var invalid = switch (vector) {
                case ABSENT -> null;
                case SAME_INDEX -> metadata(NAME_INDEX, NAME_INDEX);
                case SAME_COLUMN -> new RegistrationIdentityMetadata(good.databaseProduct(), good.databaseVersion(),
                        good.catalog(), good.schema(), good.table(), good.name(), good.name());
                case WRONG_TABLE -> new RegistrationIdentityMetadata("H2", "2.2.224", "FIXTURE", "PUBLIC", "OTHER_TABLE", good.name(), good.emailKey());
                case WRONG_VERSION -> new RegistrationIdentityMetadata("H2", "9.9.9", "FIXTURE", "PUBLIC", good.table(), good.name(), good.emailKey());
                case WRONG_TYPE -> new RegistrationIdentityMetadata("H2", "2.2.224", "FIXTURE", "PUBLIC", good.table(),
                        new IdentityKey("NAME", Types.VARBINARY, "BINARY VARYING", 255, false, "UK_USER_ACCOUNT_NAME", NAME_INDEX), good.emailKey());
                case WRONG_CONSTRAINT -> new RegistrationIdentityMetadata("H2", "2.2.224", "FIXTURE", "PUBLIC", good.table(),
                        new IdentityKey("NAME", Types.VARCHAR, "CHARACTER VARYING", 255, false, "OTHER_CONSTRAINT", NAME_INDEX), good.emailKey());
                case UNAVAILABLE -> throw new AssertionError("Metadata vector is configured separately");
            };
            when(accounts.registrationIdentityMetadata()).thenReturn(invalid);
        }
        failWorker(unique(NAME_INDEX, "NAME"));
        assertFailure(500);
        assertThat(calls(accounts, "registrationIdentityMetadata")).as("the metadata control actually participates").isEqualTo(1);
        assertThat(calls(worker, "register")).as("no retry even when metadata blocks the write").isLessThanOrEqualTo(1);
        assertNoRecoveryOrRepositoryWrites();
    }

    @ParameterizedTest(name = "{index}: {0}")
    @EnumSource(PreparationFailure.class)
    void lookupAndEncoderFailuresStopWithoutAWriteOrInvalidInputClassification(PreparationFailure stage) {
        if (stage == PreparationFailure.ENCODER) {
            doAnswer(invocation -> { throw new IllegalStateException("Controlled encoder failure"); })
                    .when(hasher).hash(anyString());
        } else if (stage == PreparationFailure.NAME_LOOKUP) {
            doAnswer(invocation -> { throw new IllegalStateException("Controlled lookup failure"); })
                    .when(accounts).findByName(anyString());
        } else {
            doAnswer(invocation -> { throw new IllegalStateException("Controlled lookup failure"); })
                    .when(accounts).findByEmailKey(any(byte[].class));
        }
        assertFailure(500);
        assertThat(calls(worker, "register")).isZero();
        assertThat(calls(hasher, "hash")).isEqualTo(1);
        assertThat(calls(accounts, "findByName")).isEqualTo(stage == PreparationFailure.ENCODER ? 0 : 1);
        assertThat(calls(accounts, "findByEmailKey")).isEqualTo(stage == PreparationFailure.EMAIL_LOOKUP ? 1 : 0);
        assertNoRecoveryOrRepositoryWrites();
    }

    @Test
    void successfulFacadeControlDelegatesTheExactCommandOnceWithoutClaimingARealCommit() {
        when(worker.register(any(CustomerRegistrationCommand.class))).thenReturn(new CustomerRegistrationConfirmationDTO(123L, NAME));
        var result = service.register(request());
        assertThat(result.id() == 123L && NAME.equals(result.name())).isTrue();
        var command = mockingDetails(worker).getInvocations().stream().filter(call -> call.getMethod().getName().equals("register"))
                .map(call -> (CustomerRegistrationCommand) call.getArgument(0)).findFirst().orElseThrow();
        assertThat(NAME.equals(command.name()) && PHC.equals(command.passwordHash())
                && Arrays.equals("classify@example.test".getBytes(java.nio.charset.StandardCharsets.UTF_8), command.emailKey()))
                .as("literal identity, exact prepared hash and independent key passed once").isTrue();
        assertOneWorkerAndNoRecovery();
    }

    private void failWorker(RuntimeException failure) {
        doAnswer(invocation -> { throw failure; }).when(worker).register(any(CustomerRegistrationCommand.class));
    }

    private void assertFailure(int status) {
        var failure = catchThrowable(() -> service.register(request()));
        assertThat(failure instanceof CustomerRegistrationException).as("closed registration failure").isTrue();
        var safe = (CustomerRegistrationException) failure;
        assertThat(safe.getStatus()).isEqualTo(status);
        assertThat((status == 409 ? "Customer registration not available" : "Customer registration could not be completed")
                .equals(safe.getMessage()) && safe.getCause() == null && safe.getSuppressed().length == 0)
                .as("no diagnostic, persistence cause or secret in the public failure").isTrue();
    }

    private void assertOneWorkerAndNoRecovery() {
        assertThat(calls(worker, "register")).isEqualTo(1);
        assertThat(calls(hasher, "hash")).isEqualTo(1);
        assertNoRecoveryOrRepositoryWrites();
    }

    private void assertNoRecoveryOrRepositoryWrites() {
        var workerCall = mockingDetails(worker).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("register")).findFirst();
        if (workerCall.isPresent()) {
            assertThat(mockingDetails(accounts).getInvocations().stream()
                    .allMatch(call -> call.getSequenceNumber() < workerCall.get().getSequenceNumber()))
                    .as("all availability and metadata reads precede the worker, never recover after failure").isTrue();
        }
        assertThat(mockingDetails(accounts).getInvocations().stream().allMatch(call ->
                Set.of("findByName", "findByEmailKey", "registrationIdentityMetadata").contains(call.getMethod().getName())))
                .as("facade never saves, merges, deletes or compensates through the repository").isTrue();
        assertThat(calls(accounts, "findByName")).isLessThanOrEqualTo(1);
        assertThat(calls(accounts, "findByEmailKey")).isLessThanOrEqualTo(1);
        assertThat(calls(hasher, "hash")).isLessThanOrEqualTo(1);
        assertThat(mockingDetails(hasher).getInvocations().stream().filter(call -> call.getMethod().getName().equals("hash"))
                .allMatch(call -> PASSWORD.equals(call.getArgument(0)))).as("password is passed exactly").isTrue();
    }

    private static long calls(Object mock, String method) {
        return mockingDetails(mock).getInvocations().stream().filter(call -> call.getMethod().getName().equals(method)).count();
    }

    private static CustomerRegistrationRequestDTO request() {
        return new CustomerRegistrationRequestDTO(NAME, "Classification Person", "classify@example.test",
                "+12345678", "2000-02-29", PASSWORD);
    }

    private static RegistrationIdentityMetadata metadata(String nameIndex, String emailIndex) {
        return new RegistrationIdentityMetadata("H2", "2.2.224", "FIXTURE", "PUBLIC", "TB_USER_ACCOUNT",
                new IdentityKey("NAME", Types.VARCHAR, "CHARACTER VARYING", 255, false, "UK_USER_ACCOUNT_NAME", nameIndex),
                new IdentityKey("EMAIL_KEY", Types.VARBINARY, "BINARY VARYING", 1016, true, "UK_USER_ACCOUNT_EMAIL_KEY", emailIndex));
    }

    private static RuntimeException unique(String index, String column) { return constraint("23505", 23505, index, "TB_USER_ACCOUNT", column); }

    private static RuntimeException constraint(String state, int code, String index, String table, String column) {
        String value = "EMAIL_KEY".equals(column) ? "X'00'" : "'controlled'";
        String identity = index == null ? null : "PUBLIC." + index + " ON PUBLIC." + table + "(" + column
                + " NULLS FIRST) VALUES ( /* 1 */ " + value + " )";
        var sql = new JdbcSQLIntegrityConstraintViolationException("Unique index or primary key violation: \"" + identity + "\"",
                INSERT, state, code, null, null);
        return new ConstraintViolationException("Controlled persistence failure", sql, INSERT, identity);
    }

    private static RuntimeException unproven(Unproven vector) {
        return switch (vector) {
            case OTHER_PK -> constraint("23505", 23505, "PRIMARY_KEY_OTHER", "TB_USER_ACCOUNT", "ID");
            case ROLE_UNIQUE -> constraint("23505", 23505, "UK_USER_ACCOUNT_ROLE_INDEX", "TB_USER_ACCOUNT_ROLE", "USER_ACCOUNT_ID, ROLE");
            case OTHER_UNIQUE -> constraint("23505", 23505, "UK_USER_ACCOUNT_NAME_SHADOW", "TB_USER_ACCOUNT", "FULL_NAME");
            case FOREIGN_KEY -> constraint("23506", 23506, NAME_INDEX, "TB_USER_ACCOUNT", "NAME");
            case NOT_NULL -> constraint("23502", 23502, NAME_INDEX, "TB_USER_ACCOUNT", "NAME");
            case LENGTH -> constraint("22001", 22001, NAME_INDEX, "TB_USER_ACCOUNT", "NAME");
            case WRONG_CODE -> constraint("23505", 23506, NAME_INDEX, "TB_USER_ACCOUNT", "NAME");
            case WRONG_STATE -> constraint("23506", 23505, NAME_INDEX, "TB_USER_ACCOUNT", "NAME");
            case MISSING_IDENTITY -> constraint("23505", 23505, null, "TB_USER_ACCOUNT", "NAME");
            case SUFFIX_ONLY -> unique("PHYSICAL_INDEX", "NAME");
            case EMBEDDED_TEXT -> unique("prefix_" + NAME_INDEX + "_suffix", "NAME");
            case WRONG_TABLE -> constraint("23505", 23505, NAME_INDEX, "OTHER_TABLE", "NAME");
            case WRONG_COLUMN -> constraint("23505", 23505, NAME_INDEX, "TB_USER_ACCOUNT", "FULL_NAME");
            case MESSAGE_ONLY -> new DataIntegrityViolationException("Controlled text 23505 " + NAME_INDEX);
            case MALFORMED_IDENTITY -> new ConstraintViolationException("Controlled failure",
                    new SQLException("Controlled text", "23505", 23505), INSERT, "unstructured " + NAME_INDEX);
            case CONNECTION -> new TransactionSystemException("Controlled connection failure", new SQLException("Controlled", "08006", 8006));
            case TIMEOUT -> new TransactionSystemException("Controlled timeout", new SQLTimeoutException("Controlled", "HYT00", 50200));
            case DEADLOCK -> new TransactionSystemException("Controlled deadlock", new SQLException("Controlled", "40001", 40001));
            case MAPPING -> new MappingException("Controlled mapping failure");
            case COMMIT_UNKNOWN -> new TransactionSystemException("Controlled unknown commit", new SQLException("Controlled", "08007", 8007));
            case UNKNOWN_WRAPPER -> new IllegalStateException("Unapproved transport wrapper", unique(NAME_INDEX, "NAME"));
            case AMBIGUOUS_CAUSES -> {
                var diagnostic = (ConstraintViolationException) unique(NAME_INDEX, "NAME");
                var first = diagnostic.getSQLException();
                first.setNextException(new SQLException("Controlled", "23506", 23506));
                yield diagnostic;
            }
        };
    }

    enum Recognized { NAME_DIRECT, EMAIL_SPRING, NAME_JPA, EMAIL_TRANSACTION }
    enum Unproven { OTHER_PK, ROLE_UNIQUE, OTHER_UNIQUE, FOREIGN_KEY, NOT_NULL, LENGTH, WRONG_CODE, WRONG_STATE,
        MISSING_IDENTITY, SUFFIX_ONLY, EMBEDDED_TEXT, WRONG_TABLE, WRONG_COLUMN, MESSAGE_ONLY, MALFORMED_IDENTITY,
        CONNECTION, TIMEOUT, DEADLOCK, MAPPING, COMMIT_UNKNOWN, UNKNOWN_WRAPPER, AMBIGUOUS_CAUSES }
    enum MetadataProblem { ABSENT, UNAVAILABLE, SAME_INDEX, SAME_COLUMN, WRONG_TABLE, WRONG_VERSION, WRONG_TYPE, WRONG_CONSTRAINT }
    enum PreparationFailure { ENCODER, NAME_LOOKUP, EMAIL_LOOKUP }
}
