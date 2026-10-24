package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Shared-key lookup after the caller locks the order; deliberately exposes no save/merge API. */
public interface PaymentRepository extends Repository<Payment, Long>, PaymentInsertRepository {
    @Transactional(readOnly = true, propagation = Propagation.MANDATORY)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId")
    Optional<Payment> findById(@Param("orderId") Long orderId);
}
