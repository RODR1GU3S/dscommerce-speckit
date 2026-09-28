package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByName(String name);
}
