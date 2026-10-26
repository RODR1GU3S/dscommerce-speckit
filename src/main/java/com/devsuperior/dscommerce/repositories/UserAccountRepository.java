package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.entities.UserRole;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, UserAccountInsertRepository {

    Optional<UserAccount> findByName(String name);

    Optional<UserAccount> findByEmailKey(byte[] emailKey);

    @Query("select distinct account from UserAccount account left join fetch account.roles where account.id = :id")
    Optional<UserAccount> findByIdWithRoles(@Param("id") Long id);

    // Scalar rows bypass initialized entity collections. Do not flush a caller's pending changes for this guard.
    @Query("select account.id as id, role as role from UserAccount account left join account.roles role where account.id = :id")
    @QueryHints(@QueryHint(name = "org.hibernate.flushMode", value = "COMMIT"))
    List<EffectiveAccountRole> findEffectiveAccountRolesById(@Param("id") Long id);

    interface EffectiveAccountRole {
        Long getId();
        UserRole getRole();
    }
}
