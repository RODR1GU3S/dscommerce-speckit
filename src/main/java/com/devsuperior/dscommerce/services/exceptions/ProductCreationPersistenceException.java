package com.devsuperior.dscommerce.services.exceptions;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.spi.TemplatedViolatedConstraintNameExtractor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Context of a failed creation flush, delivered to HTTP advice only after the transaction has unwound. */
public final class ProductCreationPersistenceException extends RuntimeException {
    private static final String H2_MISSING_REFERENCE = "Referential integrity constraint violation: \"";
    private static final Pattern PAIR_INSERT = Pattern.compile(
            "insert\\s+into\\s+tb_product_category\\s*\\(\\s*product_id\\s*,\\s*category_id\\s*\\)"
                    + "\\s*values\\s*\\(\\s*\\?\\s*,\\s*\\?\\s*\\)", Pattern.CASE_INSENSITIVE);
    private final Set<String> categoryForeignKeys;

    public ProductCreationPersistenceException(Set<String> categoryForeignKeys, DataIntegrityViolationException cause) {
        super("Product maintenance could not be completed", cause);
        this.categoryForeignKeys = Set.copyOf(categoryForeignKeys);
    }

    public boolean isMissingCategoryReference() {
        var visited = new HashSet<Throwable>();
        for (Throwable cause = getCause(); cause != null && visited.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                var name = h2MissingReferenceConstraint(violation);
                return "23506".equals(violation.getSQLState())
                        && name != null && categoryForeignKeys.contains(name)
                        && violation.getSQL() != null && PAIR_INSERT.matcher(violation.getSQL().strip()).matches();
            }
        }
        return false;
    }

    private static String h2MissingReferenceConstraint(ConstraintViolationException violation) {
        var sqlException = violation.getSQLException();
        // Hibernate 6.5.3's H2 23506 extractor drops the first character. Recover the complete vendor identity;
        // never guess a prefix, match a suffix, search arbitrary text or inspect the appended raw SQL/payload.
        if (sqlException.getErrorCode() != 23506 || !"23506".equals(sqlException.getSQLState())) return null;
        var message = sqlException.getMessage();
        if (message == null || !message.startsWith(H2_MISSING_REFERENCE)) return null;
        return TemplatedViolatedConstraintNameExtractor.extractUsingTemplate(H2_MISSING_REFERENCE, ": ", message);
    }
}
