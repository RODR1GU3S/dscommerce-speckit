package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Order;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Root-only acquisition; the caller retains its transaction for subsequent reads and decisions. */
public interface OrderPaymentLockRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    Optional<Order> findPaymentOrderForUpdate(long orderId);
}
