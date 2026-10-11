package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/** JPA writes enter through the complete order aggregate. */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
