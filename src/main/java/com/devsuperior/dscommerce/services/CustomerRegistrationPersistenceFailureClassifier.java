package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.repositories.UserAccountInsertRepository.IdentityKey;
import com.devsuperior.dscommerce.repositories.UserAccountInsertRepository.RegistrationIdentityMetadata;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.RollbackException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;

import java.sql.Types;
import java.util.regex.Pattern;

/** Pure H2 2.2.224 decoder; callers must capture metadata before writing and classify outside the worker proxy. */
public final class CustomerRegistrationPersistenceFailureClassifier {

    private static final String IDENTIFIER = "[A-Z_][A-Z0-9_]*";
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile(IDENTIFIER);
    private static final Pattern H2_INDEX_DESCRIPTION = Pattern.compile(
            "(" + IDENTIFIER + ")\\.(" + IDENTIFIER + ") ON (" + IDENTIFIER + ")\\.(" + IDENTIFIER
                    + ")\\((" + IDENTIFIER + ") NULLS FIRST\\)(?: VALUES \\([\\s\\S]*\\))?");
    private static final String H2_INTEGRITY_EXCEPTION = "org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException";
    private static final int MAX_TRANSPORT_DEPTH = 16;

    private CustomerRegistrationPersistenceFailureClassifier() { }

    /** Invalid/unavailable/ambiguous metadata must remain a processing failure, never authorize a conflict. */
    public static boolean hasValidIdentityMetadata(RegistrationIdentityMetadata metadata) {
        return metadata != null && "H2".equals(metadata.databaseProduct())
                && isSupportedVersion(metadata.databaseVersion())
                && metadata.catalog() != null && !metadata.catalog().isBlank()
                && simpleIdentifier(metadata.schema()) && "TB_USER_ACCOUNT".equals(metadata.table())
                && validKey(metadata.name(), "NAME", Types.VARCHAR, "CHARACTER VARYING", 255,
                        false, "UK_USER_ACCOUNT_NAME")
                && validKey(metadata.emailKey(), "EMAIL_KEY", Types.VARBINARY, "BINARY VARYING", 1016,
                        true, "UK_USER_ACCOUNT_EMAIL_KEY")
                && !metadata.name().indexName().equals(metadata.emailKey().indexName());
    }

    /** No database access, logging, retry, cause retention or inference from a generic persistence failure. */
    public static boolean isRecognizedConflict(Throwable failure, RegistrationIdentityMetadata metadata) {
        if (!hasValidIdentityMetadata(metadata)) { return false; }
        // A finite closed path also rejects cause cycles without traversing arbitrary exception graphs.
        for (int depth = 0; failure != null && depth < MAX_TRANSPORT_DEPTH; depth++) {
            if (failure.getSuppressed().length != 0) { return false; }
            if (failure.getClass() == ConstraintViolationException.class) {
                return matchesIdentity((ConstraintViolationException) failure, metadata);
            }
            if (!knownTransport(failure)) { return false; }
            failure = failure.getCause();
        }
        return false;
    }

    private static boolean knownTransport(Throwable failure) {
        Class<?> type = failure.getClass();
        if (type == TransactionSystemException.class) {
            // An application exception is another diagnostic branch, not the single persistence cause.
            return ((TransactionSystemException) failure).getApplicationException() == null;
        }
        return type == DataIntegrityViolationException.class || type == RollbackException.class
                || type == PersistenceException.class;
    }

    private static boolean matchesIdentity(ConstraintViolationException failure,
                                           RegistrationIdentityMetadata metadata) {
        var sql = failure.getSQLException();
        if (sql == null || failure.getCause() != sql || !H2_INTEGRITY_EXCEPTION.equals(sql.getClass().getName())
                || !"23505".equals(sql.getSQLState()) || sql.getErrorCode() != 23505
                || sql.getNextException() != null || sql.getCause() != null || sql.getSuppressed().length != 0) {
            return false;
        }
        String identity = failure.getConstraintName();
        if (identity == null) { return false; }

        // Hibernate 6.5.3's H2 extractor returns the whole qualified index name, before " ON ".
        if (identity.equals(metadata.schema() + "." + metadata.name().indexName())
                || identity.equals(metadata.schema() + "." + metadata.emailKey().indexName())) {
            return true; // The captured constraint/index binding already proves the owning table and column.
        }

        // Also decode the complete H2 descriptor carried by the existing explicit transport fixtures.
        // Whole-string grammar and exact identifiers are required; the VALUES payload is never a discriminator.
        var descriptor = H2_INDEX_DESCRIPTION.matcher(identity);
        if (!descriptor.matches() || !metadata.schema().equals(descriptor.group(1))
                || !metadata.schema().equals(descriptor.group(3)) || !metadata.table().equals(descriptor.group(4))) {
            return false;
        }
        return matchesKey(metadata.name(), descriptor.group(2), descriptor.group(5))
                || matchesKey(metadata.emailKey(), descriptor.group(2), descriptor.group(5));
    }

    private static boolean matchesKey(IdentityKey key, String index, String column) {
        return key.indexName().equals(index) && key.column().equals(column);
    }

    private static boolean validKey(IdentityKey key, String column, int jdbcType, String typeName,
                                    int capacity, boolean nullable, String constraint) {
        return key != null && column.equals(key.column()) && key.jdbcType() == jdbcType
                && typeName.equals(key.typeName()) && key.capacity() == capacity && key.nullable() == nullable
                && constraint.equals(key.constraintName()) && simpleIdentifier(key.indexName());
    }

    private static boolean isSupportedVersion(String version) {
        return version != null && (version.equals("2.2.224") || version.startsWith("2.2.224 "));
    }

    private static boolean simpleIdentifier(String value) {
        return value != null && SIMPLE_IDENTIFIER.matcher(value).matches();
    }
}
