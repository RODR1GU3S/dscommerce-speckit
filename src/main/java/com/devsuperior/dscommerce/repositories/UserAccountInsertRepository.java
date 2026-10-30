package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.UserAccount;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Insert-only registration path; the caller owns profiles, flush and transaction completion. */
public interface UserAccountInsertRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    void insert(UserAccount account);

    /** Capture while the transaction is healthy, before attempting a write; never recover after failure. */
    @Transactional(readOnly = true)
    RegistrationIdentityMetadata registrationIdentityMetadata();

    record RegistrationIdentityMetadata(String databaseProduct, String databaseVersion, String catalog,
                                        String schema, String table, IdentityKey name, IdentityKey emailKey) { }

    /** Actual database identifiers, not inferred index suffixes or account values. */
    record IdentityKey(String column, int jdbcType, String typeName, int capacity, boolean nullable,
                       String constraintName, String indexName) { }
}
