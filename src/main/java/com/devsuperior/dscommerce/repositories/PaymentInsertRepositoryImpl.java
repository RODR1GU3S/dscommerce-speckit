package com.devsuperior.dscommerce.repositories;

import com.devsuperior.dscommerce.entities.Payment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class PaymentInsertRepositoryImpl implements PaymentInsertRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void insert(Payment payment) {
        entityManager.persist(payment);
    }
}
