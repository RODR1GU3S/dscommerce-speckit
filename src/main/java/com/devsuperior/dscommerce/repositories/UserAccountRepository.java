package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByName(String name);

    @Query("select distinct account from UserAccount account left join fetch account.roles where account.id = :id")
    Optional<UserAccount> findByIdWithRoles(@Param("id") Long id);
}
