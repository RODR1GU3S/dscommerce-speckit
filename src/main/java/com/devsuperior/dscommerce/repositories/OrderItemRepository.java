package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.OrderItem;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Reference precheck only; the database FK remains the final protection against concurrent changes. */
public interface OrderItemRepository extends Repository<OrderItem, Long> {

    @Transactional(readOnly = true)
    boolean existsByProductId(Long productId);
}
