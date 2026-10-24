package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Order;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.Optional;

public class OrderPaymentLockRepositoryImpl implements OrderPaymentLockRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<Order> findPaymentOrderForUpdate(long orderId) {
        var order = entityManager.find(Order.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) {
            return Optional.empty();
        }
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(order);
    }
}
